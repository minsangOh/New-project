# PST Archive Search

Windows 로컬 PC에서 여러 Outlook `.pst` 아카이브를 등록하고, 장기적으로 통합 검색기로 확장하기 위한 Java 21 CLI 프로젝트입니다.

현재 저장소는 **Phase 1: Archive Catalog + PST 등록/상태 관리 CLI**만 구현합니다. 검색 기능, Lucene 인덱스, 메일 본문 저장, java-libpst 실제 scan은 아직 구현하지 않았습니다.

## Phase 1 범위

구현됨:

- archive catalog SQLite 생성
- PST 파일 등록
- PST 목록 조회
- PST 상세 조회
- PST 상태 변경
  - `active`
  - `warm`
  - `cold`
  - `archive`
  - `missing`
  - `invalid`
- PST fingerprint 계산
- PST 경로 변경 처리
- PST 존재 여부 및 fingerprint verify
- per-PST shard directory 생성
- `manifest.json` 생성
- java-libpst scan을 나중에 붙일 수 있는 `PstScanner` 인터페이스와 stub
- JUnit 5 테스트

아직 구현하지 않음:

- Lucene 검색 인덱스
- 메일 본문 저장
- 원문 재검증 검색
- java-libpst 실제 PST scan
- 첨부파일 검색
- GUI
- AI Q&A
- 전체 MVP 검색 기능

## 기술 스택

```text
Java 21
Gradle
picocli
SQLite via sqlite-jdbc
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
archive --data-dir "D:\PstArchiveSearch" init
```

## Windows 실행 방법

Java 21이 필요합니다.

```powershell
java -version
```

Gradle이 설치되어 있다면 다음처럼 실행합니다.

```powershell
gradle run --args="init"
gradle run --args="add-pst D:\MailArchive\2026\work_2026_Q2.pst --status active --period-from 2026-04-01 --period-to 2026-06-30"
gradle run --args="list"
```

배포용 zip을 만들려면:

```powershell
gradle installDist
```

생성 후:

```powershell
.\build\install\archive\bin\archive.bat init
```

## CLI 사용 예시

초기화:

```powershell
archive init
```

PST 등록:

```powershell
archive add-pst "D:\MailArchive\2026\work_2026_Q2.pst" --status active --period-from 2026-04-01 --period-to 2026-06-30
```

목록 조회:

```powershell
archive list
```

상세 조회:

```powershell
archive show <pst_id>
```

상태 변경:

```powershell
archive mark-active <pst_id>
archive mark-archive <pst_id>
archive mark-warm <pst_id>
archive mark-cold <pst_id>
```

PST 존재 여부 및 fingerprint 확인:

```powershell
archive verify
archive verify --pst <pst_id>
```

PST 파일을 외장 SSD 등 다른 경로로 옮긴 뒤 catalog 경로 재연결:

```powershell
archive move-pst <pst_id> "E:\MailArchive\2026\work_2026_Q2.pst"
```

통계:

```powershell
archive stats
```

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
gradle test
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

## 다음 Phase 2 계획

Phase 2에서는 PST 파서 검증에 집중합니다.

- java-libpst `0.9.3`
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
