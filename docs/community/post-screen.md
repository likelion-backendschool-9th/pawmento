# 커뮤니티 화면을 글 API에 연결

관련 이슈: [#104](https://github.com/likelion-backendschool-9th/pawmento/issues/104)

## 한눈에
- 끝나면: 가입·로그인한 회원이 커뮤니티에서 쓴 글이 서버에 저장되어 다른 회원의 목록·상세에도 보이고, 작성자는 자기 글을 고치거나 지운다. 데모로 시작한 계정은 지금처럼 브라우저 데모를 쓴다.
- 결정 필요: 없음
- 선행 조건: 없음. 글 API(#21)와 프론트 구조 개편(#182)이 `develop`에 있다
- 규모: 6단계

## 1. 카테고리별 목록 (#104-R1)
회원이 서버의 카테고리 탭에서 하나를 골라 최신순 목록을 보고 더 불러온다.

#104-S1 목록 요청과 응답 변환 [테스트]
- Given 서버가 `GET /api/v1/posts?category=LIFE&size=20`에 글 20개와 `nextCursor: 31`을 돌려준다
- When  `postApi.list({ category: "LIFE" })`를 부른다
- Then  목록 항목(`PostSummary`) 20개를 받는다
- Then  각 항목의 `id`는 서버의 `postId`를, `authorId`는 서버의 `authorId`를 문자열로 바꾼 값이다
- Then  각 항목은 `category`·`categoryName`·`title`·`preview`·`createdAt`을 서버 값 그대로 가진다
- Then  `nextCursor`는 `"31"`이다
- When  `postApi.list({ category: "LIFE", cursor: "31" })`를 부른다
- Then  요청 주소가 `/posts?category=LIFE&size=20&cursor=31`이다
- 서버가 `nextCursor: null`을 주면 `nextCursor`는 `null`이다

#104-S2 더 불러오기 [직접]
- Given 회원 A가 로그인했고, `LIFE` 글이 25개 있다
- When  `/community?category=LIFE`를 연다
- Then  가장 최근 글부터 20개와 "더 보기" 버튼이 보인다
- When  "더 보기"를 누른다
- Then  25개가 보이고, 같은 글이 두 번 나오지 않고, "더 보기" 버튼이 사라진다

#104-S3 카테고리 탭 [직접]
- Given 서버 카테고리가 기본 5개다
- When  `/community`를 연다
- Then  탭이 `GET /api/v1/posts/categories` 순서대로 보이고, 반려 생활 탭이 선택되어 있다
- When  건강·돌봄 탭을 누른다
- Then  주소가 `?category=CARE`가 되고, `CARE` 글만 보인다
- When  `/community?category=NOPE`를 연다
- Then  반려 생활 탭과 `LIFE` 글이 보인다

#104-S12 주소의 카테고리 고르기 [테스트]
- Given 서버 카테고리가 `LIFE`, `CARE`, `FUNERAL`, `MEMORIAL`, `MEMORY` 순서다
- When  주소의 카테고리 `CARE`로 고른다
- Then  `CARE`를 받는다
- When  주소의 카테고리 `NOPE`나 빈 값으로 고른다
- Then  첫 카테고리 `LIFE`를 받는다

#104-S13 더 보기 이어 붙이기 [테스트]
- Given 화면에 글 25~6(20개)이 있다
- When  다음 페이지로 글 6~1(6개)을 받아 이어 붙인다
- Then  글 25~1(25개)이 최신순이고, 글 6은 한 번만 있다

## 2. 상세 (#104-R2)
회원이 글을 눌러 본문 전체를 보고, 작성자에게만 수정·삭제가 보인다.

#104-S4 상세와 작성자 표시 [직접]
- Given 회원 A가 쓴 `CARE` 글 1이 있다
- When  A가 목록에서 글 1을 누른다
- Then  `/community/1`에 제목과 본문 전체가 보이고, 수정·삭제 버튼이 보인다
- When  회원 B가 `/community/1`을 연다
- Then  같은 제목·본문이 보이고, 수정·삭제 버튼은 보이지 않는다
- When  `/community/9999`(없거나 지운 글)를 연다
- Then  "게시글을 찾을 수 없어요"와 목록으로 가는 링크가 보인다

## 3. 글 작성 (#104-R3)
회원이 쓴 글이 서버에 저장되고 다른 회원의 목록에도 보인다.

#104-S5 작성·수정·삭제 요청 [테스트]
- When  `postApi.create({ category: "CARE", title: "노견 관절", body: "3개월 먹였어요" })`를 부른다
- Then  `POST /posts`에 같은 세 값을 보내고, 서버의 `{"postId": 7}`을 `"7"`로 돌려준다
- When  `postApi.get("7")`을 부른다
- Then  `GET /posts/7`의 응답을 상세(`PostDetail`)로 받는다. `id`·`authorId`는 문자열이고 `body`·`updatedAt`이 있다
- When  `postApi.update("7", { category: "MEMORIAL", title: "고친 제목", body: "고친 본문" })`를 부른다
- Then  `PUT /posts/7`에 세 값을 보낸다
- Then  서버가 200과 함께 준 수정된 글을 상세(`PostDetail`)로 돌려준다
- When  `postApi.remove("7")`를 부르고 서버가 204를 준다
- Then  `DELETE /posts/7`을 보내고 오류 없이 끝난다
- Given 서버가 409와 `{"code":"CONFLICT","message":"다른 요청에서 글을 먼저 고치거나 지웠습니다. 다시 불러와 주세요."}`를 준다
- Then  `ApiError`의 `status`는 409, `code`는 `CONFLICT`, `message`는 서버 문구다

#104-S6 다른 회원에게 보임 [직접]
- Given 회원 A와 회원 B가 각자 다른 브라우저에서 로그인했다
- When  A가 `/community/new?category=CARE`에서 제목 "노견 관절"과 본문을 저장한다
- Then  A는 새 글의 상세(`/community/<새 id>`)로 이동하고 저장 알림이 보인다
- When  B가 `/community?category=CARE`를 새로 연다
- Then  B의 목록 맨 위에 "노견 관절"이 보인다

## 4. 글 수정·삭제 (#104-R4)
작성자가 자기 글을 고치거나 지운다.

#104-S7 수정과 삭제 [직접]
- Given 회원 A가 쓴 `CARE` 글 1이 있다
- When  A가 `/community/1/edit`에서 카테고리를 추모 방식으로, 제목을 "고친 제목"으로 바꿔 저장한다
- Then  `/community/1`에 `PUT` 응답의 고친 값이 보인다
- Then  추모 방식 목록에 글 1이 있고 건강·돌봄 목록에는 없다
- When  A가 상세에서 삭제를 누르고 확인한다
- Then  추모 방식 목록으로 이동하고, 목록에 글 1이 없다
- When  회원 B가 `/community/1/edit`을 주소로 연다
- Then  "작성자만 수정할 수 있어요"가 보이고 수정 폼은 보이지 않는다

## 5. 실패해도 입력 유지 (#104-R5)
저장에 실패하면 서버의 오류 문구를 보여 주고, 입력한 내용을 지우지 않는다.

#104-S8 저장 실패 [직접]
- Given 회원 A가 글쓰기 화면에서 제목과 본문을 입력했다
- Given 백엔드를 멈췄다 (또는 개발자 도구로 `POST /api/v1/posts`가 500을 받게 했다)
- When  저장한다
- Then  폼 안에 오류 문구가 보이고, 제목·본문·카테고리가 입력한 그대로다
- Then  상세로 이동하지 않고, 저장 버튼을 다시 누를 수 있다
- 409(두 수정 요청이 서버에서 겹침)는 손으로 재현하기 어렵다. 서버의 409는 #21-S14(`PostConcurrentUpdateTest`)가, 409 문구가 `ApiError`로 오는 것은 #104-S5가 확인한다. 화면은 500과 같은 경로로 서버 문구를 보이고 입력을 남긴다 (고침 #104: #257 리뷰. `PUT`에 편집을 시작할 때의 버전이 없어, 두 탭에서 차례로 저장하면 409가 아니라 나중 저장이 덮어쓴다)

## 6. 세션에 따른 데이터 출처 (#104-R6)
가입·로그인한 회원(HTTP 세션)은 서버 글을, 데모로 시작한 계정은 브라우저 데모 글을 본다. 둘을 한 목록에 섞지 않는다.

#104-S9 출처 고르기 [테스트]
- Given 데모 계정(`demo: true`)으로 들어왔다
- When  커뮤니티 출처에서 목록을 부른다
- Then  브라우저 데모 저장소의 글을 받고, 글 API는 불리지 않는다
- Given 가입·로그인한 계정(`demo` 없음)으로 들어왔다
- When  같은 목록을 부른다
- Then  글 API의 글을 받고, 데모 저장소는 불리지 않는다

#104-S10 서버에 없는 기능 숨기기 [직접]
- Given 가입·로그인한 회원 A가 커뮤니티 목록과 상세를 연다
- Then  검색창, 정렬(최신순·인기순), 좋아요·북마크, 신고하기, 댓글, 사진 선택이 보이지 않는다
- Then  "이 브라우저에서만 공유되는 데모 데이터" 안내와 "내가 쓴 글 · 댓글 · 북마크" 링크가 보이지 않는다
- Then  장례 정보·추모 방식 탭의 전문가 확인 안내는 그대로 보인다
- When  A가 `/me/activity`를 주소로 연다
- Then  준비 중 안내와 커뮤니티로 가는 링크가 보인다
- Given 데모로 시작한 계정으로 같은 화면을 연다
- Then  검색·정렬·좋아요·댓글·신고·사진·내 활동이 지금처럼 보이고 동작한다

## 7. 로그인 안내 (#104-R7)
로그인하지 않은 방문자가 글쓰기로 들어오면 로그인으로 안내하고, 로그인 뒤 원래 화면으로 돌아온다. 지금 `AuthenticatedLayout`이 `returnTo`를 넘겨 이미 이렇게 동작하므로 코드를 바꾸지 않고, 화면을 나누고 고친 뒤에도 유지되는지만 확인한다.

#104-S11 비로그인 글쓰기 (기존 동작 확인) [직접]
- Given 로그인하지 않았다
- When  `/community/new?category=CARE`를 연다
- Then  로그인 화면이 보인다
- When  회원 A로 로그인한다
- Then  `/community/new?category=CARE` 글쓰기 화면이 보인다

## 범위 밖
- 로그인 없이 목록·상세 보기: 백엔드 GET은 공개지만, 프론트의 `app/(app)` 레이아웃 전체가 로그인을 요구한다. 공개 화면의 셸(메뉴·반려동물 선택이 없는 화면)을 정해야 해서 이번에 하지 않는다. 이슈 시나리오 2·3의 "누구나"를 이번에는 로그인한 회원으로 좁힌다
- 댓글, 좋아요·북마크, 검색, 인기순 정렬: API가 생기는 이슈에서 화면을 다시 붙인다
- 신고 접수: #112. 관리자 신고 화면(`AdminScreen`)은 계속 브라우저 데모 글을 본다
- 사진 첨부: 파일 업로드(#33)와 글 API의 사진 필드가 생긴 뒤 한다
- 내 활동(`/me/activity`)의 내가 쓴 글·댓글·북마크: 내 글 목록 API가 없다
- 작성자 닉네임 표시: 응답에 `authorId`만 있다 ([community README](README.md#아직-정하지-않은-것))
- 데모 세션의 커뮤니티 정리: 데모 진입점 결정(#168) 뒤 한다

## 작업 순서
1. 글 API 함수와 HTTP 메서드 — S1, S5
   - 파일: `frontend/lib/http/apiClient.ts`(`put`, `delete` 추가), `frontend/features/community/api/postApi.ts`, `frontend/features/community/types/post.ts`(`PostSummary`, `PostDetail`, `PostCategory`, `PostPage`), 테스트 `frontend/tests/community-post-api.test.ts`
   - 확인: `cd frontend && npm test`
2. 세션별 출처와 의존성 조립 — S9
   - 파일: `frontend/features/community/source.ts`, `frontend/app/composition.ts`, 같은 테스트 파일
   - 확인: `cd frontend && npm test`
3. 화면 나누기 (동작 변경 없음) — 시나리오 없음. 데모 화면이 그대로인지 확인
   - 파일: `CommunityScreen.tsx`를 목록(`PostList.tsx`)·상세(`PostDetail.tsx`)·편집기(`PostEditor.tsx`)·내 활동(`ActivityView.tsx`)으로 나누고, `CommunityScreen.tsx`는 경로와 `activity` 속성에 따라 네 화면 중 하나를 고르기만 한다. `me/activity/page.tsx`도 같은 컴포넌트를 쓰기 때문이다 (#229 리뷰)
   - 방법: 함수 본문은 고치지 않고 옮기기만 한다. 한 파일을 여러 파일로 나누면 `git diff -M`이 이동으로 잡지 못하므로, `git diff --color-moved=zebra`로 옮긴 줄만 보이는지 확인한다
   - 확인: `cd frontend && npm run lint && npm run build`, 데모 계정으로 아래가 전과 같은지. 같은 목록으로 구현 PR 본문에 결과를 적는다
     - 목록: 카테고리 탭 5개와 주소의 `category`, 장례 정보·추모 방식의 전문가 확인 안내, 검색, 최신순·인기순 정렬, 글 카드의 사진·좋아요 수·댓글 수, 빈 목록 안내, 데모 데이터 안내, "내가 쓴 글 · 댓글 · 북마크" 링크
     - 상세: 본문과 사진, 작성자에게만 보이는 수정·삭제, 좋아요·북마크 토글, 신고 대화상자, 댓글 작성·수정·삭제, 없는 글 안내
     - 편집기: 새 글의 기본 카테고리(주소의 `category`), 수정 폼의 기존 값, 사진 선택, 남의 글 수정 안내, 저장 뒤 상세로 이동
     - 내 활동(`/me/activity`): 내가 쓴 글·내 댓글·북마크 탭, "커뮤니티로" 링크
4. 목록 화면: 서버 카테고리 탭, 최신순 목록, 더 보기 — S2, S3, S12, S13
   - 파일: `frontend/features/community/components/PostList.tsx`, `frontend/features/community/hooks/usePosts.ts`, `frontend/features/community/utils/postList.ts`(카테고리 고르기·이어 붙이기), `frontend/features/community/utils/categories.ts`·`types/community.ts`(데모 전용임을 주석으로 표시, 값은 그대로), 테스트 `frontend/tests/community-post-api.test.ts`
   - 확인: `cd frontend && npm test && npm run lint && npm run build`, 로컬 백엔드로 S2·S3
5. 상세·작성·수정·삭제와 실패 처리 — S4, S6, S7, S8
   - 파일: `PostDetail.tsx`, `PostEditor.tsx`, `usePosts.ts`
   - 확인: `cd frontend && npm run lint && npm run build`, 로컬 백엔드와 두 브라우저로 S4·S6·S7·S8, 마지막에 S11(기존 동작 확인)
6. 서버 세션에서 없는 기능 숨기기와 내 활동 안내 — S10
   - 파일: `PostList.tsx`, `PostDetail.tsx`, `PostEditor.tsx`, `ActivityView.tsx`
   - 확인: 같은 명령, HTTP 계정과 데모 계정으로 S10

## 정한 것
- 이번에는 로그인한 회원만 커뮤니티를 본다. 공개 열람은 레이아웃 셸을 따로 정해야 해서 범위 밖으로 둔다.
- 서버 API가 없는 기능은 HTTP 세션에서 화면에 그리지 않는다. "준비 중"으로 남겨 두지 않는다. [구조 문서](../frontend-structure/README.md#7-단계별-전환-순서와-선행-조건)의 "API가 없는 동작은 실제 모드에서 성공시켜 두지 않는다"를 따르고, 쓸 수 없는 버튼을 남기지 않기 위해서다.
- 데모로 시작한 계정은 브라우저 데모(`BrowserLifeRepository`)를 그대로 쓴다. 데모 계정에는 서버 쿠키가 없어 글을 쓸 수 없기 때문이다. 지도(`features/map/source.ts`)처럼 출처를 한 파일에서 고르고, #168 결정 뒤 정리한다.
- 서버 글은 새 타입(`types/post.ts`)에 담는다. 목록 항목 `PostSummary`는 `preview`를, 상세 `PostDetail`은 `body`·`updatedAt`을 가진다. 서버 `postId`는 `id`, 숫자 id는 문자열로 바꾼다. 서버에 없는 `photoUrl`·`likes`·`bookmarks`·`hidden`을 기본값으로 채우지 않는다. 채우면 "좋아요 0개"처럼 사실이 아닌 값이 화면에 나오기 때문이다. 데모 화면은 기존 `Post` 타입(`types/community.ts`)을 그대로 쓴다. (#229 리뷰)
- 카테고리는 HTTP 세션에서 서버의 `code`·`name`을 쓰고, 데모 세션은 기존 `utils/categories.ts`와 `Category` 타입을 쓴다. 둘은 #168 결정 뒤 데모를 정리할 때 함께 지운다. (#229 리뷰)
- `CommunityScreen.tsx`(576줄)를 4~6단계가 모두 고치므로, 먼저 동작을 바꾸지 않고 목록·상세·편집기·내 활동으로 나눈다. (#229 리뷰)
- 저장 중에 401(쿠키 만료)을 받으면 로그인 화면으로 가고, 입력한 제목·본문은 잃는다. 로그인 전에 입력을 보관하려면 브라우저 저장소가 필요한데, [구조 문서](../frontend-structure/README.md#3-데이터-저장과-화면-상태)가 업무 데이터의 브라우저 저장을 막고 있다. (#229 리뷰)

## 가정
- 출처는 계정의 `demo` 표시로 고른다. 글 id 형식이나 서버 오류로 데모에 넘어가지 않는다. 서버 요청이 실패하면 데모 글을 대신 보여 주지 않고 실패를 보여 준다.
- 작성자 확인은 화면 표시용이다. 글의 `authorId`와 현재 계정(`/api/v1/members/me`)의 `id`를 문자열로 비교해 수정·삭제 버튼을 보인다. 실제 권한은 서버의 403이 정한다.
- 작성자 이름 자리에는 닉네임 대신 내 글이면 "나", 남의 글이면 "보호자"를 쓴다.
- 목록은 서버의 `preview`(앞 100자)를 보여 주고, 상세와 수정 폼은 `GET /posts/{id}`의 본문 전체를 쓴다.
- 한 번에 20개씩 받는다. "더 보기"는 `nextCursor`가 있을 때만 보인다. 카테고리를 바꾸면 처음부터 다시 받는다.
- 카테고리 목록을 받지 못하면 목록 대신 오류와 "다시 시도"를 보여 준다. 하드코딩한 카테고리로 대신하지 않는다. 장례 정보·추모 방식 안내 문구는 카테고리 `code`(`FUNERAL`, `MEMORIAL`)로 띄운다.
- 주소의 카테고리가 서버 카테고리에 없으면 첫 탭(`LIFE`)으로 본다. 글쓰기 화면의 기본 카테고리도 같다.
- 입력 길이는 지금 폼처럼 제목 100자, 본문 5000자로 막고, 최종 판단은 서버의 400이다.
- 저장 중에는 저장 버튼을 막아 같은 글이 두 번 올라가지 않게 한다.
- 404를 받으면 "게시글을 찾을 수 없어요"를, 401을 받으면 로그인 화면을 보여 준다.
- 수정이 성공하면 `PUT` 응답의 글로 상세를 보이고, `GET`을 다시 부르지 않는다.
- 같은 글을 두 탭에서 고쳐 차례로 저장하면 나중에 저장한 내용이 남는다. 편집을 시작할 때의 버전을 비교하는 계약은 이번에 만들지 않고 #260에서 다룬다 ([community README](README.md#아직-정하지-않은-것)). (#257 리뷰)
- 서버의 글 id·작성자 id·커서는 JavaScript의 안전한 정수(2^53-1 이하) 안에 있다고 본다. 벗어난 값이 오면 반올림된 값으로 다음 요청을 만들지 않고 오류를 보인다. (#257 리뷰)
- 목록의 "내가 쓴 글 · 댓글 · 북마크" 링크와 내 활동의 "커뮤니티로" 링크는 이 작업 전부터 데모 세션에서도 화면에 보이지 않는다. 앱 폭이 480px로 고정되어 `Life.module.css`의 `@container app (max-width: 1023px)` 규칙이 숨기기 때문이다. 서버 세션에서는 S10에 따라 그리지 않고, 데모 세션의 링크는 이번에 바꾸지 않는다. (#265 리뷰)

## 참고
- 글 API 계약은 [커뮤니티 글 작성·조회·수정·삭제](post.md)에 있다. 응답의 `postId`·`authorId`·`nextCursor`는 숫자이고, 시각은 ISO 문자열(`Instant`)이다. 목록 항목에는 `body` 대신 `preview`가 있고 `updatedAt`이 없다. `PUT`은 200과 수정된 글(`PostResponse`)을, `DELETE`는 204를 돌려준다.
- `createApiClient`에는 지금 `get`, `post`, `patch`, `postEmpty`만 있다. 수정(`PUT`)과 삭제(`DELETE`, 204)를 쓰려면 메서드를 더해야 한다.
- 의존 경계 검사(`tests/dependency-boundaries.test.ts`)는 기능 폴더 바로 아래 파일이 HTTP 클라이언트를 가져오지 못하게 한다. `features/community/source.ts`는 `ApiClient`가 아니라 `postApi`와 데모 저장소를 받는다.
- 데모 계정 판별은 `features/member/types/account.ts`의 `isDemoAccount`다.
- 지금 화면은 `useLife()`의 `LifeState.posts`·`comments`를 직접 걸러 쓴다. 관리자 신고 화면도 같은 `posts`를 보므로, 데모 저장소를 지우지 않는다.
