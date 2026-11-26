# 게시글 신고 접수와 운영 목록

관련 이슈: [#112](https://github.com/likelion-backendschool-9th/pawmento/issues/112). 상위 이슈 #92, 관리자 계획은 [report-management.md](../admin/report-management.md)다. 글 계약은 [post.md](../community/post.md)(#21)를 따른다. 신고를 `report` 도메인이 맡고 대상 도메인을 입력 포트로 부르는 경계는 [report README](README.md#정한-것)에 있다.

## 한눈에
- 끝나면: 로그인한 회원이 `POST /api/v1/reports`로 남의 글을 신고하고, 신고 당시 글 내용이 함께 저장된다. 관리자 목록이 쓸 `ListReportsUseCase`가 실제 신고 테이블과 글 상태를 읽어 동작한다.
- 결정 필요: 없음. `trim()` 공용화는 팀 합의를 기다리며, 그전에는 `report` 도메인에 같은 함수를 둔다([아직 정하지 않은 것](README.md#아직-정하지-않은-것)).
- 규모: 5단계, 시나리오 15개, PR 2개(접수 / 목록과 관리자 파일 이동)

## 1. 신고 접수 (#112-R1)
로그인한 회원이 다른 회원의 공개 글을 신고 유형을 골라 신고하면(`OTHER`는 설명을 함께 적는다) PENDING 신고가 저장되고, 같은 시점의 글 제목·본문·카테고리·버전이 함께 저장된다.

#112-S1 정상 [테스트]
- Given 회원 B가 쓴 글 7(제목 `산책 후기`, 카테고리 `LIFE`, version 2)이 있다
- When  회원 A가 `POST /api/v1/reports`에 `{"targetType":"POST","targetId":7,"reasonType":"SPAM"}`을 보낸다
- Then  201과 `{"reportId": <새 id>}`를 받는다
- Then  `reports`에 target_type `POST`, target_id 7, status `PENDING`, reporter A, reasonType `SPAM`, reasonDetail `null`, target_author_id B, snapshot 제목 `산책 후기`·본문·카테고리 `LIFE`·targetVersion 2가 저장된다 (고침 #112: report 도메인 분리와 대상 일반화, PR #276 리뷰로 작성자 ID 저장)

#112-S2 경계: 접수 뒤 글 수정 [테스트]
- Given S1의 신고가 저장됐다
- When  B가 글 7을 제목 `수정한 글`로 고친다(version 3)
- Then  신고의 snapshot 제목은 `산책 후기`, targetVersion은 2로 그대로다

#112-S13 경계: 글을 읽은 뒤 수정·삭제가 먼저 커밋 [테스트]
- Given 회원 B가 쓴 글 7(제목 `산책 후기`, 카테고리 `LIFE`, version 2)이 있다
- When  A의 접수 트랜잭션이 글 7을 읽은 뒤 멈추고, 그사이 별도 트랜잭션에서 B가 글 7을 제목 `수정한 글`로 고쳐 커밋한 다음 접수가 이어진다(실제 PostgreSQL, latch로 순서 제어)
- Then  201이고 신고의 snapshot 제목·본문·카테고리·targetVersion은 처음 읽은 `산책 후기`·원래 본문·`LIFE`·2다
- When  같은 순서에서 B가 수정 대신 글 7을 지워 커밋한다
- Then  201이고 snapshot은 처음 읽은 값이며, 운영 목록(`ListReportsUseCase`, PENDING, size 20)에서 그 신고의 `targetState`는 `DELETED`다 (고침 #112: `postState`를 `targetState`로)

## 2. 중복 신고 (#112-R2)
같은 회원이 같은 대상을 처리 대기 중에 다시 신고하면 409다. 기각된 뒤에는 대상이 그사이 수정됐을 때만 다시 접수한다. 다른 회원의 신고와는 상관없다.

#112-S3 실패: 처리 대기 중 재신고 [테스트]
- Given A의 글 7 신고가 PENDING이다
- When  A가 글 7을 `ABUSE`로 다시 신고한다
- Then  409 `CONFLICT` "이미 신고한 게시글이에요."를 받고 A의 신고 행은 1개다
- When  B가 글 7을 고쳐 version이 오른 뒤 A가 다시 신고한다
- Then  여전히 409다
- When  회원 C가 글 7을 신고한다
- Then  201이고 글 7의 PENDING 신고는 A·C의 2개다

#112-S4 경계: 기각 뒤 재신고 [테스트]
- Given A의 글 7 신고(snapshot targetVersion 2)가 DISMISSED다
- When  글 7이 version 2 그대로일 때 A가 다시 신고한다
- Then  409 "이미 신고한 게시글이에요."다
- When  B가 글 7을 고쳐 version 3이 된 뒤 A가 다시 신고한다
- Then  201이고 새 신고의 snapshot targetVersion은 3이다

#112-S5 실패: 동시 신고 [테스트]
- Given A의 글 7 신고가 없다
- When  A의 신고 요청 두 개가 별도 트랜잭션에서 둘 다 중복 조회를 통과한 뒤 저장한다(실제 PostgreSQL, latch로 순서 제어)
- Then  하나는 201, 하나는 409 `CONFLICT` "이미 신고한 게시글이에요."다
- Then  커밋 뒤 새 트랜잭션에서 A의 글 7 PENDING 신고는 1개다

## 3. 본인 글 신고 금지 (#112-R3)
작성자는 자기 글을 신고할 수 없다.

#112-S6 실패 [테스트]
- Given B가 쓴 글 7이 있다
- When  B가 글 7을 신고한다
- Then  403 `FORBIDDEN` "내 글은 신고할 수 없어요."이고 신고 행은 생기지 않는다

## 4. 신고 대상과 로그인 (#112-R4)
대상 종류는 지금 `POST`만 받는다. 없는 글과, 접수할 때 글을 읽는 시점에 이미 지운 글은 공개 상세와 같이 404다. 비로그인은 401이다.

#112-S7 실패 [테스트]
- Given 글 8은 지웠고 글 999는 없다
- When  A가 `targetType` `POST`로 글 8, 글 999를 신고한다
- Then  둘 다 404 `NOT_FOUND` "신고할 글을 찾을 수 없어요."이고 신고 행은 생기지 않는다 (고침 #112: 대상 조회를 report가 맡아 문구를 신고 쪽에서 정함)
- When  로그인하지 않고 글 7을 신고한다
- Then  401이다

#112-S14 실패: 잘못된 대상 [테스트]
- Given A는 글 7을 신고한 적이 없다
- When  `targetType`이 없거나 `post`·`COMMENT`·`UNKNOWN`이다
- Then  400 `VALIDATION` "신고 대상을 선택해 주세요."다. `COMMENT`는 댓글 신고를 만들 때 받는다
- When  `targetId`가 없거나 0 이하이거나 문자열 `"7"`이다
- Then  400 `VALIDATION`이다
- Then  모두 신고 행이 생기지 않는다

## 5. 신고 사유 입력 (#112-R5)
유형은 필수이고 6개 중 하나를 고른다. 설명은 `OTHER`일 때만 받으며, 앞뒤 공백을 지운 뒤 1~200자다. 다른 유형에 설명을 보내면 거절한다.

#112-S8 정상: 기타 [테스트]
- Given 회원 B가 쓴 글 7이 있다
- When  A가 `{"targetType":"POST","targetId":7,"reasonType":"OTHER","reasonDetail":"  사진 속 차량 번호가 보여요  "}`로 신고한다
- Then  201이고 reasonDetail `사진 속 차량 번호가 보여요`가 저장된다

#112-S9 실패: 잘못된 사유 [테스트]
- Given A는 글 7을 신고한 적이 없다. 성공하는 경우는 각각 신고가 없는 상태에서 확인한다
- When  요청 본문이 JSON `null`이거나 유형이 없거나 `spam`·`UNKNOWN`·숫자 `1`이다
- Then  400 `VALIDATION`이다
- When  유형이 `OTHER`이고 설명이 없거나, 공백·줄바꿈·BOM(`﻿`)만 있다
- Then  400 `VALIDATION`이다
- When  유형이 `OTHER`이고 공백을 지운 설명이 201자다
- Then  400 `VALIDATION`이고, 1자와 200자는 201이다
- When  유형이 `OTHER`이고 설명에 NUL 문자(`\u0000`)가 있거나, 설명이 숫자·불리언이다
- Then  400 `VALIDATION`이다 (고침 #112: PR #267 리뷰에서 500으로 빠지거나 문자열로 바뀌던 입력)
- When  유형이 `SPAM`이고 설명 `광고예요`를 함께 보낸다
- Then  400 `VALIDATION`이다
- Then  400인 경우 모두 신고 행이 생기지 않는다

## 6. 운영 목록 (#112-R6)
`ListReportsUseCase`가 상태별 신고 요약을 ID 내림차순 커서 페이지로 돌려준다. 페이지는 신고 테이블만으로 정하고, 각 항목의 현재 대상 상태는 그 페이지의 대상 id들로 대상 도메인을 한 번에 조회해 붙인다. (고침 #112: 출력 포트 대신 report의 입력 포트로 제공)

#112-S10 정상: 상태·정렬·커서 [테스트]
- Given PENDING 신고 21건(id 1~10, 12~22. id 11은 비어 있다)과 더 큰 id의 RESOLVED 23, DISMISSED 24가 있다
- When  `find(PENDING, size 20, before null)`을 부른다
- Then  id 22~2(11 제외)의 20건이 내림차순으로 오고 `hasMore`는 true다
- When  `find(PENDING, size 20, before 2)`를 부른다
- Then  id 1의 1건이 오고 `hasMore`는 false다
- When  `find(PENDING, size 3, before 11)`(없는 id)을 부른다
- Then  id 10~8이 온다
- When  `find(RESOLVED, 20, null)`과 `find(DISMISSED, 20, null)`을 부른다
- Then  각각 id 23, id 24만 온다
- When  `find(PENDING, 20, before 1)`을 부른다
- Then  빈 목록이고 `hasMore`는 false다
- Then  각 항목의 `reportId`·`targetType`·`targetId`·`status`·`reasonType`·`createdAt`·`snapshotTitle`이 저장값과 같고 `snapshotExpired`는 false다 (고침 #112: `postId`를 `targetType`·`targetId`로)

#112-S11 경계: 지운 글의 신고 [테스트]
- Given 글 7(제목 `산책 후기`)의 PENDING 신고가 있다
- When  B가 글 7을 지운 뒤 운영 목록(PENDING, size 20)을 조회한다
- Then  그 신고가 목록에 남고 `snapshotTitle`은 `산책 후기`, `targetState`는 `DELETED`다. 지우지 않은 글은 `VISIBLE`이다

#112-S12 경계: 스냅샷을 정리한 신고 [테스트]
- Given DISMISSED 신고 30의 `snapshot_cleared_at`이 채워져 있고 `snapshot_title`·`snapshot_body`·`reason_detail`이 null이다(테스트가 DB에 직접 넣는다)
- When  `find(DISMISSED, 20, null)`을 부른다
- Then  신고 30이 목록에 남고 `snapshotTitle`은 null, `snapshotExpired`는 true다

#112-S15 경계: 대상을 찾을 수 없는 신고 [테스트]
- Given PENDING 신고 41·42·43이 있고, 신고 42의 대상 글 id 900은 `posts`에 행이 없다(테스트가 DB에 직접 넣는다)
- When  운영 목록(PENDING, size 2)을 조회하고 받은 `nextCursor`로 다음 페이지를 조회한다
- Then  첫 페이지는 신고 43·42이고 `nextCursor`는 `42`다. 신고 42는 빠지지 않고 `snapshotTitle`이 저장값 그대로이며 `targetState`는 `MISSING`이다
- Then  다음 페이지는 신고 41이다. 대상 조회 결과가 빠져도 페이지 크기와 커서는 신고 id 기준 그대로다

## 범위 밖
- 관리자 목록 HTTP 연결·컨트롤러 등록과 웹 응답 검사의 새 필드 반영(#92 관리자 담당). 이 이슈는 패키지 이동과 결과·응답 필드 변경까지 한다
- 관리자 상세·처리·기각·게시글 숨김(#92-R2·R3). 숨김 컬럼, 목록의 `HIDDEN`, 숨긴 글의 신고 404는 #92의 처리·숨김 단계에서 더하고, 그 단계의 완료 조건으로 검증한다
- 댓글 신고(`COMMENT`). 댓글 기능이 생기면 대상 종류와 대상 조회 어댑터를 더한다
- 처리 1년 뒤 스냅샷 정리 작업, 자동 숨김, 프론트 신고 화면 연결
- 탈퇴 회원의 신고 정리(탈퇴 뒤에도 신고와 내부 ID는 보존 정책대로 남긴다)
- `trim()` 함수를 `global.text`로 합치는 일. 팀 합의 뒤 구조 문서와 함께 바꾼다
- 기각 뒤 같은 버전 재신고 경합: 같은 회원의 요청이 "마지막 신고는 이전 버전 기각"을 읽고 저장하기 전에, 다른 요청이 현재 버전으로 접수되고 관리자가 그것을 기각하면 같은 버전의 신고가 두 개 남는다. #112에는 기각 API가 없어 운영에서 `DISMISSED`가 생기지 않으므로 이 경합도 생기지 않는다. #92의 처리·기각 단계에서 막고 그 단계의 회귀 시나리오로 확인한다. 방법(같은 회원·대상 조합의 PostgreSQL advisory lock, 또는 `(target_type, target_id, reporter_id, snapshot_target_version)` 고유 인덱스)은 숨김·복구 규칙과 함께 그 단계에서 고른다. 고유 인덱스는 숨김 해제처럼 대상 버전이 오르지 않는 복구가 생기면 재신고를 영구히 막으므로, 지금 스키마로 굳히지 않는다

## 작업 순서
확인 명령은 모두 `backend/`에서 실행하고, 그 단계에서 바꾼 테스트만 돌린다. 전체 테스트는 PR의 CI에서 확인한다. 아래 경로의 `report/`는 `com.pawmento.report`, `community/`는 `com.pawmento.community`다. 1~4단계가 PR 1(접수, 머지 보류 중인 PR #267을 이 구조로 고친다), 5단계가 PR 2(목록, #112를 닫는다)다.

1. 신고 도메인 — S8, S9, S14의 도메인 규칙
   - 파일: `report/domain/Report.java`(`receive(ReportTarget, reporterId, reasonType, reasonDetail)`. 유형 필수, `OTHER`만 설명 1~200자, NUL 거절, 다른 유형의 설명 거절), `ReportReason.java`, `TargetType.java`(`POST`), `ReportTarget.java`(대상 종류·id·작성자·버전·제목·본문·카테고리 코드를 담는 신고 쪽 값 객체), `PreviousReport.java`, `TextTrim.java`(임시, `Post`와 같은 기준), `domain/exception/InvalidReportException.java`·`OwnTargetReportException.java`, `test/.../report/domain/ReportTest.java`·`PreviousReportTest.java`
   - `report` 도메인은 community의 `Post`·`TextTrim`을 import하지 않는다. PR #267이 만든 `community/domain/TextTrim`은 되돌려 `Post` 안의 함수로 둔다.
   - 확인: `./gradlew test --tests 'com.pawmento.report.domain.*'`
2. community의 신고용 글 조회 입력 포트 — S1, S7의 대상 조회
   - 파일: `community/application/port/in/GetPostForReportUseCase.java`·`PostForReportResult.java`(id·작성자·제목·본문·카테고리 코드·버전), `community/application/service/GetPostForReportService.java`(지운 글은 빈 결과)
   - 기존 `LoadPostPort`를 그대로 쓴다. 테스트는 3단계의 시나리오 테스트가 맡는다.
3. 신고 테이블과 접수 API, 동시 접수 — S1, S2, S3, S5, S6, S7, S8, S9, S13, S14
   - 파일: `report/adapter/out/persistence/ReportJpaEntity.java`·`ReportJpaRepository.java`·`ReportPersistenceAdapter.java`(저장 직후 `flush`, `ux_reports_pending` 위반만 `DuplicateReportException`으로 바꾸고 cause 보존), `resources/db/report-schema.sql`, `report/adapter/out/persistence/ReportSchemaConfiguration.java`, `report/adapter/out/post/PostReportTargetAdapter.java`(`LoadReportTargetPort`를 `GetPostForReportUseCase`로 구현), `report/application/port/in/CreateReportUseCase.java`·`CreateReportCommand.java`, `report/application/port/out/LoadReportTargetPort.java`·`SaveReportPort.java`·`LoadLatestReportPort.java`, `report/application/service/CreateReportService.java`, `report/application/exception/DuplicateReportException.java`·`ReportTargetNotFoundException.java`, `report/adapter/in/web/ReportController.java`(`POST /reports`)·`CreateReportRequest.java`·`CreateReportResponse.java`·`JsonStringOnly.java`, `test/.../report/ReportScenarioTest.java`, `test/.../report/ReportConcurrentCreateTest.java`
   - PR #267이 `PostController`에 더한 `POST /{postId}/reports`와 `community` 아래의 신고 파일은 모두 옮기고, `PostScenarioTest`의 `post_reports` 정리는 되돌린다(신고 테이블이 `posts`를 FK로 참조하지 않는다).
   - S13은 `LoadReportTargetPort`를 `@Primary` 래퍼로 감싸 대상을 읽은 직후 latch로 멈추게 하고, 그사이 다른 스레드가 수정·삭제를 커밋한다. 3단계에서는 201과 스냅샷을 확인하고, 목록의 `targetState`는 5단계에서 같은 테스트에 더한다.
   - 확인: `./gradlew test --tests 'com.pawmento.report.ReportScenarioTest' --tests 'com.pawmento.report.ReportConcurrentCreateTest' --tests 'com.pawmento.community.PostScenarioTest'`
4. 기각 뒤 재신고 — S4
   - 파일: `CreateReportService`(가장 최근 신고 판정), `test/.../report/ReportScenarioTest.java`
   - 확인: `./gradlew test --tests 'com.pawmento.report.ReportScenarioTest'`
5. 운영 목록과 관리자 파일 이동 — S10, S11, S12, S13의 목록 확인, S15
   - 관리자 담당이 만든 `community` 아래 목록 파일 16개를 `report`로 옮긴다(아래 표). 옮기는 것과 필드를 바꾸는 것은 커밋을 나눠, 첫 커밋은 패키지 선언과 import만 바꾼다.
   - 계약 변경: `FindReportPagePort.Item`은 `postId`·`postState` 대신 `targetType`·`targetId`를 갖고 `snapshotExpired`를 더한다. `ListReportItemResult`는 `postId`·`PostState` 대신 `targetType`·`targetId`·`targetState`(`VISIBLE`/`HIDDEN`/`DELETED`/`MISSING`)를 갖고 `snapshotExpired`를 더한다. `ReportListItemResponse`도 같은 9개 필드가 된다.
   - 파일: `ReportPersistenceAdapter`가 `FindReportPagePort`도 구현하고 조회 쿼리는 `ReportJpaRepository`에 둔다(`size + 1`건으로 `hasMore`, `snapshot_cleared_at`으로 `snapshotExpired`). `report/application/port/out/FindTargetStatesPort.java`(대상 종류와 id 목록 → 찾은 대상의 상태), `PostReportTargetAdapter`가 이 포트도 구현, `community/application/port/in/ListPostStatesUseCase.java`·`PostStateResult.java`, `community/application/service/ListPostStatesService.java`(`deleted_at`으로 `VISIBLE`/`DELETED`), `ListReportsService`에 `@Service`와 상태 붙이기(결과에 없는 대상은 `MISSING`), `test/.../report/ReportPagePersistenceTest.java`, `test/.../report/ReportListScenarioTest.java`(S11, S15를 `ListReportsUseCase`로 확인)
   - 필드 변경 커밋에서 기존 목록 테스트도 함께 고친다. `ListReportsServiceTest`, `ReportListItemResponseTest`, `AdminReportControllerTest`의 `Item`·`ListReportItemResult` 생성, 매핑, 정확한 키 검사를 9개 필드로 맞춘다. 컨트롤러 등록과 실제 조회·보안을 연결한 HTTP 통합 검증은 #92에 남긴다. (PR #276 리뷰)
   - 관리자 담당 파일을 옮기고 필드를 바꾸므로 PR에 이동 파일과 계약 변경을 적고 관리자 담당(@D-Gyun103)을 리뷰어로 지정한다.
   - 확인: `./gradlew test --tests 'com.pawmento.report.*'`

| 지금 위치(`community/`) | 옮길 위치(`report/`) |
| --- | --- |
| `adapter/in/web/report/AdminReportController.java`, `ListReportsRequest.java`, `ReportListItemResponse.java`, `ReportListQueryParameters.java`, `ReportListResponse.java`, `exception/InvalidReportListRequestException.java` | `adapter/in/web/` 아래 같은 이름 |
| `application/port/in/report/ListReportItemResult.java`, `ListReportsQuery.java`, `ListReportsUseCase.java`, `ReportStatus.java` | `application/port/in/` |
| `application/port/out/report/FindReportPagePort.java` | `application/port/out/` |
| `application/service/report/ListReportsService.java` | `application/service/` |
| 테스트 `adapter/in/web/report/AdminReportControllerTest.java`, `ListReportsRequestTest.java`, `ReportListItemResponseTest.java`, `application/service/report/ListReportsServiceTest.java` | 테스트의 `report/` 아래, 하위 경로에서 `report/` 한 단계를 뺀 곳 |

## 정한 것
- 신고할 때는 유형만 고르고, `OTHER`만 설명을 1~200자로 적는다. 다른 유형에 설명을 보내면 400이다. 신고 당시 글 원문을 스냅샷으로 저장하므로 관리자는 설명 없이도 판단할 수 있고, 신고자가 남의 글이나 개인정보를 옮겨 적는 일이 줄어든다. 처음 초안은 "모든 유형에서 설명 선택, 최대 500자"였고, 500자는 유형 없이 사유 문장 하나만 받던 프론트 데모의 상한이었다. (#112)
- 일반 사용자 화면과 API에서 신고 여부와 건수를 알리지 않는 원칙은 [report README](README.md#정한-것)에 있다. 중복 신고의 409 "이미 신고한 게시글이에요."는 요청한 회원 본인의 신고만 보고 판정하므로 이 원칙에 맞는다. (#112)
- 그 밖의 접수 규칙(중복·본인 글·대상 상태·유형 6개)과 운영 목록 필드는 [이슈 #112 본문](https://github.com/likelion-backendschool-9th/pawmento/issues/112)과 [관리자 목록 계약](../admin/report-management.md#목록에서-채택하는-계약)을 따른다. (#112)
- 스냅샷은 접수 트랜잭션에서 대상 한 건을 읽어 `snapshot_title`·`snapshot_body`·`snapshot_category_code`·`snapshot_target_version`과 대상 작성자 ID `target_author_id`를 함께 저장한다. 작성자 ID는 관리자 상세의 `authorId`와 작성자 본인 처리 금지에 쓰며, 대상 행을 찾을 수 없는(`MISSING`) 신고에서도 이 판정이 되도록 접수 때 남긴다. 내부 ID라 개인정보 복제 금지 원칙에 걸리지 않는다. (PR #276 리뷰) 캡처 시각은 신고의 `created_at`이다. 닉네임·이메일·IP는 복제하지 않는다. `snapshot_category_code`는 글에만 있고 다른 대상은 null이다. ([관리자 계획 Q4](../admin/report-management.md#q4--접수-스냅샷과-운영-상세))
- 글을 읽은 뒤 신고를 저장하기 전에 작성자가 글을 고치거나 지워도, 신고는 읽은 시점의 스냅샷으로 201 접수한다. 글 행을 잠그지 않는다. 신고자가 본 시점의 글은 공개 상태였고, 접수해 두어야 작성자가 바로 지운 글도 관리자가 신고 당시 내용으로 확인하고 관리할 수 있다. 지운 글의 신고가 목록에 `DELETED`로 남는 것과도 맞다. 이 순서는 #112-S13으로 확인한다. (#112)
- 커서 경계·`hasMore`·마지막 페이지 표현은 관리자 쪽이 정했고, `FindReportPagePort`는 `beforeReportId` 배타 경계와 `hasMore`만 제공한다. (#92)
- 운영 목록은 신고 테이블만으로 페이지를 정한 뒤 대상 상태를 붙인다. 대상 도메인 테이블과 SQL로 조인하지 않는다. 대상 조회 결과에 없는 신고도 목록에서 빼지 않고 `targetState: MISSING`으로 둔다. 글은 soft delete라 정상이라면 대상이 사라지지 않으므로, 이 값은 운영자가 데이터 이상을 알아볼 수 있게 `DELETED`와 구분한다. 페이지 크기와 커서는 대상 조회 결과와 상관없이 신고 id 기준이다. (#92 관리자 담당 답변, #112)
- 대상 조회는 같은 트랜잭션 안에서 community의 입력 포트를 부른다. 접수는 `GetPostForReportUseCase`가 지운 글을 빈 결과로 돌려줘 404가 되고, 목록은 `ListPostStatesUseCase`로 페이지의 글 id들을 한 번에 조회한다. family가 member의 `ListMemberProfilesUseCase`를 부르는 것과 같은 방식이다. (#112)

## 가정
- 응답은 201과 `{"reportId": n}`이다. 프론트 화면은 신고 id를 쓰지 않지만, 관리자 화면과 테스트에서 신고를 가리키기 쉽다.
- 재신고 판정은 그 회원의 같은 대상 신고 중 **가장 최근 것**만 본다. DISMISSED이고 snapshot targetVersion이 현재 대상 버전과 다를 때만 접수하고, 나머지(PENDING, 버전이 같은 DISMISSED, RESOLVED)는 409다. RESOLVED된 글은 숨기거나 지운 상태라 보통 404가 먼저 나온다.
- 글 버전은 지울 때도 오르지만, 지운 글은 404라 재신고 판정에 닿지 않는다.
- 스냅샷 네 값은 한 번 읽은 같은 대상에서 나오므로 서로 어긋나지 않는다.
- 숨김 컬럼이 아직 없으므로 글의 `targetState`는 `deleted_at`이 있으면 `DELETED`, 아니면 `VISIBLE`이다.
- `snapshotExpired`는 `snapshot_cleared_at IS NOT NULL`로 판정한다. 제목이 null인지로 추론하지 않는다. `snapshot_title`·`snapshot_body`·`reason_detail`은 정리 작업이 지울 수 있게 null을 허용한다. 정리 작업 전까지 `snapshot_cleared_at`은 늘 null이다.
- `reports`는 대상 테이블을 FK로 참조하지 않는다. 대상 존재는 접수할 때 대상 도메인이 확인하고, 글은 soft delete라 참조가 끊길 일이 적다. `reporter_id`는 탈퇴 하드 삭제를 막지 않도록 `member`를 FK로 참조하지 않는다([탈퇴 Q1](../member/withdrawal.md)과 같은 이유).
- 처리 후속 작업이 쓸 신고 `version`(`@Version`) 컬럼은 이번에 만든다. 처리자·처리 시각·메모 컬럼은 처리 작업에서 더한다.
- 유형·대상 종류 값은 대문자 enum 이름만 받는다(`spam`, `post`는 400). 설명의 공백 기준은 `Post`의 JavaScript `trim()` 기준과 같다.
- 비로그인 401은 `POST /api/v1/reports`가 공개 경로(`PublicEndpoints`)에 없으므로 기존 보안 설정으로 된다. 보안 설정은 바꾸지 않는다.

## 참고
- 스키마는 `ddl-auto: update`로 엔티티 테이블을 만들고, 부분 유니크 인덱스처럼 JPA로 못 만드는 것은 도메인별 SQL(`db/postcard-schema.sql`, `PostcardSchemaConfiguration`)을 시작할 때 실행한다. 신고도 같은 방식으로 `ux_reports_pending ON reports(target_type, target_id, reporter_id) WHERE status = 'PENDING'`, 목록용 `ix_reports_status_id (status, id DESC)`, 최근 신고 조회용 `ix_reports_target_reporter (target_type, target_id, reporter_id, id DESC)`를 둔다. SQL은 여러 번 실행해도 같게 `CREATE INDEX IF NOT EXISTS`로 쓴다. 3단계의 `ReportScenarioTest`에서 스키마를 한 번 더 적용해도 실패하지 않는지 확인한다.
- 유니크 위반을 409로 바꿀 때는 제약 이름이 `ux_reports_pending`인 경우만 바꾼다. 다른 무결성 오류는 원인을 보존한 채 500으로 둔다([공통 예외](../backend-exceptions.md)). 위반은 커밋 시점이 아니라 저장 직후 `flush`에서 잡는다. 글 저장 어댑터(`PostPersistenceAdapter`)도 같은 방식이다.
- 계획을 고칠 때 develop에는 관리자 담당의 목록 파일 16개(`FindReportPagePort`, `ListReportsService`, 등록하지 않은 `AdminReportController` 등)가 `community` 아래에 있고, 구현 어댑터와 신고 테이블은 없다. `posts`에는 숨김 컬럼이 없다. 접수 구현은 PR #267에 community 구조로 올라가 머지 보류 중이다.
- 프론트 데모(`frontend/mocks/handlers/lifeUseCases.ts`의 `reportPost`)는 사유 문자열 하나와 "이미 신고한 게시글이에요." 문구를 쓴다. 서버 계약의 근거는 아니다.
