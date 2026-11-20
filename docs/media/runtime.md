# 미디어 백엔드 실행과 현재 경계

[업로드 설계](README.md)의 파일 수신·검증·미리보기·미사용 정리를 구현한다. 프론트엔드 데모 저장은 아직 API와 연결하지 않는다.

## 실행 환경

- Java 21, PostgreSQL, Linux의 SecureDirectoryStream·POSIX 권한을 지원하는 파일 시스템을 사용한다.
- 로컬 실행과 미디어 테스트에는 `ffmpeg`, `ffprobe`, `prlimit`가 PATH에 있어야 한다. Ubuntu/Debian에서는 `apt-get install ffmpeg util-linux`로 준비한다. Docker 이미지와 CI는 이 도구를 설치한다. 파서 자원 제한 단위 테스트는 Python 3도 사용한다(애플리케이션 실행에는 불필요).
- `PAWMENTO_MEDIA_ROOT`는 Docker에서 `/var/lib/pawmento/media`, 로컬 기본값은 실행 폴더의 `.local/media`다. 공개 정적 경로로 연결하지 않는다.
- `pawmento.media.parser.ffmpeg`, `.ffprobe`로 도구 경로를 지정할 수 있다. `.timeout-seconds`는 실행 1회의 벽시계 제한(기본 30초, 최대 60초)이다. 자식 프로세스에 가상 메모리 1GiB·CPU 25초·출력 파일 16MiB·열린 파일 64개 제한을 적용한다. 파서 표준 출력은 64KiB까지 읽는다.
- 작업은 기본 2초, 정리는 60초 간격으로 확인한다. `pawmento.media.worker.enabled=false`로 자동 처리를 끄며, `.delay-ms`, `.cleanup-delay-ms`로 간격을 변경한다.

