# mypage

마이페이지(내 정보·내 활동 진입)에서 정한 것, member·pet과의 경계, 아직 정하지 않은 것을 모은다.

문서 폴더 이름은 제품 용어 `mypage`다. 화면 URL은 `/me`, `/me/activity`다. API는 member·pet 등 기존 도메인을 조합한다.

## 이슈와 문서

| 이슈 | 내용 | 문서 |
| --- | --- | --- |
| [#45](https://github.com/likelion-backendschool-9th/pawmento/issues/45) | 설계 골격 | (이 README 초안) |
| [#51](https://github.com/likelion-backendschool-9th/pawmento/issues/51) | 마이페이지 본문 계획 문서 | 이 README, [home.md](home.md) |
| [#52](https://github.com/likelion-backendschool-9th/pawmento/issues/52) | 마이페이지 홈(내 정보·닉네임·반려동물 진입·로그아웃) | [home.md](home.md) |

## 담당과 경계

회의(2026-11-16) 기준 도균 담당: **마이페이지**.

인증·세션 자체는 [member](../member/README.md) / [#14](https://github.com/likelion-backendschool-9th/pawmento/issues/14)다.

| 구분 | 위치 |
| --- | --- |
| 가입·로그인·로그아웃·쿠키·`GET /members/me`·닉네임 변경 API | member |
| 마이페이지 화면에서의 내 정보·닉네임 UI·반려동물 요약·진입·로그아웃 UI | mypage (#52) |
| 내 글·댓글·북마크 등 커뮤니티 활동 | community 등과 계약 후 `/me/activity` 후속 |
| 반려동물 CRUD·모드 | [pet](../pet/README.md) / #46 |

mypage에서 인증을 다시 구현하지 않는다. 백엔드 `mypage` 패키지는 만들지 않는다. ([home.md](home.md))

## 정한 것

- 화면 URL `/me`, 문서·제품 용어 `mypage`. (#51, #52)
- MVP 홈(#52): 조회·**닉네임 변경(월 1회)**·반려동물 진입·로그아웃. ([home.md](home.md))
- **닉네임은 Asia/Seoul 달력월 기준 한 달에 한 번만 변경**할 수 있다. (Q1 답, #51/#52)
- 닉네임 변경 API는 **member**에 둔다. 경로·에러 `code`는 구현 전 합의. (#52)
- **(후속 UI)** 닉네임 저장 전 모달·팝업으로 「이 닉네임으로 확정할까요?」를 한 번 더 묻는 방안이 리뷰에서 제안됨. 자동 테스트 범위 밖, 프론트 닉네임 UI 구현 때 재검토. (#51 리뷰, [home.md](home.md))
- 내 활동·탈퇴·비밀번호 변경은 #52 범위 밖. (member·community 후속)

## 아직 정하지 않은 것

- 닉네임 변경 API의 정확한 경로·한도 초과 시 HTTP 상태/`code` (member와 합의)
- `/me/activity`와 community API 계약
- 탈퇴 API 일정(member)
