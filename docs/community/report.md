# 게시글 신고 접수와 운영 목록 포트

관련 이슈: [#112](https://github.com/likelion-backendschool-9th/pawmento/issues/112). 상위 이슈 #92, 관리자 계획은 [report-management.md](../admin/report-management.md)다. 글 계약은 [post.md](post.md)(#21)를 따른다. 신고 데이터를 community가 맡는 경계는 [community README](README.md#정한-것)에 있다.

## 한눈에
- 끝나면: 로그인한 회원이 `POST /api/v1/posts/{postId}/reports`로 남의 글을 신고하고, 신고 당시 글 내용이 함께 저장된다. 관리자 목록이 연결할 `FindReportPagePort`의 실제 DB 구현이 생긴다.
- 결정 필요: 없음
- 규모: 4단계, 시나리오 12개

## 1. 신고 접수 (#112-R1)
로그인한 회원이 다른 회원의 공개 글을 신고 유형을 골라 신고하면(`OTHER`는 설명을 함께 적는다) PENDING 신고가 저장되고, 같은 시점의 글 제목·본문·카테고리·버전이 함께 저장된다.

#112-S1 정상 [테스트]
- Given 회원 B가 쓴 글 7(제목 `산책 후기`, 카테고리 `LIFE`, version 2)이 있다
- When  회원 A가 `POST /api/v1/posts/7/reports`에 `{"reasonType":"SPAM"}`을 보낸다
- Then  201과 `{"reportId": <새 id>}`를 받는다
- Then  `post_reports`에 status `PENDING`, reporter A, reasonType `SPAM`, reasonDetail `null`, snapshot 제목 `산책 후기`·본문·카테고리 `LIFE`·postVersion 2가 저장된다

#112-S2 경계: 접수 뒤 글 수정 [테스트]
- Given S1의 신고가 저장됐다
- When  B가 글 7을 제목 `수정한 글`로 고친다(version 3)
- Then  신고의 snapshot 제목은 `산책 후기`, postVersion은 2로 그대로다

## 2. 중복 신고 (#112-R2)
같은 회원이 같은 글을 처리 대기 중에 다시 신고하면 409다. 기각된 뒤에는 글이 그사이 수정됐을 때만 다시 접수한다. 다른 회원의 신고와는 상관없다.

#112-S3 실패: 처리 대기 중 재신고 [테스트]
- Given A의 글 7 신고가 PENDING이다
- When  A가 글 7을 `ABUSE`로 다시 신고한다
- Then  409 `CONFLICT` "이미 신고한 게시글이에요."를 받고 A의 신고 행은 1개다
- When  B가 글 7을 고쳐 version이 오른 뒤 A가 다시 신고한다
- Then  여전히 409다
- When  회원 C가 글 7을 신고한다
- Then  201이고 글 7의 PENDING 신고는 A·C의 2개다

#112-S4 경계: 기각 뒤 재신고 [테스트]
- Given A의 글 7 신고(snapshot postVersion 2)가 DISMISSED다
- When  글 7이 version 2 그대로일 때 A가 다시 신고한다
- Then  409 "이미 신고한 게시글이에요."다
- When  B가 글 7을 고쳐 version 3이 된 뒤 A가 다시 신고한다
- Then  201이고 새 신고의 snapshot postVersion은 3이다

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
없는 글과, 접수할 때 글을 읽는 시점에 이미 지운 글은 공개 상세와 같이 404다. 비로그인은 401이다.

#112-S7 실패 [테스트]
- Given 글 8은 지웠고 글 999는 없다
- When  A가 글 8, 글 999를 신고한다
- Then  둘 다 404 `NOT_FOUND`이고 신고 행은 생기지 않는다
- When  로그인하지 않고 글 7을 신고한다
- Then  401이다

## 5. 신고 사유 입력 (#112-R5)
유형은 필수이고 6개 중 하나를 고른다. 설명은 `OTHER`일 때만 받으며, 앞뒤 공백을 지운 뒤 1~200자다. 다른 유형에 설명을 보내면 거절한다.

#112-S8 정상: 기타 [테스트]
- Given 회원 B가 쓴 글 7이 있다
- When  A가 `{"reasonType":"OTHER","reasonDetail":"  사진 속 차량 번호가 보여요  "}`로 신고한다
- Then  201이고 reasonDetail `사진 속 차량 번호가 보여요`가 저장된다

#112-S9 실패: 잘못된 사유 [테스트]
- Given A는 글 7을 신고한 적이 없다. 성공하는 경우는 각각 신고가 없는 상태에서 확인한다
- When  유형이 없거나 `spam`·`UNKNOWN`이다
- Then  400 `VALIDATION`이다
- When  유형이 `OTHER`이고 설명이 없거나, 공백·줄바꿈·BOM(`﻿`)만 있다
- Then  400 `VALIDATION`이다
- When  유형이 `OTHER`이고 공백을 지운 설명이 201자다
- Then  400 `VALIDATION`이고, 1자와 200자는 201이다
- When  유형이 `SPAM`이고 설명 `광고예요`를 함께 보낸다
- Then  400 `VALIDATION`이다
- Then  400인 경우 모두 신고 행이 생기지 않는다

## 6. 운영 목록 포트 (#112-R6)
`FindReportPagePort`가 상태별로 신고 요약을 ID 내림차순 커서 페이지로 돌려주고, 다음 항목이 있는지 알려 준다.

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
- Then  각 항목의 `reportId`·`postId`·`status`·`reasonType`·`createdAt`·`snapshotTitle`이 저장값과 같고 `snapshotExpired`는 false다

#112-S11 경계: 지운 글의 신고 [테스트]
- Given 글 7(제목 `산책 후기`)의 PENDING 신고가 있다
- When  B가 글 7을 지운 뒤 `find(PENDING, 20, null)`을 부른다
- Then  그 신고가 목록에 남고 `snapshotTitle`은 `산책 후기`, `postState`는 `DELETED`다. 지우지 않은 글은 `VISIBLE`이다

#112-S12 경계: 스냅샷을 정리한 신고 [테스트]
- Given DISMISSED 신고 30의 `snapshot_cleared_at`이 채워져 있고 `snapshot_title`·`snapshot_body`·`reason_detail`이 null이다(테스트가 DB에 직접 넣는다)
- When  `find(DISMISSED, 20, null)`을 부른다
- Then  신고 30이 목록에 남고 `snapshotTitle`은 null, `snapshotExpired`는 true다

## 범위 밖
- 관리자 목록 HTTP 연결·컨트롤러 등록, `ListReportItemResult`·웹 응답에 `snapshotExpired` 더하기 (#92 관리자 담당)
- 관리자 상세·처리·기각·게시글 숨김(#92-R2·R3). 숨김 컬럼, 목록의 `HIDDEN`, 숨긴 글의 신고 404는 #92의 처리·숨김 단계에서 더하고, 그 단계의 완료 조건으로 검증한다
- 처리 1년 뒤 스냅샷 정리 작업, 자동 숨김, 프론트 신고 화면 연결
- 탈퇴 회원의 신고 정리(탈퇴 뒤에도 신고와 내부 ID는 보존 정책대로 남긴다)

## 작업 순서
확인 명령은 모두 `backend/`에서 실행하고, 그 단계에서 바꾼 테스트만 돌린다. 전체 테스트는 PR의 CI에서 확인한다.

1. 신고 도메인 — S8, S9의 도메인 규칙
   - 파일: `community/domain/report/Report.java`(유형 필수, `OTHER`만 설명 1~200자, 다른 유형의 설명 거절, `Post`와 같은 trim 기준), `domain/report/ReportReason.java`, `domain/report/exception/InvalidReportException.java`, `test/.../community/domain/report/ReportTest.java`
   - 확인: `./gradlew test --tests 'com.pawmento.community.domain.report.*'`
2. 신고 테이블과 접수 API, 동시 접수 — S1, S2, S3, S5, S6, S7, S8, S9
   - 파일: `adapter/out/persistence/report/PostReportJpaEntity.java`·`PostReportJpaRepository.java`·`ReportPersistenceAdapter.java`(저장 직후 `flush`, `ux_post_reports_pending` 위반만 `DuplicateReportException`으로 바꾸고 cause 보존), `resources/db/community-report-schema.sql`, `adapter/out/persistence/report/ReportSchemaConfiguration.java`, `application/port/in/report/ReportPostUseCase.java`·`ReportPostCommand.java`, `application/port/out/report/SaveReportPort.java`·`LoadLatestReportPort.java`, `application/service/report/ReportPostService.java`, `application/exception/DuplicateReportException.java`, `domain/report/exception/OwnPostReportException.java`, `adapter/in/web/report/ReportPostRequest.java`·`ReportPostResponse.java`, `PostController`에 `POST /{postId}/reports`, `test/.../community/ReportScenarioTest.java`, `test/.../community/ReportConcurrentCreateTest.java`
   - 기존 `PostScenarioTest`가 `DELETE FROM posts`로 데이터를 지운다. 새 FK 때문에 막히지 않도록 그 앞에서 `post_reports`를 먼저 지운다.
   - 확인: `./gradlew test --tests 'com.pawmento.community.ReportScenarioTest' --tests 'com.pawmento.community.ReportConcurrentCreateTest' --tests 'com.pawmento.community.PostScenarioTest'`
3. 기각 뒤 재신고 — S4
   - 파일: `ReportPostService`(가장 최근 신고 판정), `test/.../community/ReportScenarioTest.java`
   - 확인: `./gradlew test --tests 'com.pawmento.community.ReportScenarioTest'`
4. 운영 목록 포트 구현 — S10, S11, S12
   - 파일: `FindReportPagePort.Item`에 `snapshotExpired`, `ListReportsServiceTest`의 `Item` 생성에 `false`, 2단계의 `ReportPersistenceAdapter`가 `FindReportPagePort`도 구현하고 조회 쿼리는 `PostReportJpaRepository`에 둔다(같은 테이블이라 어댑터를 나누지 않는다, [백엔드 구조](../backend-architecture.md)). `size + 1`건 조회로 `hasMore` 판정, `posts.deleted_at`으로 `postState`, `snapshot_cleared_at`으로 `snapshotExpired`, `test/.../community/ReportPagePersistenceTest.java`
   - `ListReportsServiceTest`는 관리자 담당 파일이라 PR에서 관리자 담당(@D-Gyun103)을 리뷰어로 지정한다.
   - 확인: `./gradlew test --tests 'com.pawmento.community.ReportPagePersistenceTest' --tests 'com.pawmento.community.application.service.report.ListReportsServiceTest'`

## 정한 것
- 신고할 때는 유형만 고르고, `OTHER`만 설명을 1~200자로 적는다. 다른 유형에 설명을 보내면 400이다. 신고 당시 글 원문을 스냅샷으로 저장하므로 관리자는 설명 없이도 판단할 수 있고, 신고자가 남의 글이나 개인정보를 옮겨 적는 일이 줄어든다. 처음 초안은 "모든 유형에서 설명 선택, 최대 500자"였고, 500자는 유형 없이 사유 문장 하나만 받던 프론트 데모의 상한이었다. (#112)
- 일반 사용자 화면과 API에서는 신고 여부와 건수를 신고한 본인 말고는 알리지 않는다. 다른 회원, 비로그인, 작성자 모두에게 보여 주지 않는다. 관리자 목록·상세는 관리자 계획의 계약대로 조회한다. 기각되기 전의 신고가 작성자에 대한 인상을 만들지 않게 하기 위해서다. 작성자는 관리자가 숨김 처리했을 때만 알게 된다. 중복 신고의 409 "이미 신고한 게시글이에요."는 요청한 회원 본인의 신고만 보고 판정하므로 이 원칙에 맞는다. (#112)
- 그 밖의 접수 규칙(중복·본인 글·대상 상태·유형 6개)과 운영 목록 필드는 [이슈 #112 본문](https://github.com/likelion-backendschool-9th/pawmento/issues/112)과 [관리자 목록 계약](../admin/report-management.md#목록에서-채택하는-계약)을 따른다. (#112)
- 스냅샷은 접수 트랜잭션에서 글 한 행을 읽어 `snapshot_title`·`snapshot_body`·`snapshot_category_code`·`snapshot_post_version`을 함께 저장한다. 캡처 시각은 신고의 `created_at`이다. 닉네임·이메일·IP는 복제하지 않는다. ([관리자 계획 Q4](../admin/report-management.md#q4--접수-스냅샷과-운영-상세))
- `FindReportPagePort.Item`에 `snapshotExpired`를 더하고, 그래서 컴파일이 깨지는 관리자 담당의 `ListReportsServiceTest`는 #112에서 `Item` 생성에 `false`만 채운다. `ListReportItemResult`·`ListReportsService`·웹 응답으로 옮기는 일은 관리자 목록 연결 작업에서 한다. 관리자 계획이 이 값을 #112가 제공한다고 정했고, 컬럼과 포트가 같은 작업에 있어야 연결 전에 값의 출처를 검사할 수 있기 때문이다. #112는 출력 포트(`FindReportPagePort`)까지 제공하고, 입력 포트 결과(`ListReportItemResult`)로 넘기는 경계는 관리자 쪽이 맡는다. 이 경계와 테스트 수정은 계획 PR에서 관리자 담당(@D-Gyun103)을 리뷰어로 지정해 확인받는다. (#112)
- 글을 읽은 뒤 신고를 저장하기 전에 작성자가 글을 고치거나 지워도, 신고는 읽은 시점의 스냅샷으로 201 접수한다. 글 행을 잠그지 않는다. 신고자가 본 시점의 글은 공개 상태였고, 접수해 두어야 작성자가 바로 지운 글도 관리자가 신고 당시 내용으로 확인하고 관리할 수 있다. 지운 글의 신고가 목록에 `DELETED`로 남는 것과도 맞다. (#112)
- 커서 경계·`hasMore`·마지막 페이지 표현은 관리자 쪽이 정했고, 포트는 `beforeReportId` 배타 경계와 `hasMore`만 제공한다. (#92)

## 가정
- 응답은 201과 `{"reportId": n}`이다. 프론트 화면은 신고 id를 쓰지 않지만, 관리자 화면과 테스트에서 신고를 가리키기 쉽다.
- 재신고 판정은 그 회원의 같은 글 신고 중 **가장 최근 것**만 본다. DISMISSED이고 snapshot postVersion이 현재 글 버전과 다를 때만 접수하고, 나머지(PENDING, 버전이 같은 DISMISSED, RESOLVED)는 409다. RESOLVED된 글은 숨기거나 지운 상태라 보통 404가 먼저 나온다.
- 글 버전은 지울 때도 오르지만, 지운 글은 404라 재신고 판정에 닿지 않는다.
- 스냅샷 네 값은 한 번 읽은 같은 행에서 나오므로 서로 어긋나지 않는다.
- 숨김 컬럼이 아직 없으므로 `postState`는 `deleted_at`이 있으면 `DELETED`, 아니면 `VISIBLE`이다.
- `snapshotExpired`는 `snapshot_cleared_at IS NOT NULL`로 판정한다. 제목이 null인지로 추론하지 않는다. `snapshot_title`·`snapshot_body`·`reason_detail`은 정리 작업이 지울 수 있게 null을 허용한다. 정리 작업 전까지 `snapshot_cleared_at`은 늘 null이다.
- `post_reports.post_id`는 `posts(id)`를 FK(`ON DELETE RESTRICT`)로 참조한다. 글은 soft delete라 막히지 않는다. `reporter_id`는 탈퇴 하드 삭제를 막지 않도록 `member`를 FK로 참조하지 않는다([탈퇴 Q1](../member/withdrawal.md)과 같은 이유).
- 처리 후속 작업이 쓸 신고 `version`(`@Version`) 컬럼은 이번에 만든다. 처리자·처리 시각·메모 컬럼은 처리 작업에서 더한다.
- 유형 값은 대문자 enum 이름만 받는다(`spam`은 400). 설명의 공백 기준은 `Post`의 JavaScript `trim()` 기준과 같다.
- 비로그인 401은 `POST /posts/{id}/reports`가 공개 경로(`PublicEndpoints`의 GET 세 개)에 없으므로 기존 보안 설정으로 된다. 보안 설정은 바꾸지 않는다.

## 참고
- 스키마는 `ddl-auto: update`로 엔티티 테이블을 만들고, 부분 유니크 인덱스처럼 JPA로 못 만드는 것은 도메인별 SQL(`db/postcard-schema.sql`, `PostcardSchemaConfiguration`)을 시작할 때 실행한다. 신고도 같은 방식으로 `ux_post_reports_pending ON post_reports(post_id, reporter_id) WHERE status = 'PENDING'`와 목록용 `(status, id DESC)` 인덱스를 둔다. SQL은 여러 번 실행해도 같게 쓴다. 인덱스는 `CREATE INDEX IF NOT EXISTS`로, FK는 `postcard-schema.sql`처럼 한 `DO` 블록에서 테이블을 잠그고 `pg_constraint`에 없을 때만 추가한다. 2단계의 `ReportScenarioTest`에서 스키마를 한 번 더 적용해도 실패하지 않는지 확인한다.
- 유니크 위반을 409로 바꿀 때는 제약 이름이 `ux_post_reports_pending`인 경우만 바꾼다. 다른 무결성 오류는 원인을 보존한 채 500으로 둔다([공통 예외](../backend-exceptions.md)). 위반은 커밋 시점이 아니라 저장 직후 `flush`에서 잡는다. 글 저장 어댑터(`PostPersistenceAdapter`)도 같은 방식이다.
- 계획을 세울 때 develop에는 `FindReportPagePort`(인터페이스, `Item` 7개 필드), `ListReportsService`, 등록하지 않은 `AdminReportController`가 있고 구현 어댑터와 신고 테이블은 없다. `posts`에는 숨김 컬럼이 없다.
- 프론트 데모(`frontend/mocks/handlers/lifeUseCases.ts`의 `reportPost`)는 사유 문자열 하나와 "이미 신고한 게시글이에요." 문구를 쓴다. 서버 계약의 근거는 아니다.
