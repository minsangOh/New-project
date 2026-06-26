from dataclasses import dataclass
from datetime import datetime


@dataclass(slots=True)
class EmailRecord:
    pst_path: str
    folder_path: str
    internal_id: str
    message_id: str
    subject: str
    sender: str
    recipients: str
    cc: str
    sent_at: str
    received_at: str
    body_text: str
    body_html_text: str


@dataclass(slots=True)
class MatchLocation:
    field: str
    offset: int
    line_number: int
    paragraph_number: int
    context: str


@dataclass(slots=True)
class SearchResult:
    email_id: int
    date: str
    pst_path: str
    folder_path: str
    sender: str
    recipients: str
    cc: str
    subject: str
    found_fields: list[str]
    matches: list[MatchLocation]


def datetime_to_text(value: object) -> str:
    if value is None:
        return ""
    if isinstance(value, datetime):
        return value.isoformat(sep=" ", timespec="seconds")
    return str(value)

