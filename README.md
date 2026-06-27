# PST Archive Search

Windows 로컬 PC에서 여러 Outlook `.pst` 아카이브를 등록하고, 장기적으로 통합 검색기로 확장하기 위한 Java 21 CLI 프로젝트입니다.

현재 저장소는 **Phase 1: Archive Catalog + PST 등록/상태 관리 CLI**만 구현합니다. 검색 기능, Lucene 인덱스, 메일 본문 저장, java-libpst 실제 scan은 아직 구현하지 않았습니다.

Phase 2에서는 검색 기능이 아니라 **java-libpst 실제 PST scan PoC**만 추가했습니다. 목적은 실제 PST 파일을 열고 폴더 구조와 메일 기본 필드를 콘솔에 진단 출력할 수 있는지 확인하는 것입니다.

Phase 2B에서는 **한글 인코딩 복구 PoC**를 추가했습니다. 목적은 java-libpst getter가 반환한 folder name, subject, sender, recipients, plain/html body가 깨질 때 raw property byte 또는 charset 후보 비교로 복구 가능성을 진단하는 것입니다.

## Phase 1 범위

구현됨:

- archive catalog SQLite 생성
- PST 파일 등록
- PST 목록 조회
- PST 상세 조회
- PST 상태 변경: `active`, `warm`, `cold`, `archive`, `missing`, `invalid`
- PST fingerprint 계산
- PST 경로 변경 처리
- PST 존재 여부 및 fingerprint verify
- per-PST shard directory 생성
- `manifest.json` 생성
- java-libpst scan을 나중에 붙일 수 있는 `PstScanner` 인터페이스와 stub
- JUnit 5 테스트
- GitHub Actions CI
- Gradle Wrapper
- java-libpst scan PoC 명령
- 한글 인코딩 진단 명령

아직 구현하지 않음:

- Lucene 검색 인덱스
- 메일 본문 저장
- 원문 재검증 검색
- 첨부파일 검색
- GUI
- AI Q&A
- 전체 MVP 검색 기능

## 기술 스택

```text
Java 21
Gradle Wrapper
picocli
SQLite via sqlite-jdbc
java-libpst 0.9.3
jsoup
Jackson
SLF4J + Logback
JUnit 5
```

## 데이터 저장 위치

기본 위치:

```text
%LOCALAPPDATA%\PstArchiveSearch\
  catalog\
    archive_catalog.sqlite
  shards\
    <pst_id>\
      store.sqlite
      lucene\
      errors.sqlite
      manifest.json
  logs\
    app.log
```

CLI 옵션으로 data directory를 바꿀 수 있습니다.

```powershell
.\gradlew.bat run --args="--data-dir D:\PstArchiveSearch init"
```

## Windows 실행 방법

Java 21이 필요합니다.

```powershell
java -version
```

Gradle은 별도로 설치하지 않아도 됩니다. 이 저장소는 Gradle Wrapper를 포함합니다.

```powershell
.\gradlew.bat test
.\gradlew.bat run --args="init"
.\gradlew.bat run --args="add-pst D:\MailArchive\2026\work_2026_Q2.pst --status active --period-from 2026-04-01 --period-to 2026-06-30"
.\gradlew.bat run --args="list"
```

배포용 zip을 만들려면:

```powershell
.\gradlew.bat installDist
```

생성 후:

```powershell
.\build\install\archive\bin\archive.bat init
```

macOS/Linux 또는 Git Bash에서는:

```bash
./gradlew test
./gradlew run --args="init"
```

## CLI 사용 예시

초기화:

```powershell
.\gradlew.bat run --args="init"
```

PST 등록:

```powershell
.\gradlew.bat run --args="add-pst D:\MailArchive\2026\work_2026_Q2.pst --status active --period-from 2026-04-01 --period-to 2026-06-30"
```

목록 조회:

```powershell
.\gradlew.bat run --args="list"
```

상세 조회:

```powershell
.\gradlew.bat run --args="show <pst_id>"
```

상태 변경:

