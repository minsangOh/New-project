# PST Archive Search

Windows 10/11 local CLI project for directly reading Outlook `.pst` files, storing extracted mail metadata/body text in local SQLite stores, and searching without Outlook Search, Windows Search, Microsoft Graph, Outlook COM, external servers, or cloud sync.

Before starting future Codex work, read [`PROJECT_STATE.md`](PROJECT_STATE.md) for the current phase, CLI list, known issues, and next-step boundaries.

## Current Status

The repository is currently complete through **Phase 3C-3**.

Completed phases:

- Phase 1: Archive Catalog
- Phase 2: PST Scan PoC with `java-libpst`
- Phase 2B: Encoding Probe
- Phase 3A: SQLite Store
- Phase 3A-Verify: Store Inspection
- Phase 3B: SQLite `LIKE` Search MVP with source-field verification
- Phase 3B-Fix: Text Quality Diagnostics and BROKEN match display policy
- Phase 3C-0: Candidate Search Abstraction
- Phase 3C-1: SQLite FTS5 candidate index build
- Phase 3C-2: `search-store --engine fts5`
- Phase 3C-3: `benchmark-search` for LIKE vs FTS5 comparison

For detailed phase history, CLI behavior, known issues, and future boundaries, see [`PROJECT_STATE.md`](PROJECT_STATE.md).

## Core Search Rule

Search candidate results are not trusted as final results.

Current `search-store` flow:

1. `--engine like` or `--engine fts5` finds candidate message IDs.
2. Stored source fields are loaded from SQLite.
3. The query is rechecked against the actual stored field values.
4. Only verified matches are displayed.

The default engine is still `like`. FTS5 is a faster candidate layer, not a final result source. `--engine fts5` requires `build-search-index <store.sqlite> --replace` first.

## Important Known Issues

- Some `body_html_text` values can be stored as literal `????` / repeated `U+003F` question marks.
- When code point samples show repeated `U+003F`, the stored string is already damaged. That is not only a PowerShell display issue.
- `folder_path`, `subject`, `sender_name`, `recipients`, and `cc` are generally stored as normal Korean in UTF-8 output files.
- `body_html_text` can be more damaged than metadata fields because it depends on HTML source/recovery and HTML-to-text conversion.
- `search-store` hides `textQuality: BROKEN` matches by default.
- Use `search-store --include-broken` when debugging damaged body text.
- FTS5 can miss some arbitrary punctuation-heavy terms depending on SQLite tokenization. Use `--engine like` as the conservative fallback when checking suspicious misses.

## Main Commands

Run commands through the Gradle Wrapper:

```powershell
.\gradlew.bat run --args="<command> <options>"
```

Archive catalog:

```powershell
.\gradlew.bat run --args="init"
.\gradlew.bat run --args="add-pst D:\MailArchive\work_2026_Q2.pst --status active"
.\gradlew.bat run --args="list"
.\gradlew.bat run --args="show <pst_id>"
.\gradlew.bat run --args="verify"
.\gradlew.bat run --args="move-pst <pst_id> E:\MailArchive\work_2026_Q2.pst"
.\gradlew.bat run --args="stats"
```

PST scan and encoding diagnostics:

```powershell
.\gradlew.bat run --args="scan-file D:\MailArchive\sample.pst --limit 10"
.\gradlew.bat run --args="scan-pst <pst_id> --limit 10"
.\gradlew.bat run --args="encoding-probe D:\MailArchive\sample.pst --limit 10 --output D:\MailArchive\encoding-probe-report.txt"
```

Index to SQLite:

```powershell
.\gradlew.bat run --args="index-file D:\MailArchive\oms39.pst --out D:\MailArchive\oms39-store.sqlite --limit 1000 --replace"
.\gradlew.bat run --args="index-pst <pst_id> --limit 1000 --replace"
```

Inspect stored data:

```powershell
.\gradlew.bat run --args="inspect-store D:\MailArchive\oms39-store.sqlite"
.\gradlew.bat run --args="sample-messages D:\MailArchive\oms39-store.sqlite --limit 10"
.\gradlew.bat run --args="show-message D:\MailArchive\oms39-store.sqlite --id 123"
.\gradlew.bat run --args="quality-report D:\MailArchive\oms39-store.sqlite"
.\gradlew.bat run --args="diagnose-text-quality D:\MailArchive\oms39-store.sqlite --limit 100 --output D:\MailArchive\text-quality-report.txt"
.\gradlew.bat run --args="dump-message-raw D:\MailArchive\oms39-store.sqlite --id 55 --output D:\MailArchive\message-55-raw.txt"
.\gradlew.bat run --args="build-search-index D:\MailArchive\oms39-store.sqlite --replace"
```

Search:

```powershell
.\gradlew.bat run --args="search-store D:\MailArchive\oms39-store.sqlite RWP90H --limit 20 --output D:\MailArchive\search-rwp90h.txt"
.\gradlew.bat run --args="search-store D:\MailArchive\oms39-store.sqlite RWP90H --limit 20 --engine like"
.\gradlew.bat run --args="search-store D:\MailArchive\oms39-store.sqlite RWP90H --limit 20 --engine fts5"
.\gradlew.bat run --args="search-store D:\MailArchive\oms39-store.sqlite RWP90H --limit 20 --include-broken"
```

Benchmark:

```powershell
.\gradlew.bat run --args="benchmark-search D:\MailArchive\oms39-store.sqlite RWP90H --engine both --limit 20 --repeat 3"
.\gradlew.bat run --args="benchmark-search D:\MailArchive\oms39-store.sqlite 얼음정수기 --engine both --limit 20 --repeat 3 --output D:\MailArchive\benchmark-ice.txt"
```

`benchmark-search` compares LIKE and FTS5 through the same `SearchStoreService` and source-field verification path. It prints summary counts and timings only, not mail body context. Benchmark results are evidence for deciding whether the default `search-store` engine should later change from `like` to `fts5`; they do not change the default automatically.

## Next Phase

Next planned work is **Phase 3C-4: default engine policy decision**.

Goal:

- Use benchmark results from real stores to decide whether `search-store` should keep `like` as default or switch to `fts5`.
- Compare Korean terms, part numbers, punctuation-heavy terms, and BROKEN-match behavior before changing any default.
- Keep source-field verification, text quality diagnostics, and BROKEN match hiding behavior.

## Out Of Scope For Now

Do not implement these unless the user explicitly changes scope:

- GUI or desktop app UI
- AI Q&A
- Attachment body search
- External server integration
- Cloud sync
- Microsoft Graph
- Outlook COM
- Windows Search or Outlook Search index integration
- Multi-user/server deployment

## Build And Test

Java 21 is required. Gradle does not need to be installed globally because the repository includes the Gradle Wrapper.

```powershell
.\gradlew.bat test
.\gradlew.bat clean build
```

Real PST files must not be committed to GitHub. Optional integration tests use `PST_TEST_FILE` only when it is set:

```powershell
$env:PST_TEST_FILE="D:\MailArchive\sample.pst"
.\gradlew.bat test
```
