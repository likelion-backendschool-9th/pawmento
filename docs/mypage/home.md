# 마이페이지 홈

관련 이슈: [#52](https://github.com/likelion-backendschool-9th/pawmento/issues/52)  
설계 문서 작업: [#51](https://github.com/likelion-backendschool-9th/pawmento/issues/51)

양식: [계획 문서 양식](../plan-template.md). 도메인 요약: [README](README.md).

## 한눈에
- 끝나면: 로그인한 보호자가 `/me`에서 닉네임·이메일을 보고, (월 1회 한도 안에서) 닉네임을 바꾸며, 반려동물 관리로 이동하고, 로그아웃할 수 있다. `GET /members/me`·닉네임 변경 API(member)·`GET /pets`·`POST /auth/logout`에 연결한다.
- 결정 필요: 없음
- 규모: 5단계 (프론트 마이페이지 홈 + member 닉네임 변경 API 합의·연동)

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
- Then  이 시나리오는 **화면 차단**을 프론트 자동 테스트로 확인한다. `AuthScenarioTest` #14-S2의 API 401만으로 대체하지 않는다

## 2. 닉네임 변경 (#52-R2)
보호자는 `/me`에서 닉네임을 바꿀 수 있다. **달력월(Asia/Seoul) 기준 한 달에 한 번**만 성공한다. API는 member 도메인에 두고, mypage 화면이 호출한다.

#52-S3 정상 [테스트]
- Given 회원 A가 로그인했고, 이번 달(Asia/Seoul)에 아직 닉네임을 바꾼 적이 없다
- When  A가 `/me`에서 닉네임을 `"콩이 보호자"`로 저장한다
- Then  member 닉네임 변경 API가 200(또는 합의한 성공)을 주고, 화면 닉네임이 `"콩이 보호자"`다
- Then  `GET /api/v1/members/me`의 `nickname`도 `"콩이 보호자"`다

#52-S4 월 한도 [테스트]
- Given 회원 A가 이번 달에 이미 닉네임을 한 번 바꿨다
- When  A가 다시 다른 닉네임으로 저장한다
- Then  400 또는 409와 `code: "VALIDATION"` 또는 `code: "CONFLICT"`(member와 합의한 코드)를 받는다
- Then  저장된 닉네임은 바뀌지 않는다

#52-S5 월 경계 [테스트]
- Given 고정 시계(Asia/Seoul)로 회원 A가 `2026-11-30` `23:59`에 닉네임을 `"11월 보호자"`로 바꾸는 데 성공했다
- When  시계를 `2026-12-01` `00:00`(Asia/Seoul)으로 옮긴 뒤 A가 닉네임을 `"12월 보호자"`로 저장한다
- Then  변경이 성공하고 `GET /api/v1/members/me`의 `nickname`이 `"12월 보호자"`다
- Then  이 시나리오는 **백엔드 자동 테스트**(고정 `Clock`)로 확인한다. 롤링 30일·UTC 달력월로 구현하면 이 케이스가 실패해야 한다

## 3. 반려동물 진입 (#52-R3)
마이페이지에서 내 반려동물 관리로 이동하고, 함께하는 친구 수를 본다.

#52-S6 정상 [테스트]
- Given 회원 A가 로그인했고 반려동물 `"콩이"`, `"보리"` 두 마리를 등록했다
- When  A가 `/me`를 연다
- Then  함께하는 친구 수에 `2`가 보인다
- Then  `GET /api/v1/pets`가 200이고 목록 길이 2다
- When  A가 「반려동물 관리」로 이동한다
- Then  `/pets`(또는 합의한 반려동물 관리 경로)로 간다

#52-S7 반려동물 없음 [테스트]
- Given 회원 A가 로그인했고 반려동물이 없다
- When  A가 `/me`를 연다
- Then  함께하는 친구 수에 `0`이 보인다
- Then  「반려동물 관리」로 이동할 수 있다

## 4. 로그아웃 (#52-R4)
마이페이지에서 로그아웃하면 쿠키가 지워지고 보호 화면을 쓰지 못한다.

#52-S8 정상 [테스트]
- Given 회원 A가 로그인한 채 `/me`에 있다
- When  A가 로그아웃을 누른다
- Then  `POST /api/v1/auth/logout`이 204이고 `ACCESS_TOKEN` 쿠키가 지워진다
- Then  A는 로그인 전 화면(예: `/`)으로 이동한다
- When  A가 다시 `/me`로 가려 한다
- Then  #52-S2와 같이 마이페이지 본문을 보지 못한다
- Then  로그아웃 후 `/me` 재진입 차단은 **프론트 자동 테스트**로 확인한다. `AuthScenarioTest` #14-S6의 쿠키 삭제만으로 이 Then을 대체하지 않는다

## 5. 패키지 경계 (#52-R5)
마이페이지 전용 백엔드 패키지는 두지 않는다. 인증·닉네임 변경은 member, 반려동물은 pet, 화면 조합은 frontend mypage다.

#52-S9 경계 [직접]
- Given #52 구현이 끝난 상태다
- When  리뷰어가 `backend/src/main/java/com/pawmento/` 아래 `mypage` 패키지 추가 여부를 본다
- Then  `mypage` 백엔드 패키지는 없다
- Then  닉네임 변경 API가 있다면 `member`(또는 팀이 합의한 member 경로)에만 있고, mypage 전용 컨트롤러는 없다

## 범위 밖
- **내 활동** `/me/activity` (글·댓글·북마크) — community 계약 후 후속
- **회원 탈퇴·비밀번호 변경·찾기** — member 문서의 미정·후속
- **관리자 페이지** — [admin](../admin/README.md)
- **반려동물 CRUD·모드** — 이미 [pet/manage.md](../pet/manage.md) / #46. 마이페이지는 진입·요약만
- **닉네임 확정 확인 UI(모달·팝업)** — 제품 의견으로 남겨 두었고, 프론트 닉네임 변경 UI 구현 때 다시 본다. ([정한 것](#정한-것)·[README](README.md))

## 작업 순서
1. `/me`에 `GET /members/me`와 **비로그인 화면 차단**을 붙인다 — S1, S2
   - 파일: `frontend` 마이페이지(Account) 화면·auth HTTP 어댑터, 로그인 가드/리다이렉트
   - 확인: 프론트 자동 테스트(`@DisplayName` 또는 팀이 정한 이름에 `#52-S1`, `#52-S2`). **S2는 수동·`[직접]`로 대체하지 않는다**
2. member와 닉네임 변경 API(월 1회·달력월)를 합의·연동한다 — S3, S4, S5
   - 파일: member 쪽 Request/UseCase(합의 경로), 프론트 저장 흐름
   - 확인: 백엔드 `#52-S3`·`#52-S4`·`#52-S5`(S5는 고정 `Clock`으로 월 경계), 프론트 `#52-S3`·`#52-S4`
3. `GET /pets`로 친구 수와 「반려동물 관리」 링크를 붙인다 — S6, S7
   - 파일: 같은 화면, pet HTTP 어댑터
   - 확인: 프론트 자동 테스트 `#52-S6`, `#52-S7`
4. 로그아웃을 `POST /auth/logout`에 연결하고 **재진입 차단**을 테스트한다 — S8
   - 파일: auth 어댑터·세션 정리
   - 확인: 프론트 자동 테스트 `#52-S8`. 쿠키 삭제는 [AuthScenarioTest #14-S6](../../backend/src/test/java/com/pawmento/member/AuthScenarioTest.java)과 맞추되, **화면 재진입 Then은 프론트 테스트가 담당**한다
5. 백엔드 `mypage` 패키지가 없는지 확인한다 — S9
   - 파일: 해당 없음
   - 확인: `[직접]` PR 리뷰

## 정한 것
- 이번 계획(#52)은 **마이페이지 홈**이다. 백엔드 `com.pawmento.mypage`는 만들지 않는다
- 문서 폴더·제품 용어는 `mypage`, 화면 URL은 `/me`다. ([README](README.md))
- 인증·쿠키·로그아웃 계약은 [member](../member/README.md) / #14를 따른다
- 반려동물 목록·관리는 [pet](../pet/README.md) / #46 API를 쓰고, 마이페이지는 요약·진입만 한다
- **닉네임은 Asia/Seoul 달력월 기준 한 달에 한 번만 변경할 수 있다.** (Q1 답, #51/#52)
- 닉네임 변경 API 소속은 **member**다. 경로·성공/한도 초과 `code`는 구현 전 member 담당과 한 줄로 합의한다
- **(후속 UI 메모)** 프론트에서 닉네임을 저장할 때, 모달·팝업으로 「이 닉네임으로 확정할까요?」를 한 번 더 묻는 방안이 리뷰에서 나왔다. #52 자동 테스트 범위에는 넣지 않고, 닉네임 변경 UI를 붙일 때 다시 검토한다

## 가정
- 「한 달」은 Asia/Seoul 기준 **달력의 그 달**(매월 1일에 한도가 리셋)이다. 롤링 30일이 아니다
- 닉네임 길이·검증은 가입과 같다(1~20자, member 기존 규칙)
- 로컬에서 프론트(3000)→백엔드(8080)는 member 문서대로 Next rewrites로 `/api/`를 넘긴다. CORS는 열지 않는다
- 「남긴 이야기」 기록 수는 diary API 준비 전 마이페이지 홈에 넣지 않는다. 친구 수(pet)만 표시한다
- 로그인 가드는 앱 공통 방식(쿠키 없음 → 로그인)을 쓴다

## 참고
- 양식·권한·보안은 `[테스트]`: [plan-template.md](../plan-template.md)
- 인증·`GET /members/me`·로그아웃: [member/README.md](../member/README.md), [auth-base.md](../member/auth-base.md)
- 반려동물 API: [pet/manage.md](../pet/manage.md)
- 데모 화면: `frontend` AccountScreen(`/me`), `frontend/README.md` 마이·활동 행
- 회의: [2026-11-16](../meetings/2026-11-16.md)