```powershell
.\gradlew.bat run --args="mark-active <pst_id>"
.\gradlew.bat run --args="mark-archive <pst_id>"
.\gradlew.bat run --args="mark-warm <pst_id>"
.\gradlew.bat run --args="mark-cold <pst_id>"
```

PST 존재 여부 및 fingerprint 확인:

```powershell
.\gradlew.bat run --args="verify"
.\gradlew.bat run --args="verify --pst <pst_id>"
```

PST 파일을 외장 SSD 등 다른 경로로 옮긴 뒤 catalog 경로 재연결:

```powershell
.\gradlew.bat run --args="move-pst <pst_id> E:\MailArchive\2026\work_2026_Q2.pst"
```

통계:

```powershell
.\gradlew.bat run --args="stats"
```

## Phase 2 PST Scan PoC

이 PoC는 PST 파일을 열고 폴더 구조와 메일 preview 최대 N개를 콘솔에 출력하는 진단 명령입니다. 검색 인덱스 생성, 본문 저장, 원문 재검증 검색은 하지 않습니다.

실제 PST 파일은 GitHub에 올리지 마세요. 업무 메일에는 개인정보, 고객 정보, 첨부파일명, 내부 프로젝트명이 포함될 수 있습니다.

Catalog 등록 없이 파일을 직접 scan:

```powershell
.\gradlew.bat run --args="scan-file C:\path\to\archive.pst --limit 10"
```

Catalog에 등록된 PST ID로 scan:

```powershell
.\gradlew.bat run --args="--data-dir D:\PstArchiveSearch scan-pst <pst_id> --limit 10"
```

출력 내용:

- PST scan 시작 정보
- PST 내부 폴더 경로
- 폴더명
- item count
- subfolder count
- 최대 `--limit`개 메일 preview
- descriptor node id
- subject
- sender name/email
- to/cc
- sent/received date
- plain body 앞부분 500자
- HTML body 앞부분 500자
- HTML을 jsoup으로 text 변환한 preview 500자
- scan summary

PoC 성공 기준:

- PST 파일을 열 수 있다.
- 루트부터 하위 폴더가 출력된다.
- 메일 최대 10개가 출력된다.
- 특정 getter 오류가 전체 scan을 멈추지 않는다.
- summary의 `fatalErrors`가 0이다.

PoC 실패 시 확인할 것:

- PST 파일 경로가 맞는지
- Outlook이 해당 PST를 쓰고 있지 않은지
- PST 파일이 손상되었거나 암호화되어 있지 않은지
- java-libpst 0.9.3이 해당 PST 형식을 처리하지 못하는지
- `fatalErrors`, `messageErrors`, `fieldErrors` 출력이 어떤 값을 가지는지

실제 PST를 이용한 integration test:

```powershell
$env:PST_TEST_FILE="D:\MailArchive\sample.pst"
.\gradlew.bat test
```

`PST_TEST_FILE`이 없으면 integration test는 자동 skip됩니다.

다음 Phase로 넘어가기 전 체크리스트:

- `scan-file`이 실제 업무 PST를 열 수 있음
- `scan-pst`가 catalog 등록 PST를 열 수 있음
- 폴더 구조가 기대한 Outlook 폴더와 대체로 일치함
- subject/sender/to/cc/date가 추출됨
- plain body 또는 HTML body preview가 추출됨
- 한글 제목/본문이 깨지지 않는 샘플이 있음
- 품번 문자열이 preview에 보존되는 샘플이 있음
- `fatalErrors`가 0임

## Fingerprint 설계

경로만으로 같은 PST인지 판단하지 않습니다. Phase 1 fingerprint는 다음 값을 기반으로 계산합니다.

```text
file_size
mtime
first_1mb_sha256
last_1mb_sha256
```

주의:

- 파일명이 바뀌어도 fingerprint가 같으면 같은 PST로 볼 수 있습니다.
- 외장 SSD로 이동한 경우 `move-pst`로 새 경로를 연결합니다.
- 파일이 없으면 `verify`에서 `missing`으로 표시합니다.
- 파일 크기 또는 fingerprint가 바뀌면 `invalid`로 표시해 사용자가 확인하도록 합니다.

