# 커뮤니티 글 작성·조회·수정·삭제

관련 이슈: [#21](https://github.com/likelion-backendschool-9th/pawmento/issues/21)

## 한눈에
- 끝나면: 로그인한 회원이 커뮤니티 글을 쓰고·고치고·지울 수 있고, 누구나 카테고리별 목록(더 불러오기)과 상세, 카테고리 목록을 API로 본다. 프론트엔드 연결은 다음 이슈에서 한다.
- 결정 필요: 없음
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
- Given 카테고리가 `"NOPE"`(없는 코드)이거나 사용하지 않는 카테고리다
- When  글을 쓴다
- Then  400과 `code: "VALIDATION"`을 받는다
- Then  `posts`에 행이 늘지 않는다

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

#21-S6 실패: 카테고리 없음 [테스트]
- When  `GET /api/v1/posts`(카테고리 없이)나 `GET /api/v1/posts?category=NOPE`를 부른다
- Then  둘 다 400과 `code: "VALIDATION"`을 받는다

## 3. 상세 (#21-R3)
누구나 글 하나의 상세를 본다.

#21-S7 로그인 없이 보기 [테스트]
- Given 글 1이 있고, 쿠키 없이 요청한다
- When  `GET /api/v1/posts?category=CARE`와 `GET /api/v1/posts/1`을 부른다
- Then  둘 다 200을 받는다
- Given 만료된 토큰이 든 쿠키를 가지고 요청한다
- When  같은 두 경로를 부른다
- Then  둘 다 401이 아니라 200을 받는다

## 4. 글 수정 (#21-R4)
작성자가 자기 글의 카테고리·제목·본문을 고친다.

#21-S8 정상 [테스트]
- Given 회원 A가 쓴 `CARE` 글 1이 있다
- When  A가 `PUT /api/v1/posts/1`에 `{"category":"MEMORIAL","title":"고친 제목","body":"고친 본문"}`을 보낸다
- Then  200을 받는다
- Then  상세가 고친 값을 돌려주고 `updatedAt`이 `createdAt`보다 늦다
- Then  글 1은 `MEMORIAL` 목록에 있고 `CARE` 목록에는 없다

## 5. 글 삭제 (#21-R5)
작성자가 자기 글을 지우면 목록과 상세에서 보이지 않는다.

#21-S9 정상 [테스트]
- Given 회원 A가 쓴 글 1이 있다
- When  A가 `DELETE /api/v1/posts/1`을 부른다
- Then  204를 받는다
- Then  `GET /api/v1/posts/1`은 404를 받고, 목록에 글 1이 없다
- Then  DB의 `posts` 행은 남아 있고 `deleted_at`이 채워져 있다

## 6. 권한 (#21-R6, #21-R7)
작성자가 아닌 회원은 고치거나 지울 수 없고(R6), 로그인하지 않은 방문자는 쓰거나 고치거나 지울 수 없다(R7).

#21-S10 다른 회원 [테스트]
- Given 회원 A가 쓴 글 1이 있고, 회원 B가 로그인했다
- When  B가 글 1을 `PUT`하거나 `DELETE`한다
- Then  둘 다 403과 `code: "FORBIDDEN"`을 받는다
- Then  글 1의 내용은 그대로이고 목록에 남아 있다
- 도메인 `Post.edit`·`Post.delete`도 작성자가 아니면 거부한다 (단위 테스트)

#21-S11 비로그인 [테스트]
- Given 쿠키 없이 요청한다
- When  `POST`, `PUT /1`, `DELETE /1`을 부른다
- Then  셋 다 401을 받는다

## 7. 카테고리 목록 (#21-R8)
누구나 사용 중인 카테고리 목록을 본다. 프론트엔드는 카테고리를 하드코딩하지 않고 이 목록으로 탭을 만든다.

#21-S12 정상 [테스트]
- Given 기본 카테고리만 있다 (서버가 시작할 때 넣은 5개)
- When  쿠키 없이 `GET /api/v1/posts/categories`를 부른다
- Then  200과 5개를 순서대로 받는다: `LIFE`(반려 생활), `CARE`(건강·돌봄), `FUNERAL`(장례 정보), `MEMORIAL`(추모 방식), `MEMORY`(기억 나누기)
- Then  각 항목은 `code`와 `name`을 가진다

## 범위 밖
- 카테고리 추가·이름 변경·숨김 API와 관리자 화면: 관리자 설계(#45)에 들어오면 한다. 그전에는 DB에서 직접 바꾼다
- 사진 첨부: 파일 업로드(#33)가 생긴 뒤 한다. multipart를 받으면 CSRF 처리를 먼저 정해야 한다([member README](../member/README.md#아직-정하지-않은-것))
- 댓글, 신고·숨김, 좋아요·북마크: 다음 이슈
- 작성자 닉네임 표시: 다른 도메인 기능을 부르는 방식이 정해진 뒤 더한다. 이번에는 `authorId`만 돌려준다
- 검색, 인기순 정렬
- 프론트엔드 HTTP 어댑터 연결
- 관리자가 남의 글을 지우는 기능

## 작업 순서
[백엔드 구조 문서](../backend-architecture.md)의 "안쪽부터" 순서를 따른다. 1~2단계는 DB와 웹 없이 단위 테스트로 확인한다. 롬복 의존성을 더하는 PR이 머지되면 `develop`을 받은 뒤 코드를 쓴다.

1. 도메인: `Post`, `PostCategory`, 도메인 예외 — S2(도메인), S10(도메인)
   - 파일: `community/domain/Post.java`, `PostCategory.java`, `NotPostAuthorException.java`, 테스트 `community/domain/PostTest.java`
   - 확인: `./gradlew test --tests 'com.pawmento.community.domain.*'`
2. 포트와 서비스: 유스케이스 6개(글 5개, 카테고리 목록 1개), Command·Query·Result, 출력 포트, 서비스 — S1·S3·S8·S9·S10의 서비스 흐름
   - 파일: `community/application/port/in/*`, `port/out/{Save,Load,Update}PostPort.java`, `port/out/LoadPostCategoryPort.java`(`PostPersistenceAdapter`가 함께 구현), `application/service/*`, `application/exception/{PostNotFoundException,PostCategoryNotFoundException}.java`, 테스트 `community/application/service/*Test.java`(가짜 포트)
   - 확인: `./gradlew test --tests 'com.pawmento.community.application.*'`
3. 영속성 어댑터: `PostJpaEntity`, `PostCategoryJpaEntity`, 리포지토리, `PostPersistenceAdapter`, 초기 카테고리 넣기(`PostCategorySeeder`) — S4·S5(커서 쿼리), S12(초기 데이터)
   - 파일: `community/adapter/out/persistence/*`
   - 확인: `./gradlew compileJava` (동작은 5단계의 시나리오 테스트로 확인)
4. 웹 어댑터와 공개 경로: `PostController`(카테고리 목록 포함), Request·Response, `CommunityExceptionHandler`, `PublicEndpoints`에 공개 읽기 경로 묶음 추가(`GET ApiPaths.V1 + "/posts"`, `GET ApiPaths.V1 + "/posts/*"`), 그 묶음에서도 토큰을 읽지 않게 `CookieBearerTokenResolver` 수정 — S6·S7·S11
   - 파일: `community/adapter/in/web/*`, `global/security/{PublicEndpoints,SecurityConfig,CookieBearerTokenResolver}.java`, `docs/member/README.md`(공개 경로 표)
   - 확인: `./gradlew compileJava`
5. 시나리오 테스트 — S1~S12 (API 전체)
   - 파일: `community/PostScenarioTest.java` (`AuthScenarioTest`처럼 가입·로그인으로 쿠키를 받는다)
   - 확인: `./gradlew test --tests 'com.pawmento.community.*'`

## 정한 것
- 카테고리는 테이블(`post_categories`)로 관리하고, 목록은 카테고리가 필수다. 이유는 [README의 정한 것](README.md#정한-것)에 있다.
- 유스케이스는 기능마다 하나씩 나눈다(`Create`, `Get`, `List`, `Edit`, `Delete`, 카테고리 `List`). 구조 문서의 규칙을 따른다.
- API 경로는 `/api/v1/posts`다. 구조 문서의 `/api/v1/<복수형>` 규칙을 따르고, 컨트롤러에는 `@RequestMapping("/posts")`만 쓴다. 댓글·신고는 `/posts/{id}/comments`처럼 글 아래에 붙인다. 카테고리 목록은 `GET /api/v1/posts/categories`로 같은 컨트롤러에 둔다. 구조 문서가 컨트롤러를 도메인에 하나씩 두기로 했기 때문이다. Spring은 고정 경로 `categories`를 `{id}`보다 먼저 고르고, 공개 경로도 `/posts/*` 하나로 덮인다.
- 롬복 의존성은 이 이슈에서 더하지 않고 따로 올라오는 PR로 받는다. 그 PR이 머지된 뒤 구조 문서의 [롬복 사용 범위](../backend-architecture.md#롬복-사용-범위)대로 쓴다.
- 첫 이슈에서는 작성자 닉네임 없이 `authorId`만 돌려준다.
- 삭제는 soft delete(`deleted_at`)로 한다. 이유는 [README의 정한 것](README.md#정한-것)에 있다.
- 도메인 예외는 `member`처럼 도메인 전용 핸들러(`CommunityExceptionHandler`)가 공통 에러 형식(`code`, `message`)으로 바꾼다.

## 가정
- 입력 규칙은 프론트엔드 데모(#12)에 맞춘다: 제목 1~100자, 본문 1~5000자, 카테고리 필수. 앞뒤 공백은 지우고 저장한다.
- API는 카테고리를 `code`(`CARE` 같은 영문 대문자)로 주고받는다. 프론트엔드 주소에도 쓰기 좋고, 테이블의 `id`가 환경마다 달라도 같다. `posts`는 `category_id`로 카테고리 행을 가리킨다.
- 초기 카테고리 5개는 서버가 시작할 때 `PostCategorySeeder`가 넣는다. 같은 `code`가 이미 있으면 건드리지 않아, DB에서 바꾼 이름·순서·숨김이 재시작 뒤에도 남는다. 마이그레이션 도구가 정해지면([member README](../member/README.md#아직-정하지-않은-것)의 스키마 관리) 그쪽으로 옮긴다.
- 카테고리 `code`는 한 번 정하면 바꾸지 않는다. 프론트엔드가 `FUNERAL`·`MEMORIAL`을 코드로 보고 안내 문구를 띄운다. 바꿀 수 있는 것은 이름·순서·사용 여부다.
- 사용하지 않는 카테고리(`active = false`)는 카테고리 목록에 나오지 않고, 그 카테고리로 글을 쓰거나 목록을 볼 수 없다(400). 이미 쓴 글의 상세는 그대로 볼 수 있다.
- 잘못된 카테고리 주소를 반려 생활로 돌리는 것은 프론트엔드가 한다. API는 400을 돌려준다.
- 목록 크기는 기본 20, 최대 50이다. 50을 넘으면 50으로 줄인다.
- 커서는 마지막으로 받은 글의 id다. id가 작을수록 오래된 글이라 `id < cursor`, `id` 내림차순으로 고른다.
- 목록은 본문을 통째로 주지 않고 앞 100자(`preview`)만 준다. 상세는 본문 전체를 준다.
- 없는 글, 지운 글을 보거나 고치거나 지우면 404다. 지운 글을 다시 지워도 404다.
- 수정은 `PUT`으로 카테고리·제목·본문을 모두 보낸다(부분 수정 없음). 응답은 상세와 같은 모양이다.
- `DELETE`는 본문이 없어도 된다. 다른 사이트의 폼은 `DELETE`를 보낼 수 없고, `fetch`로 보내면 CORS 사전 요청에서 막힌다(CORS를 열지 않았다).
- 공개 GET 경로에서는 토큰을 읽지 않는다. 지금 `CookieBearerTokenResolver`는 가입·로그인·로그아웃 경로에서만 토큰을 건너뛰어, 그대로 두면 만료된 쿠키가 남은 사람이 공개 목록을 볼 때 401이 난다. 공개 읽기 경로 묶음에서도 건너뛰게 고친다. 그래서 응답에 "내 글인지"를 넣지 않고, 프론트엔드가 `authorId`와 내 정보(`/api/v1/members/me`)의 `id`를 비교한다.
- 도메인 예외(`NotPostAuthorException`)는 `community/domain`에 둔다. 도메인은 `application`을 import할 수 없어서다. 웹 어댑터의 예외 핸들러가 이 예외 하나를 import하는 것은 구조 문서의 "웹 어댑터는 도메인 import 금지"의 예외로 두고, 구조 문서의 "도메인 예외 연결 방식"이 정해지면(#60) 맞춘다.
- 시나리오 테스트는 Testcontainers DB 하나를 다른 테스트와 같이 쓴다. 글 개수를 세는 시나리오가 흔들리지 않게 테스트마다 글(`posts`)을 지우고, 카테고리는 남긴다.
- 시각(`created_at`, `updated_at`)은 영속성 어댑터에서 채운다. 도메인은 시각을 만들지 않고, DB에서 불러올 때 받은 값을 가진다.

## 참고
- 테이블 이름은 구조 문서의 `@Table(name = "<복수형>")` 규칙대로 `posts`, `post_categories`로 한다. 지금 코드는 `member`, `records`, `record_location`으로 섞여 있고, 통일은 #29에서 한다.
- 공개 경로를 더하면 [`PublicEndpoints`](../../backend/src/main/java/com/pawmento/global/security/PublicEndpoints.java)와 [member README의 공개 경로 표](../member/README.md#공개-경로)를 함께 고친다.
- `GlobalExceptionHandler`는 `@Valid` 실패와 JSON 형식 오류만 처리한다. 필수 쿼리 파라미터가 빠진 경우(`MissingServletRequestParameterException`)와 숫자 자리에 글자가 온 경우(`cursor=abc`, `size=abc`, `/posts/abc`: `MethodArgumentTypeMismatchException`)는 처리하지 않아, 지금은 공통 에러 형식(`code: "VALIDATION"`)으로 나오지 않는다. 이번 이슈의 핸들러에서 처리한다.
- 프론트엔드 설계의 커뮤니티 규칙(전체 목록 없음, 기본 반려 생활, 추모 글 분리): [디자인 토큰 문서](../Frontend/PawMento_Design_Tokens_Hexagonal.md), [전체 데모](../app-demo/full-demo.md) #12-R7
- 프론트엔드 데모의 커뮤니티 모델: [`models.ts`](../../frontend/src/life/domain/models.ts)(카테고리 이름), [`CommunityScreen.tsx`](../../frontend/src/life/adapters/inbound/CommunityScreen.tsx)(제목·본문 길이, 카테고리별 안내 문구)
