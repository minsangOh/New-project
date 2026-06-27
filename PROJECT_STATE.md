# Project State

Repository: https://github.com/minsangOh/New-project
Branch: master
Last known state: Phase 1 through Phase 3C-4 complete.

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
- False-positive fix for normal short Korean names/departments covered by tests.
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

Phase boundary:

- SQLite FTS5 candidate engine was added later in Phase 3C-2.
- Lucene candidate engine remains unimplemented.

### Phase 3C-1: SQLite FTS5 Candidate Index Build

Implemented:

- `build-search-index <store.sqlite> --replace` CLI.
- SQLite FTS5 virtual table `messages_fts` generated from `messages`.
- `messages_fts.rowid` and `message_id` preserve the source `messages.id` link.
- Indexed fields match the current `search-store` fields: `subject`, `sender_name`, `sender_email`, `recipients`, `cc`, `folder_path`, `body_text`, `body_html_text`.
- Existing `messages` data is not modified.
- BROKEN quality text is indexed as stored; final display filtering remains the job of `search-store` and source-field verification.

Not implemented yet:

- Lucene candidate engine.

### Phase 3C-2: FTS5 Candidate Search Engine

Implemented:

- `search-store --engine like`.
- `search-store --engine fts5`.
- Default search engine remains `like` for conservative behavior.
- `Fts5CandidateSearcher` uses `messages_fts` only to find candidate message IDs.
- FTS5 candidates are joined back to `messages` and loaded as normal `SearchCandidate` values.
- Final output still requires `RawFieldVerifier` source-field verification.
- Existing match location, text quality display, BROKEN match hiding, `--include-broken`, and `hiddenBrokenMatches` behavior are preserved.
- `--field` restrictions map to the matching FTS5 columns.
- Missing FTS5 index fails clearly with `FTS5 index not found. Run build-search-index first.`

Not implemented yet:

- Automatic fallback from FTS5 to LIKE.
- Lucene candidate engine.

### Phase 3C-3: LIKE vs FTS5 Benchmark Command

Implemented:

- `benchmark-search <store.sqlite> <query>` CLI.
- Engine options: `--engine like`, `--engine fts5`, `--engine both`.
- Benchmark options: `--limit`, `--repeat`, `--field`, `--include-broken`, `--output`.
- Default benchmark engine is `both`.
- Default `search-store` engine remains `like`.
- Benchmark uses the existing `SearchStoreService` path, so both LIKE and FTS5 still require `RawFieldVerifier` source-field verification.
- Benchmark output is summary-only and does not print mail body context.
- Missing FTS5 index is reported clearly as a failed engine result.

Not implemented yet:

- Automatic default-engine change to FTS5.
- Lucene candidate engine.

### Phase 3C-4: Default Engine Policy Decision

Benchmark evidence from the real store:

| Query | LIKE verified | FTS5 verified | Result |
| --- | ---: | ---: | --- |
| RWP90H | 7 | 7 | same |
| 테이핑 | 2 | 2 | same |
| 단선 | 4 | 4 | same |
| DA96-01767C | 11 | 9 | FTS5 missed candidates |
| 얼음정수기 | 14 | 11 | FTS5 missed candidates |
| 삼성전자 | 18 | 16 | FTS5 missed candidates |
| 1015#18 | 0 | 0 | no matching data in sampled store |
| ST760145-2 | 0 | 0 | no matching data in sampled store |
| 조인트부 | 0 | 0 | no matching data in sampled store |

Policy:

- Keep `search-store` default engine as `like`.
- Treat `like` as the conservative, accuracy-first engine.
- Treat `fts5` as a fast optional candidate engine only.
- If FTS5 returns fewer verified/displayed messages, do not switch the default engine to FTS5.
- For suspected misses, part numbers, model names, and punctuation-heavy terms such as hyphen/hash strings, re-run with `--engine like`.

Implemented:

- `benchmark-search` now emits `policyDecision: KEEP_LIKE_DEFAULT`.
- If LIKE and FTS5 verified/displayed counts differ, the decision hint says not to switch the default engine to FTS5.
- `search-store --help` now describes `like` as the accuracy-first default and `fts5` as a fast optional candidate engine.

Not implemented yet:

- Automatic fallback or hybrid candidate search.
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
- `build-search-index`

Search:

- `search-store`
- `benchmark-search`

## Known Issues and Observations

- Some `body_html_text` values can be stored as literal `????` / repeated `U+003F` question marks.
- When code point samples show repeated `U+003F`, the text is already damaged in the stored SQLite value. This is not a PowerShell display-only issue.
- `folder_path`, `subject`, `sender_name`, `recipients`, and `cc` are generally stored as normal Korean in UTF-8 output files.
- `body_html_text` can be worse than metadata fields because it depends on HTML source/recovery and HTML-to-text conversion.
- `dump-message-raw` should be used to compare `body_html` and `body_html_text` before deciding where the damage happened.
- `search-store` hides BROKEN quality matches by default to avoid noisy broken body context.
- Use `search-store --include-broken` only when debugging damaged body text.
- Current candidate search uses the candidate-search abstraction with `like` and `fts5` engines. `like` remains the default.
- `benchmark-search` compares LIKE and FTS5 speed/counts but does not change the default engine automatically.
- Real-store benchmarks showed FTS5 candidate misses for some important Korean/part-number queries, so `like` remains the default.
- FTS5 can miss some punctuation-heavy arbitrary strings depending on SQLite tokenization. Use `--engine like` as the conservative fallback when validating suspicious misses.

## Next Planned Phase

### Phase 3C-5: Hybrid Candidate Search or Fallback Design

Goal:

- Evaluate an explicit hybrid/fallback candidate strategy.
- Keep `like` as default until a hybrid/fallback design proves it does not reduce verified results.
- Continue treating FTS5 as a fast optional candidate layer.

Required invariant:

- Keep source-field re-verification.
- FTS5/Lucene results must remain candidate results only.
- Final displayed results must still be verified against stored fields.
- Existing text quality display and BROKEN match hiding policy should remain.

Suggested next Phase 3C work:

1. Design an opt-in hybrid mode, such as FTS5 first plus LIKE fallback for risky queries.
2. Define risky-query detection for part numbers, model names, hyphens, hashes, and important Korean terms.
3. Test that hybrid mode never reduces verified results compared with LIKE.
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
.\gradlew.bat run --args="build-search-index D:\MailArchive\oms39-store.sqlite --replace"
.\gradlew.bat run --args="search-store D:\MailArchive\oms39-store.sqlite RWP90H --limit 20 --engine fts5"
.\gradlew.bat run --args="benchmark-search D:\MailArchive\oms39-store.sqlite RWP90H --engine both --limit 20 --repeat 3"
.\gradlew.bat run --args="diagnose-text-quality D:\MailArchive\oms39-store.sqlite --limit 100 --output D:\MailArchive\text-quality-report.txt"
.\gradlew.bat run --args="dump-message-raw D:\MailArchive\oms39-store.sqlite --id 55 --output D:\MailArchive\message-55-raw.txt"
```