도구는 셸 없이 고정 옵션으로 실행한다. 입력 컨테이너와 로컬 파일 프로토콜만 허용한다. [FFmpeg](https://ffmpeg.org/ffmpeg.html)·[ffprobe](https://ffmpeg.org/ffprobe.html) 공식 문서를 따른다. FFmpeg 빌드·라이선스는 배포판 패키지 기준으로 관리하며 Java 코드에 바이너리를 포함하거나 링크하지 않는다.

## 검증과 저장

1. 수신 뒤 PROCESSING과 검증 작업을 같은 DB 트랜잭션으로 저장한다. 작업자는 10분 임대와 토큰으로 처리하고, 파일 I/O는 DB 트랜잭션 밖에서 수행한다.
2. 수신 크기·SHA-256·실제 시그니처·컨테이너·트랙·코덱과 전체 디코딩을 검사한다. 이미지 최대 4천만 픽셀, 음성 최대 5분, 입력·서비스본 최대 15MiB다. 영상·여러 트랙·미지원 코덱·손상 파일을 거부한다.
3. JPEG는 JPEG, PNG/WebP는 PNG 서비스본과 최대 512px JPEG 썸네일을 만든다. EXIF 방향을 보정하고 원본 메타데이터를 복사하지 않는다. WebM/Ogg의 Opus, MP4의 AAC, MP3 음성은 메타데이터를 복사하지 않는 M4A/AAC 서비스본으로 변환한다.
4. 서비스본은 서버 생성 키로 CREATE_NEW 저장한 뒤 실제 메타데이터와 함께 READY로 갱신한다. 원본은 제거하고 사용자에게 제공하지 않는다. 형식 오류는 REJECTED와 안전한 실패 코드로 기록한다.
5. 원본 정리까지 마친 작업만 DONE이 된다. 재시작 시 임대 만료 후 처리하며, 일시 장애는 지수 간격(최대 1시간)으로 재시도한다. 시도 수·오류 종류를 DB에 남긴다. FFmpeg stderr는 최대 16KiB까지 보존하며, 식별된 형식 오류만 REJECTED로 분류한다. I/O·알 수 없는 종료·타임아웃은 원본을 보존해 계속 재시도한다. 내부 계약·프로그래밍 오류는 자동 재시도 없이 FAILED로 남기고 원본을 보존하며, 운영자가 원인을 확인하거나 사용자가 취소할 수 있다. READY는 정리 실패로 되돌리지 않는다.

원본 이미지는 단일 프레임만 지원한다. PNG의 청크 길이·CRC·IEND 뒤 데이터와 APNG 청크, WebP의 RIFF 길이·애니메이션 청크, JPEG의 EOI 뒤 데이터를 검사하고 전체 원본 프레임 수와 디코딩을 확인한다. 다중 프레임과 뒤따르는 데이터는 거부하고 허용된 메타데이터는 서비스본에서 제거한다.

입력은 `tmp/uploads`, 파서 작업 공간은 `tmp/work`, 서비스본·썸네일은 `files`에 둔다. 최종 저장 뒤 DB 갱신 결과가 불명확하면 파일을 즉시 지우지 않는다. 고아 정리 시 자산과 작업 토큰을 확인해 회수한다. 늦게 도착한 이전 임대 결과는 취소·새 임대 결과를 덮어쓰지 못한다.

## 조회와 취소

상태 응답은 신고값 declared 필드와 검증된 `mimeType`, `sizeBytes`, `durationMs`, `width`, `height`, `previewPath`, `thumbnailPath`를 구분한다. 물리 경로·저장 키·체크섬·GPS는 반환하지 않는다.

| 경로 (`/api/v1` 생략) | 동작 |
| --- | --- |
| GET/HEAD `/media/{assetId}/preview?petId={petId}` | 미연결·유효 기간 내 READY의 업로더용 서비스본 |
| GET/HEAD `/media/{assetId}/thumbnail?petId={petId}` | 같은 권한의 이미지 썸네일 |
| DELETE `/media/{assetId}?petId={petId}` | 미연결 파일 삭제 예약 202. 중복 삭제는 멱등 처리 |

매 요청마다 pet 입력 포트로 실제 소유자와 업로더를 검사한다. 정확한 MIME·길이, nosniff, private/no-store와 안전한 파일명을 사용한다. [Spring MVC Resource Range 처리](https://docs.spring.io/spring/reference/web/webmvc/mvc-range.html)로 음성 206/416을 지원하며 HEAD는 본문을 보내지 않는다. 시작한 전송과 이미 받은 바이트는 회수하지 않는다.

## 다이어리 첨부 연결 (#32)

첨부 CRUD는 DiaryAttachmentJpaEntity·복합 식별자·JpaRepository로 처리한다. 기존 diary_attachments의 ID·순서·FK를 유지하며 교체 시 기존 엔티티 삭제를 flush한 뒤 새 연결을 저장한다. JPA가 테이블을 먼저 만든 신규 DB에도 초기화 SQL로 기존 제약을 보장한다. (고침 #32: 다이어리 첨부 저장을 JPA로 통일)

- 생성·수정 `attachmentIds`는 최대 5개이며 중복은 거부한다. 파일당 기존 15MiB 제한으로 전체는 최대 75MiB다. 수정 생략은 유지, 빈 배열은 해제이며 content 명시적 null은 400이다. 본문 없는 첨부 일기는 가능하지만 둘 다 없는 최종 상태는 거부한다.
- 부모 version 검사·부모 행 잠금 후 관련 자산을 ID 순서로 잠그고 첨부 관계·참조 수·삭제 작업을 같은 트랜잭션에서 변경한다. 유지 자산은 참조를 해제하지 않고 순서만 바꾼다. 마지막 참조만 삭제 예약하며 물리 파일 I/O는 기존 작업자가 수행한다.
- GET/HEAD `/diaries/{diaryId}/attachments/{assetId}?petId=...`와 `/thumbnail`은 현재 아이 접근·실제 첨부 관계를 검사한다. 음성 Range·HEAD와 private/no-store를 제공하고 미연결 미리보기를 우회하지 않는다.
- 첨부 참조 수가 0이거나 서비스 상태를 사용할 수 없으면 목록·상세는 해당 첨부를 `available=false`, 파일/썸네일 URL을 null로 반환하고 나머지 일기는 계속 조회한다. 파일 직접 열람은 계속 거부한다. 유지·순서 변경은 참조 수를 복구하지 않으며, 해제 시 이미 0인 참조는 감소시키지 않고 실제 부모 연결이 모두 사라진 경우에만 삭제 예약한다.
- 연결할 자산의 업로더·아이·용도는 상태 엔티티를 남기지 않는 일괄 identity 조회로 먼저 확인한다. 없는 자산과 접근할 수 없는 자산은 같은 404이며, 전체 사전 검사를 통과한 뒤에만 ID 순서로 자산 행을 잠근다.
- 현재 권한은 실제 pet 소유자 범위이며 가족 열람이 구현된 것은 아니다. 프론트 연결·지도/GPS는 후속이다. 질문 답변 첨부는 아래 절을 따른다. 요청 의미·시나리오는 [첨부 계획](../diary/attachments.md)을 따른다.

## 정리와 후속 연결

- 만료 PENDING(10분), 중단 UPLOADING(15분), REJECTED, 미연결 READY(24시간)를 삭제 예약한다. 자산 잠금 아래 참조 수·참조 확인 포트를 검사한다.
- 물리 삭제 실패는 DELETING과 작업을 유지하고 재시도한다. 없는 파일 삭제는 성공이며 DELETED는 미완료 업로드 한도에서 제외한다.
- 24시간 지난 미추적 파일만 회수한다. READY와 현재 작업 파일을 보존하며 오래된 파서·multipart 임시 파일도 정리한다.
- diary는 첨부 테이블·참조 획득/해제·부모 권한 파일 경로를 연결한다. 참조 확인 어댑터는 실제 diary 첨부와 참조 수를 모두 확인하며 물리 삭제 직전에도 재검사한다. question 첨부·가족 권한과 참조 수 불일치의 자동 복구는 아직 구현하지 않았다.
- 위치 동의 정책·프론트 GPS 제거 확정 전 `locationConsent.granted=true`는 409로 거부하며 GPS 후보는 추출하지 않는다.
- 실제 모바일 녹음·재생, 배포 프록시·볼륨 유지와 외부 테스터 수용 전 백업·복구는 별도 환경 검증 대상이다. 악성코드 백신 도입을 뜻하지 않는다.

## 예외 처리

[#62 공통 예외 규칙](../backend-exceptions.md)을 적용한다. 도메인의 값·소유권·상태 오류는 `domain/exception`, 조회 실패·작업 흐름·저장소 장애는 `application/exception`에 둔다. 서비스는 도메인 예외를 다른 예외로 바꾸지 않으며, 파일·DB 기술 예외는 출력 어댑터에서 변환한다. 업로드 실패 정리와 검증 작업 재시도 catch는 유지한다.

미디어 전용 HTTP 핸들러 없이 `GlobalExceptionHandler`가 공통 기본 예외를 변환한다. 기존 400/403/404/409와 413(용량)/415(형식)/503(저장소) 상태 및 `{code, message}` 계약을 유지한다. 입력 크기·형식과 저장소 장애 공통 예외 세 종류의 확장도 같은 문서에 반영했다. 회원·반려동물 예외 전환은 별도 범위다.

## 경로·정책 설정 (#33 리뷰 반영)

`POST /media/uploads?petId={petId}`는 별도 세션 테이블 대신 업로드 세션 역할을 하는 PENDING 자산을 만든다. 반환된 assetId는 수신·검증·READY·삭제까지 같은 자산을 가리킨다. 이후 `/media/{assetId}?petId={petId}` 경로는 그 자산에 대한 조회·삭제다. 이는 별개의 세션 ID와 자산 ID를 혼용한 것이 아니다. 지도(#63)와 동일하게 도메인별 루트를 따르며, 반려동물은 필수 petId 쿼리로 지정한다. 기존 중첩 경로는 제공하지 않는다. 업로드 필터는 multipart 파싱을 유발하지 않도록 raw query에서 양의 정수 petId 하나만 읽어 소유권을 확인하고, 중복·누락·잘못된 값은 파싱 전에 거부한다.

- `pawmento.media.lifecycle.session-ttl=10m`: 세션 수명
- `pawmento.media.lifecycle.unattached-ttl=24h`: READY 이후 미연결 수명
- `pawmento.media.lifecycle.upload-timeout=15m`: 중단 수신 회수 기준
- `pawmento.media.lifecycle.work-lease=10m`: 작업 임대
- `pawmento.media.lifecycle.max-open-uploads=5`: 업로더별 미완료 수신·검증·정리 한도

임대·재시도 간격·상태는 작업 도메인이 결정한다. 기본 재시도는 지수 간격, 최대 1시간이며 저장소 장애는 횟수만으로 원본을 거부하거나 삭제하지 않는다. 완료된 READY는 미완료 한도에서 제외한다. 원본 정리가 남은 READY·실패 파일·DELETING은 실제 정리 전까지 한도를 차지한다. READY 보관은 별도 24시간 수명으로 제한한다. 이것은 볼륨 전체 용량 제한을 대체하지 않는다.

다중 Range GET은 길이를 Spring의 Resource 변환기에 맡기며 HEAD에만 전체 길이를 설정한다. 생성·수신·상태 조회는 동일 조회 스냅샷 `GetMediaUploadResult`를 재사용하고, 미리보기는 종류를 포함한 `GetMediaPreviewQuery` 하나, 삭제는 `DeleteMediaUploadCommand`를 받는다.

## 공통 규칙 제안과 적용 범위

불변 정책/작업 전달 record 허용, 생성·수신에서 동일 조회 스냅샷 `GetMediaUploadResult` 재사용, 웹 필터/입력 I/O 경계의 제한된 처리는 이번 PR의 공통 규칙 변경 제안이다. 기존에 이미 확정된 규칙을 그대로 따른 부분과 구분하며 공통 구조 문서에서 함께 검토한다. 공통 예외 3종 확장도 같은 제안 범위다. API의 도메인별 루트는 예외를 제안하지 않고 기존 규칙을 그대로 적용한다.

## petId 입력과 임시 샘플 정리

생성·상태 조회·GET/HEAD 미리보기·썸네일·삭제 컨트롤러와 파일 수신 필터는 `MediaPetRequest.fromQuery` 공통 파서로 URL의 raw query에서만 `petId`를 읽는다. `@ModelAttribute`·`@Valid` DTO 바인딩을 사용하지 않으며 HTTP 헤더·JSON 본문·multipart 필드로 필수 쿼리를 채울 수 없다. 유효한 쿼리와 상충하는 헤더는 무시한다. 누락·빈 값·0 이하·숫자가 아닌 값·Long 범위 초과·잘못된 인코딩·중복(디코딩 후 같은 키 포함)은 400 `VALIDATION`과 공통 `{code, message}`로 응답한다. 파일 수신은 필터가 `getParameter*`를 호출하지 않고 multipart 파싱 전에 쿼리·소유권을 검사한다. 전역의 필수 쿼리 오류 처리 정책을 새로 정한 것은 아니다. (고침 #79: 쿼리 전용 검증과 헤더 우회 차단 반영)

PR #64에서 임시 `com.pawmento.record`·`com.pawmento.memory` 샘플과 `/records`·`/memories` API를 제거했다. Java record 값 타입이나 diary/question/map 데이터 모델을 제거하는 뜻은 아니며 기존 DB 테이블 삭제 작업도 포함하지 않는다. 실제 `diaryId`의 의미와 diary→map 경계는 [다이어리](../diary/README.md#도메인-경계)·[지도](../map/README.md#다이어리-연동-경계)를 따른다. (고침 #80: 샘플 제거 상태와 실제 도메인 연결)


## 질문 답변 첨부

- question의 첨부 CRUD도 기존 테이블과 복합키를 보존한 JPA 엔티티·리포지토리로 처리한다. question은 QUESTION_ANSWER_ATTACHMENT 파일만 연결한다. 기존 미디어 입력 포트에 목적 문자열을 추가했고 기존 생성자는 DIARY_ATTACHMENT 기본값을 유지한다.
- 연결·교체·삭제는 답변·질문 완료·진행·미디어 참조 수·삭제 작업과 같은 트랜잭션이다. 실제 질문 첨부 연결도 미디어 정리/직접 삭제의 참조 검사에 포함한다.
- 인증 원본/Range/HEAD: `/api/v1/questions/answers/{answerId}/attachments/{assetId}/content?petId=`. 이미지 썸네일은 `/thumbnail`이다. 부모 접근·실제 연결 검사 후에만 파일을 연다. 소유자 범위이며 가족·프론트 연결은 후속이다.
- 일반 첨부 CRUD의 JdbcTemplate 직접 사용은 제거한다. 스키마/기존 데이터 이관 SQL과 검증용 JDBC는 별도 용도이며 계속 사용한다. (고침 #31: 질문 첨부도 JPA 저장소로 전환)
- 요청·응답·제한은 [질문 첨부 계약](../question/question-flow.md#사진음성-첨부-연결-계약)을 따른다.
