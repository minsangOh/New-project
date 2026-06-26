import hashlib
import logging
import sqlite3
from pathlib import Path
from typing import Iterable

from .models import EmailRecord

LOGGER = logging.getLogger(__name__)


SCHEMA = """
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS pst_files (
    id INTEGER PRIMARY KEY,
    path TEXT NOT NULL UNIQUE,
    size_bytes INTEGER NOT NULL DEFAULT 0,
    mtime_ns INTEGER NOT NULL DEFAULT 0,
    indexed_at TEXT,
    status TEXT NOT NULL DEFAULT 'registered',
    message_count INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS emails (
    id INTEGER PRIMARY KEY,
    pst_file_id INTEGER NOT NULL REFERENCES pst_files(id) ON DELETE CASCADE,
    internal_id TEXT NOT NULL,
    message_id TEXT,
    folder_path TEXT NOT NULL,
    subject TEXT NOT NULL DEFAULT '',
    sender TEXT NOT NULL DEFAULT '',
    recipients TEXT NOT NULL DEFAULT '',
    cc TEXT NOT NULL DEFAULT '',
    sent_at TEXT NOT NULL DEFAULT '',
    received_at TEXT NOT NULL DEFAULT '',
    body_text TEXT NOT NULL DEFAULT '',
    body_html_text TEXT NOT NULL DEFAULT '',
    source_hash TEXT NOT NULL,
    indexed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(pst_file_id, internal_id)
);

CREATE TABLE IF NOT EXISTS indexing_errors (
    id INTEGER PRIMARY KEY,
    pst_file_id INTEGER,
    folder_path TEXT NOT NULL DEFAULT '',
    item_hint TEXT NOT NULL DEFAULT '',
    error_type TEXT NOT NULL,
    error_message TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_emails_pst_file_id ON emails(pst_file_id);
CREATE INDEX IF NOT EXISTS idx_emails_received_at ON emails(received_at);
"""


def connect(db_path: str | Path) -> sqlite3.Connection:
    con = sqlite3.connect(str(db_path))
    con.row_factory = sqlite3.Row
    con.execute("PRAGMA foreign_keys = ON")
    return con


def init_db(con: sqlite3.Connection) -> None:
    con.executescript(SCHEMA)
    _create_fts_tables(con)
    con.commit()


