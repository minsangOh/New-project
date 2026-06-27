# Project State

Repository: https://github.com/minsangOh/New-project
Branch: master
Last known state: Phase 1 through Phase 3C-0 complete.

Use this file as the first context document for future Codex work. It is intended to replace long recap prompts.

## Current Goal

Build a Windows 10/11 local PST archive search tool that directly reads Outlook `.pst` files, stores extracted mail metadata/body text locally, and searches without relying on Outlook Search, Windows Search, Microsoft Graph, Outlook COM, external servers, or cloud sync.

The core rule remains: search index/candidate results are never final. Final search output must pass source-field verification against stored message fields.

## Completed Phases

### Phase 1: Archive Catalog

Implemented:

- Local archive catalog SQLite database.
- PST registration.
- PST listing and detail view.
- PST status management: `active`, `archive`, `warm`, `cold`, plus verification-derived statuses such as missing/invalid.
- PST fingerprint and file verification.
- PST path move handling.
- Per-PST shard directory/manifest structure.
- Gradle Wrapper and GitHub Actions CI.

Main CLI examples:

- `archive init`
- `archive add-pst`
- `archive list`
- `archive show <pst_id>`
- `archive mark-active <pst_id>`
- `archive mark-archive <pst_id>`
- `archive mark-warm <pst_id>`
- `archive mark-cold <pst_id>`
- `archive verify`
- `archive verify --pst <pst_id>`
- `archive move-pst <pst_id> <new_path>`
- `archive stats`

### Phase 2: java-libpst Scan PoC

Implemented:

- `java-libpst` based PST open/scan proof of concept.
- Recursive folder traversal.
- Limited mail preview extraction.
- Safe field extraction so one bad field/message does not stop the scan.
- Direct file scan and catalog-based scan.

Main CLI:

- `scan-file <pst_path> --limit 10`
- `scan-pst <pst_id> --limit 10`

### Phase 2B: Encoding Probe PoC

Implemented:

- Encoding diagnostic command for PST fields.
- Raw property/charset recovery experiments.
- Korean text quality scoring helpers.
- HTML charset detection helpers.

Main CLI:

- `encoding-probe <pst_path> --limit 10`

### Phase 3A: SQLite Message Store

Implemented:

- PST indexing into a standalone SQLite store.
- Folder and message metadata storage.
- Stored fields include folder path, subject, sender, recipients, cc, dates, body text, body HTML, body HTML converted to text, field status, and lengths.
- Batch/transaction-oriented write path.
- Replace mode for rebuilding a store.

Main CLI:

- `index-file <pst_path> --out <store.sqlite> --limit <n> --replace`
- `index-pst <pst_id> --limit <n> --replace`

### Phase 3A-Verify: Store Inspection

Implemented read-only inspection commands:

- Store/table counts.
- Latest index run summary.
- Field status distributions.
- Sample messages.
- Message detail previews.
- Store quality report.

Main CLI:

- `inspect-store <store.sqlite>`
- `sample-messages <store.sqlite> --limit 10`
- `show-message <store.sqlite> --id <message_id>`
- `quality-report <store.sqlite>`

### Phase 3B: SQLite Store Search MVP

Implemented:

- `search-store` command.
- SQLite `LIKE` based candidate generation only.
- Candidate results are rechecked against stored source fields before output.
- Field match output includes field name, offset, length, line number, paragraph number, context, and match policy.
- Searched fields:
  - `subject`
  - `sender_name`
  - `sender_email`
  - `recipients`
  - `cc`
  - `folder_path`
  - `body_text`
  - `body_html_text`

Main CLI:

- `search-store <store.sqlite> <query> --limit 20`
- `search-store <store.sqlite> <query> --field subject`
- `search-store <store.sqlite> <query> --output <report.txt>`

Important: SQLite `LIKE` is not the final high-speed search engine. It is only the current candidate layer.

### Phase 3B-Fix: Text Quality Diagnostics and Search Output Policy

Implemented:

- Stored text NUL cleanup for searchable fields during indexing.
- Text quality diagnostics for NUL chars, repeated question marks, high question mark ratio, replacement/mojibake patterns, and status mismatches.
- `dump-message-raw` diagnostics with Unicode code point samples.
- `body_html` vs `body_html_text` comparison.
- Damage type classification:
  - `OK`
  - `SOURCE_HTML_BROKEN`
  - `HTML_TO_TEXT_EXTRACTION_PROBLEM`
  - `GETTER_OR_RECOVERY_PROBLEM`
  - `SOURCE_OR_DECODING_BROKEN`