## PST 분할 운영 권장안

단일 PST를 100GB 이상으로 계속 키우는 운영은 권장하지 않습니다.

권장 기준:

- PST 1개당 20~30GB 권장
- 35GB 경고
- 40GB 전후 상한
- 분기별 PST 권장

예시:

```text
D:\MailArchive\2026\work_2026_Q1.pst
D:\MailArchive\2026\work_2026_Q2.pst
D:\MailArchive\2026\work_2026_Q3.pst
D:\MailArchive\2026\work_2026_Q4.pst
```

운영 전략:

- 현재 분기 PST는 `active`
- 최근 archive는 `warm`
- 오래된 archive는 `cold`
- 닫힌 archive PST는 read-only처럼 관리
- active PST만 자주 증분 인덱싱
- archive PST는 변경 감지만 수행

## 테스트 방법

JUnit 5 테스트는 실제 PST 파일 없이 임시 파일로 catalog와 fingerprint 동작을 검증합니다.

```powershell
.\gradlew.bat test
```

GitHub Actions에서는 Windows runner에서 다음 명령을 실행합니다.

```powershell
.\gradlew.bat test
.\gradlew.bat run --args="--data-dir $env:RUNNER_TEMP\PstArchiveSearch init"
.\gradlew.bat run --args="scan-file --help"
.\gradlew.bat run --args="scan-pst --help"
.\gradlew.bat run --args="encoding-probe --help"
```

테스트 항목:

- catalog DB 생성
- PST 등록
- 같은 PST 중복 등록 방지
- 파일명 변경 후 fingerprint 확인
- 존재하지 않는 PST verify 시 `missing` 처리
- status 변경
- shard manifest 생성
- custom data directory 사용

## 다음 Phase 계획

다음 Phase에서는 이번 scan PoC 결과를 바탕으로 PST 파서 검증 범위를 넓힙니다.

- java-libpst `0.9.4`
- GitHub source 직접 빌드
- 50GB PST scan
- `-Xmx1g` 메모리 로그
- 한글/품번 추출 검증
- 필수 검색어 원문 추출 가능 여부 확인

필수 검증 검색어:

```text
RWP90H
DA96-01767C
ST760145-2
1015#18
삼성전자
얼음정수기
테이핑
조인트부
단선
```

Phase 2에서도 Lucene 결과를 최종 검색 결과로 바로 사용하지 않습니다. 검색 인덱스는 후보 생성용이고, 최종 결과는 반드시 원문 재검증을 통과해야 합니다.

## Phase 2B Korean Encoding Probe

실제 PST scan에서 PST open, folder traversal, mail preview는 성공했지만 한글 folder name, subject, sender, recipients, HTML text가 깨질 수 있습니다. 이 문제를 해결하지 않으면 Phase 3A에서 본문 저장이나 검색 인덱스를 만들어도 깨진 문자열이 그대로 저장됩니다.

`encoding-probe`는 다음을 진단합니다.

- java-libpst getter 문자열이 이미 깨졌는지
- java-libpst 내부 property raw byte에 reflection으로 접근 가능한지
- HTML meta charset이 있는지
- `ks_c_5601-1987` alias를 `MS949`, `EUC-KR` 후보로 fallback할 수 있는지
- UTF-8, MS949, EUC-KR 후보 중 어느 결과가 가장 좋은지
- raw byte 없이 이미 깨진 문자열만 있을 때 복구 불가능한지

사용법:

```powershell
.\gradlew.bat run --args="encoding-probe D:\MailArchive\sample.pst --limit 10"
```

PowerShell에서 한글이 `?좎룞` 또는 `臾몄꽌`처럼 깨져 보이면, 먼저 콘솔 표시 인코딩 문제와 PST 디코딩 문제를 분리해야 합니다.

권장 진단 명령:

```powershell
.\gradlew.bat run --args="encoding-probe D:\MailArchive\sample.pst --limit 10 --output D:\MailArchive\encoding-probe-report.txt"
```

