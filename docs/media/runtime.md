# 미디어 백엔드 실행과 현재 경계

[업로드 설계](README.md)의 파일 수신·검증·미리보기·미사용 정리를 구현한다. 프론트엔드 데모 저장은 아직 API와 연결하지 않는다.

## 실행 환경

- Java 21, PostgreSQL, Linux의 SecureDirectoryStream·POSIX 권한을 지원하는 파일 시스템을 사용한다.
- 로컬 실행과 미디어 테스트에는 `ffmpeg`, `ffprobe`, `prlimit`가 PATH에 있어야 한다. Ubuntu/Debian에서는 `apt-get install ffmpeg util-linux`로 준비한다. Docker 이미지와 CI는 이 도구를 설치한다.
- `PAWMENTO_MEDIA_ROOT`는 Docker에서 `/var/lib/pawmento/media`, 로컬 기본값은 실행 폴더의 `.local/media`다. 공개 정적 경로로 연결하지 않는다.
- `pawmento.media.parser.ffmpeg`, `.ffprobe`로 도구 경로를 지정할 수 있다. `.timeout-seconds`는 실행 1회의 벽시계 제한(기본 30초, 최대 60초)이다. 자식 프로세스에 가상 메모리 1GiB·CPU 25초·출력 파일 16MiB·열린 파일 64개 제한을 적용한다. 파서 표준 출력은 64KiB까지 읽는다.
- 작업은 기본 2초, 정리는 60초 간격으로 확인한다. `pawmento.media.worker.enabled=false`로 자동 처리를 끄며, `.delay-ms`, `.cleanup-delay-ms`로 간격을 변경한다.

도구는 셸 없이 고정 옵션으로 실행한다. 입력 컨테이너와 로컬 파일 프로토콜만 허용한다. [FFmpeg](https://ffmpeg.org/ffmpeg.html)·[ffprobe](https://ffmpeg.org/ffprobe.html) 공식 문서를 따른다. FFmpeg 빌드·라이선스는 배포판 패키지 기준으로 관리하며 Java 코드에 바이너리를 포함하거나 링크하지 않는다.

## 검증과 저장

1. 수신 뒤 PROCESSING과 검증 작업을 같은 DB 트랜잭션으로 저장한다. 작업자는 10분 임대와 토큰으로 처리하고, 파일 I/O는 DB 트랜잭션 밖에서 수행한다.
2. 수신 크기·SHA-256·실제 시그니처·컨테이너·트랙·코덱과 전체 디코딩을 검사한다. 이미지 최대 4천만 픽셀, 음성 최대 5분, 입력·서비스본 최대 15MiB다. 영상·여러 트랙·미지원 코덱·손상 파일을 거부한다.
3. JPEG는 JPEG, PNG/WebP는 PNG 서비스본과 최대 512px JPEG 썸네일을 만든다. EXIF 방향을 보정하고 원본 메타데이터를 복사하지 않는다. WebM/Ogg의 Opus, MP4의 AAC, MP3 음성은 메타데이터를 복사하지 않는 M4A/AAC 서비스본으로 변환한다.
4. 서비스본은 서버 생성 키로 CREATE_NEW 저장한 뒤 실제 메타데이터와 함께 READY로 갱신한다. 원본은 제거하고 사용자에게 제공하지 않는다. 형식 오류는 REJECTED와 안전한 실패 코드로 기록한다.
5. 원본 정리까지 마친 작업만 DONE이 된다. 재시작 시 임대 만료 후 처리하며, 일시 장애는 지수 간격(최대 1시간)으로 재시도한다. 시도 수·오류 종류를 DB에 남긴다. 처리 장애 5회 누적 시 검증은 실패 처리하고 원본 정리를 계속 재시도한다. READY는 정리 실패로 되돌리지 않는다.

입력은 `tmp/uploads`, 파서 작업 공간은 `tmp/work`, 서비스본·썸네일은 `files`에 둔다. 최종 저장 뒤 DB 갱신 결과가 불명확하면 파일을 즉시 지우지 않는다. 고아 정리 시 자산과 작업 토큰을 확인해 회수한다. 늦게 도착한 이전 임대 결과는 취소·새 임대 결과를 덮어쓰지 못한다.

## 조회와 취소

상태 응답은 신고값 declared 필드와 검증된 `mimeType`, `sizeBytes`, `durationMs`, `width`, `height`, `previewPath`, `thumbnailPath`를 구분한다. 물리 경로·저장 키·체크섬·GPS는 반환하지 않는다.

| 경로 (`/api/v1` 생략) | 동작 |
| --- | --- |
| GET/HEAD `/pets/{petId}/media/{assetId}/preview` | 미연결·유효 기간 내 READY의 업로더용 서비스본 |
| GET/HEAD `/pets/{petId}/media/{assetId}/thumbnail` | 같은 권한의 이미지 썸네일 |
| DELETE `/pets/{petId}/media/{assetId}` | 미연결 파일 삭제 예약 202. 중복 삭제는 멱등 처리 |

매 요청마다 pet 입력 포트로 실제 소유자와 업로더를 검사한다. 정확한 MIME·길이, nosniff, private/no-store와 안전한 파일명을 사용한다. [Spring MVC Resource Range 처리](https://docs.spring.io/spring/reference/web/webmvc/mvc-range.html)로 음성 206/416을 지원하며 HEAD는 본문을 보내지 않는다. 시작한 전송과 이미 받은 바이트는 회수하지 않는다.

## 정리와 후속 연결

- 만료 PENDING(10분), 중단 UPLOADING(15분), REJECTED, 미연결 READY(24시간)를 삭제 예약한다. 자산 잠금 아래 참조 수·참조 확인 포트를 검사한다.
- 물리 삭제 실패는 DELETING과 작업을 유지하고 재시도한다. 없는 파일 삭제는 성공이며 DELETED는 미완료 업로드 한도에서 제외한다.
- 24시간 지난 미추적 파일만 회수한다. READY와 현재 작업 파일을 보존하며 오래된 파서·multipart 임시 파일도 정리한다.
- diary/question 첨부 테이블·참조 획득/해제·부모 파일 경로·가족 권한은 아직 구현하지 않았다. 현재 참조 확인 어댑터는 자산의 참조 수를 검사한다. 부모 구현 시 실제 첨부 확인 포트와 교체하고 참조 정합성 검사도 연결해야 한다.
- 위치 동의 정책·프론트 GPS 제거 확정 전 `locationConsent.granted=true`는 409로 거부하며 GPS 후보는 추출하지 않는다.
- 실제 모바일 녹음·재생, 배포 프록시·볼륨 유지와 외부 테스터 수용 전 백업·복구는 별도 환경 검증 대상이다. 악성코드 백신 도입을 뜻하지 않는다.
