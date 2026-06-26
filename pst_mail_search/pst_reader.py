import itertools
import logging
from pathlib import Path
from typing import Iterator

from .html_text import html_to_text
from .models import EmailRecord, datetime_to_text

LOGGER = logging.getLogger(__name__)


class PstReaderUnavailable(RuntimeError):
    pass


def iter_pst_messages(pst_path: str | Path) -> Iterator[EmailRecord]:
    try:
        import pypff  # type: ignore
    except ImportError as exc:
        raise PstReaderUnavailable(
            "pypff is not installed. Install libpff/pypff, then run indexing again."
        ) from exc

    path = str(Path(pst_path).resolve())
    pst_file = pypff.file()
    pst_file.open(path)
    try:
        root = pst_file.get_root_folder()
        yield from _walk_folder(root, path, "")
    finally:
        pst_file.close()


def _walk_folder(folder: object, pst_path: str, parent_path: str) -> Iterator[EmailRecord]:
    folder_name = _safe_text(_value(folder, "name")) or "Root"
    folder_path = _join_folder(parent_path, folder_name)

    message_count = int(_value(folder, "number_of_sub_messages") or 0)
    for index in range(message_count):
        try:
            message = folder.get_sub_message(index)
            yield _message_to_record(message, pst_path, folder_path, index)
        except Exception:
            LOGGER.exception("Failed to parse message at %s item %s", folder_path, index)
            continue

    subfolder_count = int(_value(folder, "number_of_sub_folders") or 0)
    for index in range(subfolder_count):
        try:
            yield from _walk_folder(folder.get_sub_folder(index), pst_path, folder_path)
        except Exception:
            LOGGER.exception("Failed to parse subfolder at %s index %s", folder_path, index)
            continue


def _message_to_record(message: object, pst_path: str, folder_path: str, index: int) -> EmailRecord:
    subject = _safe_text(_first_value(message, "subject", "conversation_topic"))
    sender = _sender_text(message)
    recipients, cc = _recipients_text(message)
    html_body = _safe_text(_first_value(message, "html_body", "get_html_body"))
    plain_body = _safe_text(_first_value(message, "plain_text_body", "get_plain_text_body"))
    html_body_text = html_to_text(html_body)
    message_id = _message_id(message)
    internal_id = _internal_id(message, folder_path, index, message_id, subject)

    return EmailRecord(
        pst_path=pst_path,
        folder_path=folder_path,
        internal_id=internal_id,
        message_id=message_id,
        subject=subject,
        sender=sender,
        recipients=recipients,
        cc=cc,
        sent_at=datetime_to_text(_first_value(message, "client_submit_time", "delivery_time")),
        received_at=datetime_to_text(_first_value(message, "delivery_time", "client_submit_time")),
        body_text=plain_body or html_body_text,
        body_html_text=html_body_text,
    )


def _sender_text(message: object) -> str:
    parts = [
        _safe_text(_first_value(message, "sender_name")),
        _safe_text(_first_value(message, "sender_email_address", "sender_smtp_address")),
    ]
    return " <".join(part for part in parts if part) + (">" if len([p for p in parts if p]) > 1 else "")


def _recipients_text(message: object) -> tuple[str, str]:
    to_values: list[str] = []
    cc_values: list[str] = []
    count = int(_value(message, "number_of_recipients") or 0)
    for index in range(count):
        try:
            recipient = message.get_recipient(index)
        except Exception:
            continue
        text = _safe_text(_first_value(recipient, "name", "email_address", "smtp_address"))
        rtype = str(_first_value(recipient, "type", "recipient_type") or "").lower()
        if "cc" in rtype or rtype == "2":
            cc_values.append(text)
        else:
            to_values.append(text)
    return "; ".join(filter(None, to_values)), "; ".join(filter(None, cc_values))


def _message_id(message: object) -> str:
    direct = _safe_text(_first_value(message, "internet_message_identifier", "message_identifier", "identifier"))
    if direct:
        return direct
    headers = _safe_text(_first_value(message, "transport_headers"))
    for line in headers.splitlines():
        if line.lower().startswith("message-id:"):
            return line.split(":", 1)[1].strip()
    return ""


def _internal_id(message: object, folder_path: str, index: int, message_id: str, subject: str) -> str:
    for name in ("identifier", "entry_id", "record_key", "search_key"):
        value = _safe_text(_first_value(message, name))
        if value:
            return value
    return f"{folder_path}/{index}/{message_id or subject}"


def _join_folder(parent_path: str, folder_name: str) -> str:
    name = folder_name.strip("/") or "Root"
    if not parent_path:
        return f"/{name}"
    return f"{parent_path}/{name}"


def _first_value(obj: object, *names: str) -> object:
    for name in names:
        value = _value(obj, name)
        if value not in (None, ""):
            return value
    return ""


def _value(obj: object, name: str) -> object:
    try:
        attr = getattr(obj, name)
    except AttributeError:
        return None
    try:
        return attr() if callable(attr) else attr
    except Exception:
        return None


def _safe_text(value: object) -> str:
    if value is None:
        return ""
    if isinstance(value, bytes):
        for encoding in ("utf-8", "utf-16-le", "cp949", "latin-1"):
            try:
                return value.decode(encoding).replace("\x00", "").strip()
            except UnicodeDecodeError:
                continue
        return value.decode("utf-8", errors="replace").replace("\x00", "").strip()
    return str(value).replace("\x00", "").strip()

