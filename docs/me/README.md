# me

마이페이지(내 정보·내 활동 진입)에서 정할 것과 member 인증과의 경계를 모은다. **지금은 골격만** 둔다. 본문 계획·시나리오·API 표는 후속 이슈에서 연다.

## 이슈와 문서

| 이슈 | 내용 | 문서 |
| --- | --- | --- |
| [#45](https://github.com/likelion-backendschool-9th/pawmento/issues/45) | 설계 골격(이 README) | 이 문서 |
| (후속) | 마이페이지 본문·구현 | `docs/me/<기능>.md` — 아직 없음 |

## 담당과 경계

회의(2026-11-16) 기준 도균 담당: **마이페이지**.

인증·세션 자체는 [member](../member/README.md) / [#14](https://github.com/likelion-backendschool-9th/pawmento/issues/14)다.

| 구분 | 위치 |
| --- | --- |
| 가입·로그인·로그아웃·쿠키·`GET /members/me`(최소 회원 정보) | member |
| 마이페이지 화면에서의 프로필 확장·내 반려동물 요약·내 활동 진입 | me (후속) |
| 내 글·댓글·북마크 등 커뮤니티 활동 | community 등과 계약 후 me 화면에서 조합 |

member 문서의 범위 밖(프로필 수정, 탈퇴, 비밀번호 변경 등)과 일정을 맞춘다. me에서 인증을 다시 구현하지 않는다.

## MVP에서 다룰 후보 (미착수)

앱 데모 `/me`, `/me/activity` 기준 자리:

- 닉네임 등 내 정보 표시·수정(member 확장과 합의)
- 내 반려동물 목록으로의 이동(실제 CRUD는 [pet](../pet/README.md))
- 내 활동(커뮤니티) 진입
- 로그아웃(이미 member API)

## 아직 정하지 않은 것

- me 전용 API를 둘지, member·community API만 조합할지
- 닉네임 변경·탈퇴의 소속(member vs me)
- 본문 요구사항·시나리오([plan-template](../plan-template.md) 적용)
- 개발 Feat 이슈 번호
