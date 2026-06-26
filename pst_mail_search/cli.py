import argparse
import json
import logging
from pathlib import Path

from . import __version__, db
from .indexer import index_pst
from .pst_reader import PstReaderUnavailable
from .search import search


def main(argv: list[str] | None = None) -> None:
    parser = build_parser()
    args = parser.parse_args(argv)
    setup_logging(args.log_file)
    args.func(args)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="pst-search", description="Local PST mail search MVP")
    parser.add_argument("--version", action="version", version=__version__)
    parser.add_argument("--db", default="pst_search.db", help="SQLite index DB path")
    parser.add_argument("--log-file", default="pst_search_errors.log", help="Error log path")

    sub = parser.add_subparsers(required=True)

    init = sub.add_parser("init", help="Create the local index database")
    init.set_defaults(func=cmd_init)

    add = sub.add_parser("add", help="Register a PST file without indexing it")
    add.add_argument("pst_path")
    add.set_defaults(func=cmd_add)

    index = sub.add_parser("index", help="Index a PST file")
    index.add_argument("pst_path")
    index.add_argument("--rebuild", action="store_true", help="Delete existing rows for this PST before indexing")
    index.add_argument("--batch-size", type=int, default=100)
    index.set_defaults(func=cmd_index)

    rebuild = sub.add_parser("rebuild", help="Recreate the index rows for a PST file")
    rebuild.add_argument("pst_path")
    rebuild.add_argument("--batch-size", type=int, default=100)
    rebuild.set_defaults(func=cmd_rebuild)

    find = sub.add_parser("search", help="Search indexed mail and verify matches in source text")
    find.add_argument("keyword")
    find.add_argument("--limit", type=int, default=50)
    find.add_argument("--context-chars", type=int, default=80)
    find.add_argument("--json", action="store_true", help="Print JSON output")
    find.set_defaults(func=cmd_search)

    show = sub.add_parser("show", help="Show one indexed mail by local email ID")
    show.add_argument("email_id", type=int)
    show.set_defaults(func=cmd_show)

    info = sub.add_parser("info", help="List registered PST files")
    info.set_defaults(func=cmd_info)
    return parser


def setup_logging(log_file: str) -> None:
    logging.basicConfig(
        filename=log_file,
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )


def cmd_init(args: argparse.Namespace) -> None:
    con = db.connect(args.db)
    try:
        db.init_db(con)
        print(f"created index database: {Path(args.db).resolve()}")
    finally:
        con.close()


def cmd_add(args: argparse.Namespace) -> None:
    con = db.connect(args.db)
    try:
        db.init_db(con)
        pst_id = db.register_pst(con, args.pst_path)
        con.commit()
        print(f"registered PST #{pst_id}: {Path(args.pst_path).resolve()}")
    finally:
        con.close()


def cmd_index(args: argparse.Namespace) -> None:
    try:
        index_pst(args.db, args.pst_path, rebuild=args.rebuild, batch_size=args.batch_size)
    except PstReaderUnavailable as exc:
        raise SystemExit(str(exc)) from exc


def cmd_rebuild(args: argparse.Namespace) -> None:
    try:
        index_pst(args.db, args.pst_path, rebuild=True, batch_size=args.batch_size)
    except PstReaderUnavailable as exc:
        raise SystemExit(str(exc)) from exc


def cmd_search(args: argparse.Namespace) -> None:
    con = db.connect(args.db)
    try:
        db.init_db(con)
        candidate_count, results = search(
            con,
            args.keyword,
            limit=args.limit,
            context_chars=args.context_chars,
        )
        if args.json:
            print(json.dumps(_json_results(args.keyword, candidate_count, results), ensure_ascii=False, indent=2))
        else:
            _print_results(args.keyword, candidate_count, results)
    finally:
        con.close()


def cmd_show(args: argparse.Namespace) -> None:
    con = db.connect(args.db)
    try:
        row = db.get_email(con, args.email_id)
        if row is None:
            raise SystemExit(f"email not found: {args.email_id}")
        print(f"[메일 ID: {row['id']}]")
        print(f"날짜: {row['received_at'] or row['sent_at']}")
        print(f"PST: {row['pst_path']}")
        print(f"폴더: {row['folder_path']}")
        print(f"보낸 사람: {row['sender']}")
        print(f"받는 사람: {row['recipients']}")
        print(f"참조: {row['cc']}")
        print(f"제목: {row['subject']}")
        print()
        print(row["body_text"] or row["body_html_text"])
    finally:
        con.close()


def cmd_info(args: argparse.Namespace) -> None:
    con = db.connect(args.db)
    try:
        db.init_db(con)
        for row in db.list_psts(con):
            print(f"#{row['id']} {row['status']} messages={row['message_count']} {row['path']}")
    finally:
        con.close()


def _print_results(keyword: str, candidate_count: int, results: list[object]) -> None:
    print(f"검색어: {keyword}")
    print(f"인덱스 후보 결과: {candidate_count}건")
    print(f"원문 검증 통과: {len(results)}건")
    for result in results:
        print()
        print(f"[메일 ID: {result.email_id}]")
        print(f"날짜: {result.date}")
        print(f"폴더: {result.folder_path}")
        print(f"보낸 사람: {result.sender}")
        print(f"받는 사람: {result.recipients}")
        print(f"제목: {result.subject}")
        print()
        print("검색어 발견 필드:")
        for field in result.found_fields:
            print(f"- {field}")
        print()
        print("본문 위치:")
        for match in result.matches:
            print(f"- 필드: {match.field}")
            print(f"  문단 번호: {match.paragraph_number}")
            print(f"  줄 번호: {match.line_number}")
            print(f"  문자 위치: {match.offset}")
            print(f"  문맥: {match.context}")


def _json_results(keyword: str, candidate_count: int, results: list[object]) -> dict[str, object]:
    return {
        "keyword": keyword,
        "candidate_count": candidate_count,
        "verified_count": len(results),
        "results": [
            {
                "email_id": result.email_id,
                "date": result.date,
                "pst_path": result.pst_path,
                "folder_path": result.folder_path,
                "sender": result.sender,
                "recipients": result.recipients,
                "cc": result.cc,
                "subject": result.subject,
                "found_fields": result.found_fields,
                "matches": [
                    {
                        "field": match.field,
                        "offset": match.offset,
                        "line_number": match.line_number,
                        "paragraph_number": match.paragraph_number,
                        "context": match.context,
                    }
                    for match in result.matches
                ],
            }
            for result in results
        ],
    }

