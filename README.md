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
