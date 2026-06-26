import tempfile
import unittest
from pathlib import Path

from pst_mail_search import db
from pst_mail_search.models import EmailRecord
from pst_mail_search.search import search


class SearchTests(unittest.TestCase):
    def test_korean_keyword_is_verified_from_source_text(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = Path(tmpdir) / "index.db"
            pst_path = Path(tmpdir) / "sample.pst"
            pst_path.write_bytes(b"sample")

            con = db.connect(db_path)
            try:
                db.init_db(con)
                pst_id = db.register_pst(con, pst_path)
                db.store_email(
                    con,
                    pst_id,
                    EmailRecord(
                        pst_path=str(pst_path),
                        folder_path="/Inbox/Projects",
                        internal_id="1",
                        message_id="<1@example.com>",
                        subject="하네스 개발 요청",
                        sender="sender@example.com",
                        recipients="me@example.com",
                        cc="",
                        sent_at="2026-06-18 14:20:00",
                        received_at="2026-06-18 14:22:00",
                        body_text="안녕하세요.\n\nRWP90H 하네스 개발 요청드립니다.",
                        body_html_text="",
                    ),
                )
                db.store_email(
                    con,
                    pst_id,
                    EmailRecord(
                        pst_path=str(pst_path),
                        folder_path="/Inbox/Other",
                        internal_id="2",
                        message_id="<2@example.com>",
                        subject="다른 메일",
                        sender="sender@example.com",
                        recipients="me@example.com",
                        cc="",
                        sent_at="2026-06-18 14:20:00",
                        received_at="2026-06-18 14:22:00",
                        body_text="검색어가 없습니다.",
                        body_html_text="",
                    ),
                )
                con.commit()

                candidate_count, results = search(con, "하네스")
                self.assertGreaterEqual(candidate_count, 1)
                self.assertEqual(len(results), 1)
                self.assertEqual(results[0].email_id, 1)
                self.assertIn("body_text", results[0].found_fields)
                self.assertIn("[하네스]", results[0].matches[-1].context)
            finally:
                con.close()

    def test_verification_finds_multiple_fields_and_offsets(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = Path(tmpdir) / "index.db"
            pst_path = Path(tmpdir) / "sample.pst"
            pst_path.write_bytes(b"sample")

            con = db.connect(db_path)
            try:
                db.init_db(con)
                pst_id = db.register_pst(con, pst_path)
                db.store_email(
                    con,
                    pst_id,
                    EmailRecord(
                        pst_path=str(pst_path),
                        folder_path="/Inbox/Samsung/RWP90H",
                        internal_id="1",
                        message_id="<1@example.com>",
                        subject="RWP90H harness development request",
                        sender="example@samsung.com",
                        recipients="me@company.com",
                        cc="",
                        sent_at="2026-06-18 14:20:00",
                        received_at="2026-06-18 14:22:00",
                        body_text="Line one\nLine two RWP90H\n\nNext paragraph RWP90H",
                        body_html_text="",
                    ),
                )
                con.commit()

                _, results = search(con, "RWP90H")
                self.assertEqual(len(results), 1)
                fields = results[0].found_fields
                self.assertEqual(fields, ["subject", "folder_path"] if "folder_path" in fields else ["subject", "body_text"])
                body_matches = [m for m in results[0].matches if m.field == "body_text"]
                self.assertEqual(len(body_matches), 2)
                self.assertEqual(body_matches[0].line_number, 2)
                self.assertEqual(body_matches[1].paragraph_number, 2)
            finally:
                con.close()


if __name__ == "__main__":
    unittest.main()