`encoding-probe-report.txt`는 UTF-8 BOM 포함 텍스트 파일로 저장됩니다. 이 파일을 UTF-8로 열어 보고서에서도 한글이 깨지는지 확인하세요. 보고서 상단에는 `javaDefaultCharset`, `native.encoding`, `stdout.encoding`, `sun.stdout.encoding`, `consoleCharset` 진단값도 함께 출력됩니다.

콘솔에 바로 출력해야 한다면 실행 전에 다음을 시도할 수 있습니다.

```powershell
chcp 65001
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
```

출력 예시:

```text
[MAIL 1 ENCODING PROBE]
descriptorNodeId: ...
folderRaw: ...
folderDecoded: ...
folderDecodeStatus: OK / DEGRADED / UNRECOVERABLE
subjectRawGetter: ...
subjectDecodedBest: ...
subjectDecodeStatus: OK / DEGRADED / UNRECOVERABLE
htmlMetaCharset: ks_c_5601-1987
htmlDecodedBestCharset: MS949
htmlDecodedBestPreview: ...
brokenCharRatioBefore: ...
brokenCharRatioAfter: ...
hangulRatioAfter: ...
```

한글 깨짐 판정 기준:

- `?`, `�`, `占` 같은 replacement 문자 비율
- `좎`, `룞`, `뜝`, `숈`, `삕` 같은 자주 보이는 mojibake 문자 비율
- 완성형 한글 범위 `가-힣` 비율
- 깨진 문자 비율이 낮고 한글 비율이 높은 후보를 우선 선택

복구가 불가능한 경우:

- java-libpst가 이미 깨진 Java `String`만 반환하고 raw property byte에 접근할 수 없는 경우
- 원본 byte가 `?` replacement 문자로 손실된 경우
- HTML meta charset과 실제 body byte가 불일치하고 raw byte 후보도 모두 낮은 품질인 경우

다음 Phase로 넘어가기 전 조건:

- `encoding-probe`가 실제 PST에서 실행됨
- subject/folder/body 중 일부라도 `OK` 또는 개선된 `DEGRADED` 결과를 보임
- raw byte 접근 가능 여부가 필드별로 확인됨
- `UNRECOVERABLE` 필드가 많다면 java-libpst 0.9.4 또는 source build 비교 필요

## Phase 3A SQLite Shard Store POC

Phase 3A의 목적은 검색 기능을 구현하기 전에, java-libpst로 추출한 PST 폴더와 메일 원문을 per-PST SQLite shard DB에 안정적으로 저장할 수 있는지 검증하는 것입니다.

아직 구현하지 않은 것:

- Lucene
- SQLite FTS5
- 검색 명령
- 원문 재검증 검색
- 첨부파일 본문 검색
- GUI
- AI Q&A

직접 PST 파일을 SQLite로 저장:

```powershell
.\gradlew.bat run --args="index-file D:\MailArchive\oms39.pst --out D:\MailArchive\oms39-store.sqlite --limit 1000 --replace"
```

catalog에 등록된 PST를 shard store에 저장:

```powershell
.\gradlew.bat run --args="--data-dir D:\PstArchiveSearch index-pst <pst_id> --limit 1000 --replace"
```

옵션:

- `--limit`: 저장할 메일 수 상한입니다. 대형 PST 검증 시 처음에는 `10`, `100`, `1000` 순서로 늘리는 것을 권장합니다.
- `--replace`: 기존 `folders`, `messages`, `index_errors` 데이터를 지우고 다시 저장합니다. `index_runs` 기록은 유지됩니다.
- `--out`: `index-file` 전용 SQLite store 경로입니다.

`index-pst`의 store 위치:

```text
<data-dir>/shards/<pst_id>/store.sqlite
```

생성되는 SQLite 테이블:

- `folders`: PST 내부 폴더 경로와 폴더 메타데이터
- `messages`: 메일 메타데이터, subject, sender, recipients, cc, plain body, HTML body, HTML text
- `index_errors`: 필드/메일 단위 추출 오류
- `index_runs`: 인덱싱 실행 요약

