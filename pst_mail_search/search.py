import re
import sqlite3
from typing import Iterable

from . import db
from .models import MatchLocation, SearchResult

SEARCH_FIELDS = ("subject", "sender", "recipients", "cc", "body_text", "body_html_text")


def search(
    con: sqlite3.Connection,
    keyword: str,
    limit: int = 50,
    context_chars: int = 80,
    max_matches_per_email: int = 20,
) -> tuple[int, list[SearchResult]]:
    keyword = keyword.strip()
    if not keyword:
        return 0, []

    candidate_ids = _candidate_email_ids(con, keyword, max(limit * 5, 100))
    verified: list[SearchResult] = []
    for email_id in candidate_ids:
        row = db.get_email(con, email_id)
        if row is None:
            continue
        matches = _verify_row(row, keyword, context_chars, max_matches_per_email)
        if not matches:
            continue
        found_fields = sorted({match.field for match in matches}, key=SEARCH_FIELDS.index)
        verified.append(
            SearchResult(
                email_id=int(row["id"]),
                date=row["received_at"] or row["sent_at"],
                pst_path=row["pst_path"],
                folder_path=row["folder_path"],
                sender=row["sender"],
                recipients=row["recipients"],
                cc=row["cc"],
                subject=row["subject"],
                found_fields=found_fields,
                matches=matches,
            )
        )
        if len(verified) >= limit:
            break
    return len(candidate_ids), verified


def _candidate_email_ids(con: sqlite3.Connection, keyword: str, limit: int) -> list[int]:
    ids: list[int] = []
    seen: set[int] = set()

    for source_sql, query in _candidate_queries(con, keyword):
        try:
            rows = con.execute(source_sql, (query, limit)).fetchall()
        except sqlite3.OperationalError:
            continue
        for row in rows:
            email_id = int(row["email_id"])
            if email_id not in seen:
                seen.add(email_id)
                ids.append(email_id)
        if len(ids) >= limit:
            return ids[:limit]

    if len(ids) < limit:
        for email_id in _like_candidates(con, keyword, limit):
            if email_id not in seen:
                seen.add(email_id)
                ids.append(email_id)
            if len(ids) >= limit:
                break
    return ids


def _candidate_queries(con: sqlite3.Connection, keyword: str) -> Iterable[tuple[str, str]]:
    phrase = _fts_phrase(keyword)
    yield (
        """
        SELECT email_id
        FROM email_fts
        WHERE email_fts MATCH ?
        ORDER BY bm25(email_fts)
        LIMIT ?
        """,
        phrase,
    )
    if len(keyword) >= 3 and db.trigram_available(con):
        yield (
            """
            SELECT email_id
            FROM email_trigram
            WHERE email_trigram MATCH ?
            ORDER BY bm25(email_trigram)
            LIMIT ?
            """,
            phrase,
        )


def _like_candidates(con: sqlite3.Connection, keyword: str, limit: int) -> list[int]:
    pattern = f"%{keyword}%"
    rows = con.execute(
        """
        SELECT id AS email_id
        FROM emails
        WHERE subject LIKE ?
           OR sender LIKE ?
           OR recipients LIKE ?
           OR cc LIKE ?
           OR body_text LIKE ?
           OR body_html_text LIKE ?
        ORDER BY received_at DESC, id DESC
        LIMIT ?
        """,
        (pattern, pattern, pattern, pattern, pattern, pattern, limit),
    ).fetchall()
    return [int(row["email_id"]) for row in rows]


def _verify_row(
    row: sqlite3.Row,
    keyword: str,
    context_chars: int,
    max_matches: int,
) -> list[MatchLocation]:
    matches: list[MatchLocation] = []
    for field in SEARCH_FIELDS:
        text = row[field] or ""
        for offset in _find_offsets(text, keyword):
            matches.append(
                MatchLocation(
                    field=field,
                    offset=offset,
                    line_number=text.count("\n", 0, offset) + 1,
                    paragraph_number=_paragraph_number(text, offset),
                    context=_context(text, offset, len(keyword), context_chars),
                )
            )
            if len(matches) >= max_matches:
                return matches
    return matches


def _find_offsets(text: str, keyword: str) -> Iterable[int]:
    lowered = text.casefold()
    needle = keyword.casefold()
    start = 0
    while True:
        pos = lowered.find(needle, start)
        if pos == -1:
            break
        yield pos
        start = pos + max(len(needle), 1)


def _paragraph_number(text: str, offset: int) -> int:
    if not text:
        return 1
    paragraph = 1
    for match in re.finditer(r"\n\s*\n+", text):
        if match.start() >= offset:
            break
        paragraph += 1
    return paragraph


def _context(text: str, offset: int, keyword_length: int, context_chars: int) -> str:
    start = max(0, offset - context_chars)
    end = min(len(text), offset + keyword_length + context_chars)
    prefix = "..." if start > 0 else ""
    suffix = "..." if end < len(text) else ""
    return f"{prefix}{text[start:offset]}[{text[offset:offset + keyword_length]}]{text[offset + keyword_length:end]}{suffix}"


def _fts_phrase(keyword: str) -> str:
    return '"' + keyword.replace('"', '""') + '"'