- False-positive fix for normal short Korean names/departments such as `김덕용`, `오민상`, `박진수`, `하근주`, `윤영춘`, `개발팀`, `품질팀`, `제품운영팀`.
- `search-store` hides `textQuality: BROKEN` matches by default.
- `search-store --include-broken` shows BROKEN matches for debugging.
- Search output reports `hiddenBrokenMatches`.

Main CLI:

- `diagnose-text-quality <store.sqlite> --limit 100 --output <report.txt>`
- `dump-message-raw <store.sqlite> --id <message_id> --output <report.txt>`
- `search-store <store.sqlite> <query> --include-broken`


### Phase 3C-0: Candidate Search Abstraction

Implemented:

- Candidate search interface for swappable candidate engines.
- LIKE candidate search moved into `LikeCandidateSearcher`.
- `SearchStoreService` depends on the candidate search interface instead of a concrete LIKE SQL class.
- Existing raw-field verification, match location, BROKEN match hiding, and search result formatting behavior are preserved.

Not implemented yet:

- SQLite FTS5 candidate engine.
- Lucene candidate engine.
## Current CLI List

Global pattern:

```powershell
.\gradlew.bat run --args="<command> <options>"
```

Catalog/archive:

- `init`
- `add-pst`
- `list`
- `show`
- `mark-active`
- `mark-archive`
- `mark-warm`
- `mark-cold`
- `verify`
- `move-pst`
- `stats`

PST scan/diagnostics:

- `scan-file`
- `scan-pst`
- `encoding-probe`

Indexing:

- `index-file`
- `index-pst`

Store inspection:

- `inspect-store`
- `sample-messages`
- `show-message`
- `quality-report`
- `diagnose-text-quality`
- `dump-message-raw`

Search:

- `search-store`

## Known Issues and Observations

- Some `body_html_text` values can be stored as literal `????` / repeated `U+003F` question marks.
- When code point samples show repeated `U+003F`, the text is already damaged in the stored SQLite value. This is not a PowerShell display-only issue.
- `folder_path`, `subject`, `sender_name`, `recipients`, and `cc` are generally stored as normal Korean in UTF-8 output files.
- `body_html_text` can be worse than metadata fields because it depends on HTML source/recovery and HTML-to-text conversion.
- `dump-message-raw` should be used to compare `body_html` and `body_html_text` before deciding where the damage happened.
- `search-store` hides BROKEN quality matches by default to avoid noisy broken body context.
- Use `search-store --include-broken` only when debugging damaged body text.
- Current candidate search uses the candidate-search abstraction with the LIKE engine as the only implemented engine. SQLite `LIKE` is acceptable for MVP validation but not ideal for large stores.

## Next Planned Phase

### Phase 3C: Candidate Search Acceleration

Goal:

- Replace or augment SQLite `LIKE` candidate search with a faster candidate layer.
- Candidate options to evaluate/implement:
  - SQLite FTS5
  - Apache Lucene

Required invariant:

- Keep source-field re-verification.
- FTS5/Lucene results must remain candidate results only.
- Final displayed results must still be verified against stored fields.
- Existing text quality display and BROKEN match hiding policy should remain.

Suggested first Phase 3C work:

1. Design candidate search abstraction so `LIKE`, FTS5, and Lucene can share the same verification/output path.
2. Add FTS5 or Lucene behind the abstraction.
3. Benchmark against current `search-store` on the existing store.
4. Keep `--include-broken` and `hiddenBrokenMatches` behavior unchanged.

## Explicitly Out of Scope For Now

Do not implement these yet unless the user explicitly changes scope:

- GUI or desktop app UI.
- AI Q&A.
- Attachment body search.
- External server integration.
- Cloud sync.
- Microsoft Graph.
- Outlook COM.
- Windows Search or Outlook Search index integration.
- Multi-user/server deployment.

## Verification Baseline

Before pushing functional changes, normally run at least:

```powershell
.\gradlew.bat test
```

For CLI smoke tests, current CI includes help checks for the main commands. Full local build command:

```powershell
.\gradlew.bat clean build
```

For real-store smoke testing, use:

```powershell
.\gradlew.bat run --args="search-store D:\MailArchive\oms39-store.sqlite RWP90H --limit 20 --output D:\MailArchive\search-rwp90h.txt"
.\gradlew.bat run --args="diagnose-text-quality D:\MailArchive\oms39-store.sqlite --limit 100 --output D:\MailArchive\text-quality-report.txt"
.\gradlew.bat run --args="dump-message-raw D:\MailArchive\oms39-store.sqlite --id 55 --output D:\MailArchive\message-55-raw.txt"
```