encoding status:

- `OK`: 정상 또는 가장 좋은 후보로 복구됨
- `DEGRADED`: 일부 깨짐 가능성이 있지만 저장 가능한 best text가 있음
- `UNRECOVERABLE`: raw byte 복구가 어렵고 getter 문자열도 깨진 것으로 판단됨
- `NULL`: 값 없음
- `ERROR`: getter 또는 변환 중 예외 발생

실제 PST 파일은 GitHub에 올리지 마세요. Integration test는 `PST_TEST_FILE` 환경변수가 있을 때만 실행됩니다.

```powershell
$env:PST_TEST_FILE="D:\MailArchive\oms39.pst"
.\gradlew.bat test
```

성공 기준:

- `INDEX SUMMARY`에서 `messagesSaved`가 `--limit` 또는 실제 메일 수만큼 증가
- `fatalErrors`가 0
- `store.sqlite`에 `folders`, `messages`, `index_runs` row가 생성
- subject/body/body_html/body_html_text status가 함께 저장

실패 시 확인할 것:

- PST 파일 경로와 잠금 상태
- `UNRECOVERABLE` 필드 비율
- `index_errors` 테이블 내용
- `encoding-probe --output` 보고서와 저장된 본문 비교

다음 Phase 3B에서는 저장된 SQLite 원문을 기반으로 검색 후보 생성 구조를 추가합니다. Phase 3B에서도 검색 결과를 곧바로 확정하지 않고 원문 재검증을 유지해야 합니다.

## Phase 3A-Verify SQLite Store Inspection

Phase 3A-Verify adds read-only tools for checking whether a generated `store.sqlite` is healthy enough to use as the basis for Phase 3B search candidate work. These commands do not implement Lucene, FTS, search, or source-text re-verification search.

Inspect the whole store:

```powershell
.\gradlew.bat run --args="inspect-store D:\MailArchive\oms39-store.sqlite"
```

Print stored message samples with short previews only:

```powershell
.\gradlew.bat run --args="sample-messages D:\MailArchive\oms39-store.sqlite --limit 10"
```

Show one message by `messages.id`:

```powershell
.\gradlew.bat run --args="show-message D:\MailArchive\oms39-store.sqlite --id 123"
```

Evaluate readiness for Phase 3B:

```powershell
.\gradlew.bat run --args="quality-report D:\MailArchive\oms39-store.sqlite"
```

Use UTF-8 report files when console encoding is suspicious:

```powershell
.\gradlew.bat run --args="sample-messages D:\MailArchive\oms39-store.sqlite --limit 10 --output D:\MailArchive\sample-messages-report.txt"
.\gradlew.bat run --args="show-message D:\MailArchive\oms39-store.sqlite --id 123 --output D:\MailArchive\message-123.txt"
.\gradlew.bat run --args="quality-report D:\MailArchive\oms39-store.sqlite --output D:\MailArchive\quality-report.txt"
```

`inspect-store` reports table counts, latest index run, status distributions, null counts, average body lengths, and sent/received date ranges.

`quality-report` returns one of:

- `READY`: store looks healthy enough for Phase 3B candidate-search work.
- `READY_WITH_WARNINGS`: Phase 3B can proceed, but degraded fields, null fields, or index_errors should be reviewed.
- `NOT_READY`: blocking issues exist, such as no messages, no folders, fatal errors, very low body coverage, or too many unrecoverable fields.

Phase 3B checklist:

- `messages` row count is greater than 0.
- `folders` row count is greater than 0.
- latest `fatalErrors` is 0.
- `UNRECOVERABLE` fields are not excessive.
- subject exists for most messages.
- at least one of `body_text` or `body_html_text` exists for most messages.
- `index_errors` top issues have been reviewed.

These inspection tools read only the SQLite store. They do not search mail content and do not replace the later source-text re-verification requirement.

## Phase 3B SQLite Store Search MVP

Phase 3B adds a local search MVP over the stored SQLite `messages` table. It does not use Lucene or SQLite FTS5 yet. The flow is deliberately conservative:

1. Use SQLite `LIKE` only to collect candidate messages.
2. Load the stored source fields from `messages`.
3. Re-check the query against the actual field values.
4. Print only verified messages and verified match locations.

Run a search:

```powershell
.\gradlew.bat run --args="search-store D:\MailArchive\oms39-store.sqlite RWP90H --limit 20"
```

Write a UTF-8 report file:

```powershell
.\gradlew.bat run --args="search-store D:\MailArchive\oms39-store.sqlite DA96-01139A --limit 20 --output D:\MailArchive\search-report.txt"
```

Control context size:

```powershell
.\gradlew.bat run --args="search-store D:\MailArchive\oms39-store.sqlite 삼성전자 --limit 20 --context 80"
```

Options:

- `--limit <n>`: maximum verified messages to print. Default is `20`.
- `--context <n>`: characters before and after the match. Default is `80`.
- `--output <path>`: write a UTF-8 report file with BOM.
- `--field <field>`: optional field filter. Supported values: `subject`, `sender`, `recipients`, `cc`, `folder`, `body`, `all`.
- `--max-matches-per-message <n>`: maximum matches shown per message. Default is `5`.

Searched fields:

- `subject`
- `sender_name`
- `sender_email`
- `recipients`
- `cc`
- `folder_path`
- `body_text`
- `body_html_text`

Result interpretation:

- `sqlCandidates` is the number of messages found by SQLite `LIKE` candidate search.
- `verifiedMessages` is the number of messages that passed source-field verification.
- `totalMatches` is the number of verified field matches printed.
- `policy` shows how the match was found: `EXACT`, `CASE_INSENSITIVE`, `NORMALIZED`, or `WHITESPACE_INSENSITIVE`.

Current limitations:

- This is not a high-speed full-text search engine.
- Lucene and SQLite FTS5 are not implemented in this phase.
- SQLite `LIKE` is only a candidate generator and can be slow on very large stores.
- Final results are more trustworthy than SQL candidates because every result is rechecked against stored source fields.
- Normalized and whitespace-insensitive matches may use approximate offsets when exact source offset mapping is not possible.

Phase 3C should replace the slow candidate layer with FTS5 or Lucene while keeping the same source-field verification step.

## Phase 3B-Fix Stored Text Quality Diagnostics

Phase 3B-Fix keeps the existing SQLite `LIKE` search flow and adds diagnostics for stored text quality. It still does not implement Lucene, SQLite FTS5, GUI, attachment search, or AI Q&A.

### Console Encoding vs Stored Text Problems

PowerShell console output can display Korean incorrectly even when the report file is correct. Prefer UTF-8 report files when checking Korean text:

```powershell
.\gradlew.bat run --args="search-store D:\MailArchive\oms39-store.sqlite RWP90H --limit 20 --output D:\MailArchive\search-rwp90h.txt"
```

Interpretation:

- If Korean is normal in the UTF-8 output file but broken in the console, treat it as a console encoding/display issue.
- If the UTF-8 output file still contains many `????`, replacement characters, `占`, or NUL-related artifacts, treat it as stored text extraction or recovery quality degradation.
- `body_html_text` can be more degraded than `subject`, `folder_path`, or sender fields because it often comes from HTML with mixed or missing charset metadata.

### NUL Character Cleanup

The store writer removes `\u0000` NUL characters from searchable stored fields before saving them. This is intended to turn values such as `M\u0000i\u0000c\u0000r\u0000o...` into `Microsoft Outlook` for search and display.

NUL cleanup is applied to:

- `folder_path`
- `subject`
- `sender_name`
- `sender_email`
- `recipients`
- `cc`
- `body_text`
- `body_html_text`

The raw `body_html` field is still treated cautiously because it may be useful for later extraction debugging.

### Diagnose Text Quality

Run a store-level text quality diagnostic report:

```powershell
.\gradlew.bat run --args="diagnose-text-quality D:\MailArchive\oms39-store.sqlite --limit 100 --output D:\MailArchive\text-quality-report.txt"
```

The report includes:

