# PST Archive Search

Windows 10/11 local CLI project for directly reading Outlook `.pst` files, storing extracted mail metadata/body text in local SQLite stores, and searching without Outlook Search, Windows Search, Microsoft Graph, Outlook COM, external servers, or cloud sync.

Before starting future Codex work, read [`PROJECT_STATE.md`](PROJECT_STATE.md) for the current phase, CLI list, known issues, and next-step boundaries.

## Current Status

The repository is currently complete through **Phase 3C-6**.

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
- Phase 3C-4: Default engine policy decision
- Phase 3C-5: FTS5 missing candidate diagnostics
- Phase 3C-6: compare diagnostics visible result refinement

For detailed phase history, CLI behavior, known issues, and future boundaries, see [`PROJECT_STATE.md`](PROJECT_STATE.md).

## Core Search Rule

Search candidate results are not trusted as final results.

Current `search-store` flow:

1. `--engine like` or `--engine fts5` finds candidate message IDs.
2. Stored source fields are loaded from SQLite.
3. The query is rechecked against the actual stored field values.
4. Only verified matches are displayed.

The default engine is still `like`. FTS5 is a faster candidate layer, not a final result source. `--engine fts5` requires `build-search-index <store.sqlite> --replace` first.

Engine policy:

- `like`: conservative, default, accuracy-first.
- `fts5`: fast optional candidate engine.
- Use `like` to recheck suspected misses, part numbers, model names, and punctuation-heavy queries.

## Important Known Issues

- Some `body_html_text` values can be stored as literal `????` / repeated `U+003F` question marks.
- When code point samples show repeated `U+003F`, the stored string is already damaged. That is not only a PowerShell display issue.
- `folder_path`, `subject`, `sender_name`, `recipients`, and `cc` are generally stored as normal Korean in UTF-8 output files.
- `body_html_text` can be more damaged than metadata fields because it depends on HTML source/recovery and HTML-to-text conversion.
- `search-store` hides `textQuality: BROKEN` matches by default.
- Use `search-store --include-broken` when debugging damaged body text.
- FTS5 can miss some arbitrary punctuation-heavy terms depending on SQLite tokenization. Use `--engine like` as the conservative fallback when checking suspicious misses.

## Benchmark Summary

Real-store Phase 3C-3 benchmark results showed that FTS5 is much faster, but it missed candidates for several important terms. The default engine therefore remains `like`.

| Query | LIKE verified | FTS5 verified | Policy note |
| --- | ---: | ---: | --- |
| RWP90H | 7 | 7 | compatible |
| Korean: taping | 2 | 2 | compatible |
| Korean: broken wire | 4 | 4 | compatible |
| DA96-01767C | 11 | 9 | keep LIKE default |
| Korean: ice water purifier | 14 | 11 | keep LIKE default |
| Korean: Samsung Electronics | 18 | 16 | keep LIKE default |
| 1015#18 | 0 | 0 | no data in sampled store |
| ST760145-2 | 0 | 0 | no data in sampled store |
| Korean: joint area | 0 | 0 | no data in sampled store |

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

Compare search engines:

```powershell
.\gradlew.bat run --args="compare-search-engines D:\MailArchive\oms39-store.sqlite DA96-01767C --limit 20 --output D:\MailArchive\compare-da96.txt"
```

`compare-search-engines` is a diagnostic tool for finding verified messages that LIKE finds but FTS5 misses. It reports message IDs, matched fields, match policies, hidden BROKEN match counts, `visibilityClass`, and short previews. It now separates visible/displayed differences from hidden-only BROKEN differences. It is meant to guide a future hybrid/fallback design; it does not implement fallback.

Phase 3C-6 comparison notes:

- `DA96-01767C`: visible LIKE-only subject misses were observed because LIKE matched `DA96-01767CD` as a substring while FTS5 missed it as a candidate.
- Korean query `얼음정수기`: LIKE-only differences were hidden-only BROKEN matches under the default display policy.
- Future hybrid design should prioritize visible LIKE-only misses, not hidden-only BROKEN differences.

Benchmark:

```powershell
.\gradlew.bat run --args="benchmark-search D:\MailArchive\oms39-store.sqlite RWP90H --engine both --limit 20 --repeat 3"
.\gradlew.bat run --args="benchmark-search D:\MailArchive\oms39-store.sqlite DA96-01767C --engine both --limit 20 --repeat 3 --output D:\MailArchive\benchmark-da96.txt"
```

`benchmark-search` compares LIKE and FTS5 through the same `SearchStoreService` and source-field verification path. It prints summary counts and timings only, not mail body context. Current benchmark evidence keeps `like` as the default; future work should evaluate explicit hybrid/fallback behavior instead of silently switching to FTS5.

## Next Phase

Next planned work is **Phase 3C-7: visible-miss-based hybrid candidate search design**.

Goal:

- Consider an explicit hybrid/fallback search mode after reviewing `compare-search-engines` visible/hidden-only diagnostics.
- Keep `like` as the default unless a future design can prove no loss of visible or verified results.
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
