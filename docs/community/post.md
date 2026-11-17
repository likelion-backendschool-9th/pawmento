# 커뮤니티 글 작성·조회·수정·삭제

관련 이슈: [#21](https://github.com/likelion-backendschool-9th/pawmento/issues/21)

## 한눈에
- 끝나면: 로그인한 회원이 커뮤니티 글을 쓰고·고치고·지울 수 있고, 누구나 카테고리별 목록(더 불러오기)과 상세, 카테고리 목록을 API로 본다. 프론트엔드 연결은 다음 이슈에서 한다.
- 결정 필요: 없음
- 선행 조건: #60의 기본 예외(`global.exception`)와 `GlobalExceptionHandler` 매핑 코드가 develop에 들어온 뒤 1단계를 시작한다. 늦어지면 #21이 먼저 더할지 #60 담당자와 정한다
- 규모: 5단계

## 1. 글 작성 (#21-R1)
로그인한 회원이 카테고리·제목·본문으로 글을 쓴다.

#21-S1 정상 [테스트]
- Given 회원 A가 로그인했다
- When  `POST /api/v1/posts`에 `{"category":"CARE","title":"노견 관절 영양제 후기","body":"3개월 먹였어요"}`를 보낸다
- Then  201과 `{"postId": 1}`을 받는다
- Then  `GET /api/v1/posts/1`이 `category: "CARE"`, `categoryName: "건강·돌봄"`, 같은 제목·본문, `authorId`(A의 id)를 돌려준다

#21-S2 실패: 제목 [테스트]
- Given 제목이 공백 `"   "`이거나 101자다
- When  글을 쓴다
- Then  400과 `code: "VALIDATION"`을 받는다
- Then  목록에 글이 늘지 않는다
- 도메인 `Post.create`도 빈 제목·본문을 거부한다 (단위 테스트)

#21-S3 실패: 카테고리 [테스트]
- Given 카테고리가 `"NOPE"`(없는 코드)다
- When  글을 쓴다
- Then  400과 `code: "VALIDATION"`을 받는다
- Given 카테고리가 사용하지 않는 카테고리(`active = false`)다
- When  글을 쓴다
- Then  409와 `code: "CONFLICT"`를 받는다
- Then  두 경우 모두 `posts`에 행이 늘지 않는다

## 2. 목록 (#21-R2)
누구나 카테고리 하나를 골라 최신순 목록을 보고 더 불러온다. 전체 목록은 없다.

#21-S4 정상 [테스트]
- Given `LIFE` 글이 25개 있다
- When  `GET /api/v1/posts?category=LIFE&size=20`을 부른다
- Then  가장 최근 글부터 20개와 `nextCursor`를 받는다
- When  `GET /api/v1/posts?category=LIFE&size=20&cursor=<nextCursor>`를 부른다
- Then  나머지 5개와 `nextCursor: null`을 받는다

#21-S5 카테고리끼리 섞이지 않음 [테스트]
- Given `CARE` 글 3개, `LIFE` 글 2개, `MEMORIAL` 글 1개가 있다
- When  `GET /api/v1/posts?category=CARE`를 부른다
- Then  `CARE` 글 3개만 받고, 추모 글은 없다

#21-S6 실패: 카테고리 [테스트]
- When  `GET /api/v1/posts?category=NOPE`를 부른다
- Then  400과 `code: "VALIDATION"`을 받는다
- When  `GET /api/v1/posts`(카테고리 없이)를 부른다
- Then  400을 받는다. 응답 본문 형식은 #60에서 정한다([예외 처리 문서](../backend-exceptions.md#아직-정하지-않은-것)의 Spring MVC 표준 예외)

#21-S7 실패: 목록 범위 [테스트]
- When  `size=0`, `size=51`, `cursor=0`으로 각각 목록을 부른다
- Then  셋 다 400과 `code: "VALIDATION"`을 받는다

## 3. 상세 (#21-R3)
누구나 글 하나의 상세를 본다.

#21-S8 로그인 없이 보기 [테스트]
- Given 글 1이 있고, 쿠키 없이 요청한다
- When  `GET /api/v1/posts?category=CARE`와 `GET /api/v1/posts/1`을 부른다
- Then  둘 다 200을 받는다
- Given 만료된 토큰이 든 쿠키를 가지고 요청한다
- When  같은 두 경로를 부른다
- Then  둘 다 401이 아니라 200을 받는다

## 4. 글 수정 (#21-R4)
작성자가 자기 글의 카테고리·제목·본문을 고친다.

#21-S9 정상 [테스트]
- Given 회원 A가 쓴 `CARE` 글 1이 있다
- When  A가 `PUT /api/v1/posts/1`에 `{"category":"MEMORIAL","title":"고친 제목","body":"고친 본문"}`을 보낸다
- Then  200을 받는다
- Then  상세가 고친 값을 돌려주고 `updatedAt`이 `createdAt`보다 늦다
- Then  글 1은 `MEMORIAL` 목록에 있고 `CARE` 목록에는 없다

## 5. 글 삭제 (#21-R5)
작성자가 자기 글을 지우면 목록과 상세에서 보이지 않는다.

#21-S10 정상 [테스트]
- Given 회원 A가 쓴 글 1이 있다
- When  A가 `DELETE /api/v1/posts/1`을 부른다
- Then  204를 받는다
- Then  `GET /api/v1/posts/1`은 404를 받고, 목록에 글 1이 없다
- Then  DB의 `posts` 행은 남아 있고 `deleted_at`이 채워져 있다

## 6. 권한 (#21-R6, #21-R7)
작성자가 아닌 회원은 고치거나 지울 수 없고(R6), 로그인하지 않은 방문자는 쓰거나 고치거나 지울 수 없다(R7).

#21-S11 다른 회원 [테스트]
- Given 회원 A가 쓴 글 1이 있고, 회원 B가 로그인했다
- When  B가 글 1을 `PUT`하거나 `DELETE`한다
- Then  둘 다 403과 `code: "FORBIDDEN"`을 받는다
- Then  글 1의 내용은 그대로이고 목록에 남아 있다
- 도메인 `Post.edit`·`Post.delete`도 작성자가 아니면 거부한다 (단위 테스트)

#21-S12 비로그인 [테스트]
- Given 쿠키 없이 요청한다
- When  `POST`, `PUT /1`, `DELETE /1`을 부른다
- Then  셋 다 401을 받는다

## 7. 카테고리 목록 (#21-R8)
누구나 사용 중인 카테고리 목록을 본다. 프론트엔드는 카테고리를 하드코딩하지 않고 이 목록으로 탭을 만든다.

#21-S13 정상 [테스트]
- Given 기본 카테고리만 있다 (서버가 시작할 때 넣은 5개)
- When  쿠키 없이 `GET /api/v1/posts/categories`를 부른다
- Then  200과 5개를 순서대로 받는다: `LIFE`(반려 생활), `CARE`(건강·돌봄), `FUNERAL`(장례 정보), `MEMORIAL`(추모 방식), `MEMORY`(기억 나누기)
- Then  각 항목은 `code`와 `name`을 가진다

## 8. 동시 수정·삭제 (#21-R9)
작성자의 요청 두 개가 겹치면 나중에 저장되는 요청은 덮어쓰지 않고 실패한다. 지운 글은 오래된 수정으로 되살아나지 않는다.

#21-S14 삭제와 겹친 수정 [테스트]
- Given 회원 A의 글 1을 수정 요청이 읽었다
- Given 그 수정이 저장되기 전에 다른 요청이 글 1을 지웠다
- When  수정이 저장된다
- Then  수정 요청은 409와 `code: "CONFLICT"`를 받는다
- Then  DB의 글 1은 `deleted_at`이 채워진 채이고 제목·본문은 지우기 전 값이다
- 두 수정이 겹치면 나중에 저장되는 쪽이 409를 받고, 먼저 저장된 값이 남는다 (같은 테스트 파일)

## 범위 밖
- 카테고리 추가·이름 변경·숨김 API와 관리자 화면: 이번에 만들지 않는다. community와 admin 사이의 데이터 책임·API·담당을 관리자 설계(#45)에서 합의한 뒤 한다. 그전에는 DB에서 직접 바꾼다
- 사진 첨부: 파일 업로드(#33)가 생긴 뒤 한다. multipart를 받으면 CSRF 처리를 먼저 정해야 한다([member README](../member/README.md#아직-정하지-않은-것))
- 댓글, 좋아요·북마크: 다음 이슈
- 신고·숨김: 관리자 운영 기능(#45)과 데이터 책임·API·담당을 합의한 뒤 한다
- 작성자 닉네임 표시: 다른 도메인 기능을 부르는 방식이 정해진 뒤 더한다. 이번에는 `authorId`만 돌려준다
- 검색, 인기순 정렬
- 프론트엔드 HTTP 어댑터 연결
- 관리자가 남의 글을 지우는 기능

## 작업 순서
[백엔드 구조 문서](../backend-architecture.md)의 "안쪽부터" 순서를 따른다. 1~2단계는 DB와 웹 없이 단위 테스트로 확인한다. 롬복 의존성을 더하는 PR이 머지되면 `develop`을 받은 뒤 코드를 쓴다.

1. 도메인: `Post`, `PostCategory`, 도메인 예외 — S2(도메인), S11(도메인)
   - 파일: `community/domain/Post.java`, `PostCategory.java`, `community/domain/exception/{InvalidPostException,NotPostAuthorException}.java`, 테스트 `community/domain/PostTest.java`
   - 확인: `./gradlew test --tests 'com.pawmento.community.domain.*'`
2. 포트와 서비스: 유스케이스 6개(글 5개, 카테고리 목록 1개), Command·Query·Result, 출력 포트, 서비스 — S1·S3·S7·S9·S10·S11의 서비스 흐름
   - 파일: `community/application/port/in/*`, `port/out/{Save,Load,Update}PostPort.java`, `port/out/LoadPostCategoryPort.java`(`PostPersistenceAdapter`가 함께 구현), `application/service/*`, `application/exception/{PostNotFoundException,InvalidPostCategoryException,InactivePostCategoryException,InvalidPostPageException,PostConcurrentModificationException}.java`, 테스트 `community/application/service/*Test.java`(가짜 포트)
   - 확인: `./gradlew test --tests 'com.pawmento.community.application.*'`
3. 영속성 어댑터: `PostJpaEntity`(`@Version`), `PostCategoryJpaEntity`, 리포지토리, `PostPersistenceAdapter`(저장 직후 `flush`해 버전 충돌을 `PostConcurrentModificationException`으로 바꿈), 초기 카테고리 넣기(`PostCategorySeeder`) — S4·S5(커서 쿼리), S13(초기 데이터), S14(버전 충돌)
   - 파일: `community/adapter/out/persistence/*`
   - 확인: `./gradlew compileJava` (동작은 5단계의 시나리오 테스트로 확인)
4. 웹 어댑터와 공개 경로: `PostController`(카테고리 목록 포함), Request·Response, `PublicEndpoints`에 공개 읽기 경로 묶음 추가(`GET ApiPaths.V1 + "/posts"`, `GET ApiPaths.V1 + "/posts/*"`), 그 묶음에서도 토큰을 읽지 않게 `CookieBearerTokenResolver` 수정 — S6·S8·S12
   - 파일: `community/adapter/in/web/*`, `global/security/{PublicEndpoints,SecurityConfig,CookieBearerTokenResolver}.java`, `docs/member/README.md`(공개 경로 표)
   - 확인: `./gradlew compileJava`
5. 시나리오 테스트 — S1~S14 (API 전체)
   - 파일: `community/PostScenarioTest.java` (`AuthScenarioTest`처럼 가입·로그인으로 쿠키를 받는다), `community/PostConcurrentUpdateTest.java` (S14, `PetConcurrentUpdateTest` 방식)
   - 확인: `./gradlew test --tests 'com.pawmento.community.*'`

## 정한 것
- 카테고리는 테이블(`post_categories`)로 관리하고, 목록은 카테고리가 필수다. 이유는 [README의 정한 것](README.md#정한-것)에 있다.
- 유스케이스는 기능마다 하나씩 나눈다(`Create`, `Get`, `List`, `Edit`, `Delete`, 카테고리 `List`). 구조 문서의 규칙을 따른다.
- API 경로는 `/api/v1/posts`다. 구조 문서의 `/api/v1/<복수형>` 규칙을 따르고, 컨트롤러에는 `@RequestMapping("/posts")`만 쓴다. 댓글·신고는 `/posts/{id}/comments`처럼 글 아래에 붙인다. 카테고리 목록은 `GET /api/v1/posts/categories`로 같은 컨트롤러에 둔다. 구조 문서가 컨트롤러를 도메인에 하나씩 두기로 했기 때문이다. Spring은 고정 경로 `categories`를 `{id}`보다 먼저 고르고, 공개 경로도 `/posts/*` 하나로 덮인다.
- 롬복 의존성은 이 이슈에서 더하지 않고 따로 올라오는 PR로 받는다. 그 PR이 머지된 뒤 구조 문서의 [롬복 사용 범위](../backend-architecture.md#롬복-사용-범위)대로 쓴다.
- 첫 이슈에서는 작성자 닉네임 없이 `authorId`만 돌려준다.
- 삭제는 soft delete(`deleted_at`)로 한다. 이유는 [README의 정한 것](README.md#정한-것)에 있다.
- 예외는 [예외 처리 문서](../backend-exceptions.md)(#60)를 따른다. 도메인·애플리케이션 예외가 `global.exception`의 기본 예외를 상속하고, `GlobalExceptionHandler`가 상태 코드와 공통 에러 형식(`code`, `message`)으로 바꾼다. 커뮤니티 전용 예외 핸들러는 두지 않는다.

  | 예외 | 위치 | 상속 | 응답 |
  | --- | --- | --- | --- |
  | `InvalidPostException` (빈 제목·본문, 길이 초과) | `community/domain/exception` | `RuleViolationException` | 400 `VALIDATION` |
  | `NotPostAuthorException` (`Post.edit`·`Post.delete`에서 작성자가 아님) | `community/domain/exception` | `PermissionDeniedException` | 403 `FORBIDDEN` |
  | `InvalidPostCategoryException` (없는 카테고리 코드) | `community/application/exception` | `RuleViolationException` | 400 `VALIDATION` |
  | `InvalidPostPageException` (`size`가 1~50 밖, `cursor`가 1 미만) | `community/application/exception` | `RuleViolationException` | 400 `VALIDATION` |
  | `InactivePostCategoryException` (사용하지 않는 카테고리) | `community/application/exception` | `StateConflictException` | 409 `CONFLICT` |
  | `PostConcurrentModificationException` (다른 요청이 먼저 고치거나 지움) | `community/application/exception` | `StateConflictException` | 409 `CONFLICT` |
  | `PostNotFoundException` (없는 글, 지운 글) | `community/application/exception` | `ResourceNotFoundException` | 404 `NOT_FOUND` |

  카테고리와 글은 저장소를 봐야 알 수 있어 애플리케이션 예외로 둔다. 400과 409는 예외 처리 문서의 기준(같은 입력을 언제 보내도 실패하는가)으로 나눴다. 없는 코드는 언제 보내도 실패해 400, 숨긴 카테고리는 다시 켜면 성공해 409다.

## 가정
- 입력 규칙은 프론트엔드 데모(#12)에 맞춘다: 제목 1~100자, 본문 1~5000자, 카테고리 필수. 앞뒤 공백은 지우고 저장한다.
- API는 카테고리를 `code`(`CARE` 같은 영문 대문자)로 주고받는다. 프론트엔드 주소에도 쓰기 좋고, 테이블의 `id`가 환경마다 달라도 같다. `posts`는 `category_id`로 카테고리 행을 가리킨다.
- 초기 카테고리 5개는 서버가 시작할 때 `PostCategorySeeder`가 넣는다. 같은 `code`가 이미 있으면 건드리지 않아, DB에서 바꾼 이름·순서·숨김이 재시작 뒤에도 남는다. 마이그레이션 도구가 정해지면([member README](../member/README.md#아직-정하지-않은-것)의 스키마 관리) 그쪽으로 옮긴다.
- 카테고리 `code`는 한 번 정하면 바꾸지 않는다. 프론트엔드가 `FUNERAL`·`MEMORIAL`을 코드로 보고 안내 문구를 띄운다. 바꿀 수 있는 것은 이름·순서·사용 여부다.
- 사용하지 않는 카테고리(`active = false`)는 카테고리 목록에 나오지 않고, 그 카테고리로 글을 쓰거나 목록을 볼 수 없다(409). 이미 쓴 글의 상세는 그대로 볼 수 있다.
- 잘못된 카테고리 주소를 반려 생활로 돌리는 것은 프론트엔드가 한다. API는 400을 돌려준다.
- 목록 크기 `size`는 1~50이고, 보내지 않으면 20이다. `cursor`는 1 이상이다. 범위를 벗어나면 보정하지 않고 400이다. 프론트엔드는 정해진 값만 보내므로, 이상한 값은 잘못된 호출로 보고 드러나게 한다. 범위 검사는 서비스가 해서 공통 에러 형식으로 나간다. 숫자가 아닌 값(`size=abc`)은 Spring 표준 예외라 400이지만 본문 형식은 #60에서 정한다.
- 커서는 마지막으로 받은 글의 id다. id가 작을수록 오래된 글이라 `id < cursor`, `id` 내림차순으로 고른다.
- 목록은 본문을 통째로 주지 않고 앞 100자(`preview`)만 준다. 상세는 본문 전체를 준다.
- 없는 글, 지운 글을 보거나 고치거나 지우면 404다. 지운 글을 다시 지워도 404다.
- 수정은 `PUT`으로 카테고리·제목·본문을 모두 보낸다(부분 수정 없음). 응답은 상세와 같은 모양이다.
- `DELETE`는 본문이 없어도 된다. 다른 사이트의 폼은 `DELETE`를 보낼 수 없고, `fetch`로 보내면 CORS 사전 요청에서 막힌다(CORS를 열지 않았다).
- 공개 GET 경로에서는 토큰을 읽지 않는다. 지금 `CookieBearerTokenResolver`는 가입·로그인·로그아웃 경로에서만 토큰을 건너뛰어, 그대로 두면 만료된 쿠키가 남은 사람이 공개 목록을 볼 때 401이 난다. 공개 읽기 경로 묶음에서도 건너뛰게 고친다. `global/security`와 member 공개 경로 표를 바꾸는 일이라 인증 담당(#15)과 적용 범위를 맞춘 뒤 고친다. 그래서 응답에 "내 글인지"를 넣지 않고, 프론트엔드가 `authorId`와 내 정보(`/api/v1/members/me`)의 `id`를 비교한다.
- 동시 수정은 반려동물(#48)과 같이 `posts.version`(`@Version`)으로 막는다. 삭제(soft delete)도 버전을 올려, 지우기 전에 읽은 수정은 409가 된다. 영속성 어댑터가 저장 직후 `flush`해 충돌을 그 자리에서 잡는다. 커밋 시점 충돌의 처리 위치는 예외 처리 문서에서 미정이라 이 방식으로 피한다.
- 시나리오 테스트는 Testcontainers DB 하나를 다른 테스트와 같이 쓴다. 글 개수를 세는 시나리오가 흔들리지 않게 테스트마다 글(`posts`)을 지우고, 카테고리는 남긴다.
- 시각(`created_at`, `updated_at`)은 영속성 어댑터에서 채운다. 도메인은 시각을 만들지 않고, DB에서 불러올 때 받은 값을 가진다.

## 참고
- 테이블 이름은 구조 문서의 `@Table(name = "<복수형>")` 규칙대로 `posts`, `post_categories`로 한다. 지금 코드는 `member`, `records`, `record_location`으로 섞여 있고, 통일은 #29에서 한다.
- 공개 경로를 더하면 [`PublicEndpoints`](../../backend/src/main/java/com/pawmento/global/security/PublicEndpoints.java)와 [member README의 공개 경로 표](../member/README.md#공개-경로)를 함께 고친다.
- `GlobalExceptionHandler`는 기본 예외와 `@Valid` 실패, JSON 형식 오류만 처리한다. 필수 쿼리 파라미터 누락(`MissingServletRequestParameterException`)과 타입 오류(`cursor=abc`, `/posts/abc`: `MethodArgumentTypeMismatchException`)는 Spring 기본 형식의 400이다. 이것을 공통 형식으로 맞출지는 [예외 처리 문서](../backend-exceptions.md#아직-정하지-않은-것)의 미정 항목이라, 이번 이슈에서는 `GlobalExceptionHandler`를 고치지 않는다.
- 프론트엔드 설계의 커뮤니티 규칙(전체 목록 없음, 기본 반려 생활, 추모 글 분리): [디자인 토큰 문서](../Frontend/PawMento_Design_Tokens_Hexagonal.md), [전체 데모](../app-demo/full-demo.md) #12-R7
- 프론트엔드 데모의 커뮤니티 모델: [`models.ts`](../../frontend/src/life/domain/models.ts)(카테고리 이름), [`CommunityScreen.tsx`](../../frontend/src/life/adapters/inbound/CommunityScreen.tsx)(제목·본문 길이, 카테고리별 안내 문구)