- `messagesChecked`
- `fieldsChecked`
- `okFields`
- `suspectFields`
- `degradedFields`
- `brokenFields`
- `nulCharFields`
- `questionHeavyFields`
- `mojibakeFields`
- `statusMismatchCount`
- example fields where stored status says `OK` but diagnostics see suspicious or degraded text

### Dump One Message Raw

Inspect one message with field lengths, NUL counts, question mark ratios, mojibake signals, previews, and Unicode code point samples:

```powershell
.\gradlew.bat run --args="dump-message-raw D:\MailArchive\oms39-store.sqlite --id 160 --output D:\MailArchive\message-160-raw.txt"
```

Use this when a search result shows degraded context and you need to decide whether the problem is console display, SQLite stored text, or earlier PST extraction/encoding recovery.

### Search Output Quality Fields

`search-store` now prints quality information for each matched field:

```text
- field: body_html_text
  fieldStatus: DEGRADED
  textQuality: SUSPECT
  qualityWarnings: high_question_mark_ratio
```

`fieldStatus` is the status stored in SQLite when available. `textQuality` and `qualityWarnings` are computed at read time from the actual stored field value.

### Before Moving To Phase 3C

Proceed only when:

- search results are returned for known terms such as `RWP90H` and `DA96-01767C`.
- UTF-8 output files show `subject`, `folder_path`, and sender fields correctly enough for business use.
- isolated `body_html_text` degradation is understood and acceptable.
- excessive `BROKEN` or `UNRECOVERABLE` fields have been reviewed with `diagnose-text-quality` and `dump-message-raw`.

Phase 3C can replace the slow candidate layer with Lucene or FTS5, but it must keep source-field re-verification and text quality reporting.

### Body HTML vs Body HTML Text Damage Triage

Some messages can have healthy metadata while `body_html_text` is already damaged in SQLite. If the UTF-8 output file shows repeated `U+003F` question marks in code point samples, the text has already been stored as literal `?`; that is not a PowerShell display problem.

Use `dump-message-raw` to compare the original stored HTML with the extracted HTML text:

```powershell
.\gradlew.bat run --args="dump-message-raw D:\MailArchive\oms39-store.sqlite --id 55 --output D:\MailArchive\message-55-raw.txt"
```

The dump now includes `body_html` and `body_html_text` length, preview, code point sample, question mark counts, question mark ratio, repeated question mark runs, detected HTML charset/meta charset, and a comparison verdict.

Comparison verdicts:

- `OK`: both `body_html` and `body_html_text` look usable.
- `SOURCE_HTML_BROKEN`: the stored `body_html` itself has strong broken-text signals.
- `HTML_TO_TEXT_EXTRACTION_PROBLEM`: `body_html` looks usable but `body_html_text` is degraded or broken, so the HTML-to-text extraction step is suspect.
- `GETTER_OR_RECOVERY_PROBLEM`: `body_html` is missing while `body_html_text` is degraded or broken, so the PST getter or recovery path needs review.
- `SOURCE_OR_DECODING_BROKEN`: both `body_html` and `body_html_text` are degraded or broken, so the problem likely happened before or during source decoding.

A damaged `body_html_text` does not automatically mean subject, folder, sender, recipient, or part-number search is unusable. In the observed store, metadata fields can be normal Korean while HTML-derived body context is damaged.

### Broken Match Display Policy

By default, `search-store` hides matches whose matched field is diagnosed as `textQuality: BROKEN`. This prevents badly damaged `body_html_text` context from drowning out reliable matches such as `subject` or sender fields. The command still performs SQL candidate search and source-field verification; the hiding is an output policy.

The summary shows how many verified matches were hidden:

```text
hiddenBrokenMatches: 2
```

Show BROKEN matches for debugging with:

```powershell
.\gradlew.bat run --args="search-store D:\MailArchive\oms39-store.sqlite RWP90H --limit 20 --include-broken --output D:\MailArchive\search-rwp90h-with-broken.txt"
```

Use the default output for normal work, and use `--include-broken` when diagnosing damaged body text extraction.