def _create_fts_tables(con: sqlite3.Connection) -> None:
    con.execute(
        """
        CREATE VIRTUAL TABLE IF NOT EXISTS email_fts USING fts5(
            email_id UNINDEXED,
            subject,
            sender,
            recipients,
            cc,
            body_text,
            body_html_text,
            tokenize = 'unicode61 remove_diacritics 2'
        )
        """
    )
    try:
        con.execute(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS email_trigram USING fts5(
                email_id UNINDEXED,
                combined_text,
                tokenize = 'trigram case_sensitive 0'
            )
            """
        )
    except sqlite3.OperationalError as exc:
        LOGGER.warning("SQLite trigram tokenizer is unavailable: %s", exc)


def trigram_available(con: sqlite3.Connection) -> bool:
    row = con.execute(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name='email_trigram'"
    ).fetchone()
    return row is not None


def register_pst(con: sqlite3.Connection, pst_path: str | Path) -> int:
    path = Path(pst_path).resolve()
    stat = path.stat()
    con.execute(
        """
        INSERT INTO pst_files(path, size_bytes, mtime_ns, status)
        VALUES (?, ?, ?, 'registered')
        ON CONFLICT(path) DO UPDATE SET
            size_bytes = excluded.size_bytes,
            mtime_ns = excluded.mtime_ns
        """,
        (str(path), stat.st_size, stat.st_mtime_ns),
    )
    row = con.execute("SELECT id FROM pst_files WHERE path = ?", (str(path),)).fetchone()
    return int(row["id"])


def clear_pst_index(con: sqlite3.Connection, pst_file_id: int) -> None:
    ids = [row["id"] for row in con.execute("SELECT id FROM emails WHERE pst_file_id = ?", (pst_file_id,))]
    if ids:
        con.executemany("DELETE FROM email_fts WHERE email_id = ?", [(email_id,) for email_id in ids])
        if trigram_available(con):
            con.executemany("DELETE FROM email_trigram WHERE email_id = ?", [(email_id,) for email_id in ids])
    con.execute("DELETE FROM emails WHERE pst_file_id = ?", (pst_file_id,))
    con.execute("UPDATE pst_files SET message_count = 0, status = 'registered' WHERE id = ?", (pst_file_id,))


def store_email(con: sqlite3.Connection, pst_file_id: int, email: EmailRecord) -> int:
    source_hash = _email_hash(email)
    con.execute(
        """
        INSERT INTO emails(
            pst_file_id, internal_id, message_id, folder_path, subject, sender,
            recipients, cc, sent_at, received_at, body_text, body_html_text, source_hash
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(pst_file_id, internal_id) DO UPDATE SET
            message_id = excluded.message_id,
            folder_path = excluded.folder_path,
            subject = excluded.subject,
            sender = excluded.sender,
            recipients = excluded.recipients,
            cc = excluded.cc,
            sent_at = excluded.sent_at,
            received_at = excluded.received_at,
            body_text = excluded.body_text,
            body_html_text = excluded.body_html_text,
            source_hash = excluded.source_hash,
            indexed_at = CURRENT_TIMESTAMP
        """,
        (
            pst_file_id,
            email.internal_id,
            email.message_id,
            email.folder_path,
            email.subject,
            email.sender,
            email.recipients,
            email.cc,
            email.sent_at,
            email.received_at,
            email.body_text,
            email.body_html_text,
            source_hash,
        ),
    )
    row = con.execute(
        "SELECT id FROM emails WHERE pst_file_id = ? AND internal_id = ?",
        (pst_file_id, email.internal_id),
    ).fetchone()
    email_id = int(row["id"])
    _replace_search_rows(con, email_id, email)
    return email_id


def _replace_search_rows(con: sqlite3.Connection, email_id: int, email: EmailRecord) -> None:
    con.execute("DELETE FROM email_fts WHERE email_id = ?", (email_id,))
    con.execute(
        """
        INSERT INTO email_fts(email_id, subject, sender, recipients, cc, body_text, body_html_text)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        (
            email_id,
            email.subject,
            email.sender,
            email.recipients,
            email.cc,
            email.body_text,
            email.body_html_text,
        ),
    )
    if trigram_available(con):
        con.execute("DELETE FROM email_trigram WHERE email_id = ?", (email_id,))
        con.execute(
            "INSERT INTO email_trigram(email_id, combined_text) VALUES (?, ?)",
            (email_id, "\n".join([email.subject, email.sender, email.recipients, email.cc, email.body_text, email.body_html_text])),
        )


def finish_index(con: sqlite3.Connection, pst_file_id: int, message_count: int, status: str = "indexed") -> None:
    con.execute(
        """
        UPDATE pst_files
        SET indexed_at = CURRENT_TIMESTAMP, status = ?, message_count = ?
        WHERE id = ?
        """,
        (status, message_count, pst_file_id),
    )


def log_indexing_error(
    con: sqlite3.Connection,
    pst_file_id: int | None,
    folder_path: str,
    item_hint: str,
    exc: BaseException,
) -> None:
    con.execute(
        """
        INSERT INTO indexing_errors(pst_file_id, folder_path, item_hint, error_type, error_message)
        VALUES (?, ?, ?, ?, ?)
        """,
        (pst_file_id, folder_path, item_hint, type(exc).__name__, str(exc)),
    )


def list_psts(con: sqlite3.Connection) -> Iterable[sqlite3.Row]:
    return con.execute("SELECT * FROM pst_files ORDER BY path")


def get_email(con: sqlite3.Connection, email_id: int) -> sqlite3.Row | None:
    return con.execute(
        """
        SELECT emails.*, pst_files.path AS pst_path
        FROM emails
        JOIN pst_files ON pst_files.id = emails.pst_file_id
        WHERE emails.id = ?
        """,
        (email_id,),
    ).fetchone()


def _email_hash(email: EmailRecord) -> str:
    digest = hashlib.sha256()
    for value in (
        email.folder_path,
        email.message_id,
        email.subject,
        email.sender,
        email.recipients,
        email.cc,
        email.sent_at,
        email.received_at,
        email.body_text,
        email.body_html_text,
    ):
        digest.update(value.encode("utf-8", errors="replace"))
        digest.update(b"\0")
    return digest.hexdigest()

