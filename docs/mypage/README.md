# mypage

마이페이지(내 정보·내 활동 진입)에서 정한 것, member·pet과의 경계, 아직 정하지 않은 것을 모은다.

문서 폴더 이름은 제품 용어 `mypage`다. 화면 URL은 `/me`, `/me/activity`다. API는 member·pet 등 기존 도메인을 조합한다.

## 이슈와 문서

| 이슈 | 내용 | 문서 |
| --- | --- | --- |
| [#45](https://github.com/likelion-backendschool-9th/pawmento/issues/45) | 설계 골격 | (이 README 초안) |
| [#51](https://github.com/likelion-backendschool-9th/pawmento/issues/51) | 마이페이지 본문 계획 문서 | 이 README, [home.md](home.md) |
| [#52](https://github.com/likelion-backendschool-9th/pawmento/issues/52) | 마이페이지 홈(내 정보·반려동물 진입·로그아웃) | [home.md](home.md) |

## 담당과 경계

회의(2026-11-16) 기준 도균 담당: **마이페이지**.

인증·세션 자체는 [member](../member/README.md) / [#14](https://github.com/likelion-backendschool-9th/pawmento/issues/14)다.

| 구분 | 위치 |
| --- | --- |
| 가입·로그인·로그아웃·쿠키·`GET /members/me`(최소 회원 정보) | member |
| 마이페이지 화면에서의 내 정보 표시·반려동물 요약·진입·로그아웃 UI | mypage (#52) |
| 내 글·댓글·북마크 등 커뮤니티 활동 | community 등과 계약 후 `/me/activity` 후속 |
| 반려동물 CRUD·모드 | [pet](../pet/README.md) / #46 |

mypage에서 인증을 다시 구현하지 않는다. #52는 백엔드 `mypage` 패키지를 만들지 않고 기존 API만 조합한다. ([home.md](home.md))

## 정한 것

- 화면 URL `/me`, 문서·제품 용어 `mypage`. (#51, #52)
- MVP 첫 슬라이스(#52)는 **조회·진입·로그아웃**. 닉네임 수정은 질문에 따라 member 합의 후(기본 가정: 후속). ([home.md](home.md))
- 내 활동·탈퇴·비밀번호 변경은 #52 범위 밖. (member·community 후속)

## 아직 정하지 않은 것

- Q1: 닉네임 수정을 #52에 넣을지 ([home.md](home.md))
- `/me/activity`와 community API 계약
- 닉네임 변경·탈퇴 API의 소속(member) 일정
