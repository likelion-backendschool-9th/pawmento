# 마이페이지 홈

관련 이슈: [#52](https://github.com/likelion-backendschool-9th/pawmento/issues/52)  
설계 문서 작업: [#51](https://github.com/likelion-backendschool-9th/pawmento/issues/51)

양식: [계획 문서 양식](../plan-template.md). 도메인 요약: [README](README.md).

## 한눈에
- 끝나면: 로그인한 보호자가 `/me`에서 닉네임·이메일을 보고, 반려동물 관리로 이동하며, 로그아웃할 수 있다. 서버 `GET /members/me`·`GET /pets`·`POST /auth/logout`에 연결한다.
- 결정 필요: 닉네임 수정을 이번 이슈에 넣을지(member API 확장 필요)
- 규모: 4단계 (이번 계획은 **프론트 마이페이지 홈**. 닉네임 수정·내 활동·탈퇴는 범위 밖 또는 질문에 따름)

## 질문
- Q1: 데모처럼 `/me`에서 닉네임을 바로 수정할 수 있게 할까요? 되면 member에 `PATCH /members/me`(또는 합의 API)가 필요하고 시나리오가 늘어난다. 지금은 조회만 하고 수정은 member 후속으로 미룰 수 있다. (답에 따라 #52-S2·작업 단계가 달라짐)

## 1. 내 정보 보기 (#52-R1)
로그인한 보호자가 `/me`에서 서버에 저장된 닉네임·이메일을 본다.

#52-S1 정상 [테스트]
- Given 회원 A가 닉네임 `"보리 보호자"`, 이메일 `"a@example.com"`으로 가입·로그인했다
- When  A가 `/me`를 연다
- Then  화면에 닉네임 `"보리 보호자"`와 이메일 `"a@example.com"`이 보인다
- Then  브라우저는 `GET /api/v1/members/me`를 호출하고 200을 받는다

#52-S2 비로그인 [테스트]
- Given 로그인 쿠키가 없다
- When  `/me`로 들어간다
- Then  마이페이지 본문(닉네임·이메일)이 보이지 않는다
- Then  로그인 화면(또는 로그인으로 보내는 흐름)으로 넘어간다
- Then  (보호 API를 직접 부르면) `GET /api/v1/members/me`는 401이다 — [테스트: AuthScenarioTest #14-S2]

## 2. 반려동물 진입 (#52-R2)
마이페이지에서 내 반려동물 관리로 이동하고, 함께하는 친구 수를 본다.

#52-S3 정상 [테스트]
- Given 회원 A가 로그인했고 반려동물 `"콩이"`, `"보리"` 두 마리를 등록했다
- When  A가 `/me`를 연다
- Then  함께하는 친구 수에 `2`가 보인다
- Then  `GET /api/v1/pets`가 200이고 목록 길이 2다
- When  A가 「반려동물 관리」로 이동한다
- Then  `/pets`(또는 합의한 반려동물 관리 경로)로 간다

#52-S4 반려동물 없음 [테스트]
- Given 회원 A가 로그인했고 반려동물이 없다
- When  A가 `/me`를 연다
- Then  함께하는 친구 수에 `0`이 보인다
- Then  「반려동물 관리」로 이동할 수 있다

## 3. 로그아웃 (#52-R3)
마이페이지에서 로그아웃하면 쿠키가 지워지고 보호 화면을 쓰지 못한다.

#52-S5 정상 [테스트]
- Given 회원 A가 로그인한 채 `/me`에 있다
- When  A가 로그아웃을 누른다
- Then  `POST /api/v1/auth/logout`이 204이고 `ACCESS_TOKEN` 쿠키가 지워진다
- Then  A는 로그인 전 화면(예: `/`)으로 이동한다
- When  A가 다시 `/me`로 가려 한다
- Then  #52-S2와 같이 마이페이지 본문을 보지 못한다

## 4. 기존 API만 조합 (#52-R4)
마이페이지 전용 백엔드 패키지·API를 새로 두지 않는다. member·pet·auth를 프론트에서 조합한다.

#52-S6 경계 [직접]
- Given #52 구현이 끝난 상태다
- When  리뷰어가 `backend/src/main/java/com/pawmento/` 아래 `mypage` 패키지 추가 여부를 본다
- Then  `mypage` 백엔드 패키지는 없다
- Then  추가·변경된 HTTP API 경로는 없고, 쓰는 것은 `GET /members/me`, `GET /pets`, `POST /auth/logout`뿐이다

## 범위 밖
- **닉네임 수정** — Q1이 “후속”이면 member 스프린트(프로필 수정). “이번”이면 member API 합의 후 시나리오를 이 문서에 더한다
- **내 활동** `/me/activity` (글·댓글·북마크) — community 계약 후 후속
- **회원 탈퇴·비밀번호 변경·찾기** — member 문서의 미정·후속
- **관리자 페이지** — [admin](../admin/README.md)
- **반려동물 CRUD·모드** — 이미 [pet/manage.md](../pet/manage.md) / #46. 마이페이지는 진입·요약만

## 작업 순서
1. `/me`에 서버 `GET /members/me`로 닉네임·이메일을 붙인다 — S1, S2
   - 파일: `frontend` 마이페이지(Account) 화면·auth HTTP 어댑터, 로그인 가드/리다이렉트
   - 확인: 프론트 테스트 도구 확정 후 `#52-S1` 등. 당분간 `[직접]`+Playwright/수동을 PR 확인 방법에 적어도 된다
2. `GET /pets`로 친구 수와 「반려동물 관리」 링크를 붙인다 — S3, S4
   - 파일: 같은 화면, pet HTTP 어댑터
   - 확인: 같은 방식
3. 로그아웃을 `POST /auth/logout`에 연결한다 — S5
   - 파일: auth 어댑터·세션 정리
   - 확인: 같은 방식. 쿠키 삭제는 [AuthScenarioTest #14-S6](../../backend/src/test/java/com/pawmento/member/AuthScenarioTest.java)과 맞춤
4. 백엔드 `mypage` 패키지·신규 API가 없는지 확인한다 — S6
   - 파일: 해당 없음(추가하지 않음)
   - 확인: `[직접]` PR 리뷰

## 정한 것
- 이번 계획(#52)은 **마이페이지 홈 화면**과 기존 API 연동이다. 백엔드 `com.pawmento.mypage`는 만들지 않는다
- 문서 폴더·제품 용어는 `mypage`, 화면 URL은 데모와 같이 `/me`를 쓴다. ([README](README.md))
- 인증·쿠키·로그아웃 계약은 [member](../member/README.md) / #14를 따른다. mypage에서 인증을 다시 구현하지 않는다
- 반려동물 목록·관리는 [pet](../pet/README.md) / #46 API를 쓰고, 마이페이지는 요약·진입만 한다

## 가정
- Q1 답이 오기 전 기본값은 **닉네임 수정은 범위 밖**(조회만). 데모의 “내 정보 저장”은 서버 연동에서 빼거나 비활성한다
- 로컬에서 프론트(3000)→백엔드(8080)는 member 문서대로 Next rewrites로 `/api/`를 넘긴다. CORS는 열지 않는다
- 「남긴 이야기」 기록 수는 diary/record API가 준비되기 전 **마이페이지 홈에 넣지 않는다**(또는 숨김). 친구 수(pet)만 표시한다
- 로그인 가드는 앱 공통 방식(쿠키 없음 → 로그인)을 쓰고, mypage만의 별도 인가 모델은 없다

## 참고
- 양식: [plan-template.md](../plan-template.md)
- 인증·`GET /members/me`·로그아웃: [member/README.md](../member/README.md), [auth-base.md](../member/auth-base.md)
- 반려동물 API: [pet/manage.md](../pet/manage.md)
- 데모 화면: `frontend` AccountScreen(`/me`), `frontend/README.md` 마이·활동 행
- 회의: [2026-11-16](../meetings/2026-11-16.md)
