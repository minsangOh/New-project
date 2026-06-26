import logging
from pathlib import Path

from . import db
from .pst_reader import iter_pst_messages

LOGGER = logging.getLogger(__name__)


def index_pst(
    db_path: str | Path,
    pst_path: str | Path,
    rebuild: bool = False,
    batch_size: int = 100,
) -> int:
    con = db.connect(db_path)
    try:
        db.init_db(con)
        pst_file_id = db.register_pst(con, pst_path)
        if rebuild:
            db.clear_pst_index(con, pst_file_id)
            con.commit()

        count = 0
        for email in iter_pst_messages(pst_path):
            try:
                db.store_email(con, pst_file_id, email)
                count += 1
            except Exception as exc:
                db.log_indexing_error(con, pst_file_id, email.folder_path, email.subject, exc)
                LOGGER.exception("Failed to index message %s", email.subject)
            if count and count % batch_size == 0:
                con.commit()
                print(f"indexed {count} messages...")

        db.finish_index(con, pst_file_id, count)
        con.commit()
        print(f"indexed {count} messages total")
        return count
    except Exception:
        con.rollback()
        raise
    finally:
        con.close()

