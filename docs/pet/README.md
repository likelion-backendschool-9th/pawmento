# pet

반려동물 등록·관리·모드에서 정한 것, 다른 도메인이 pet을 쓰는 방법, 아직 정하지 않은 것을 모은다.

## 이슈와 문서

| 이슈 | 내용 | 문서 |
| --- | --- | --- |
| [#45](https://github.com/likelion-backendschool-9th/pawmento/issues/45) | 설계 문서 작업(반려동물 본문 + 마이·관리자 골격) | 이 README, [manage.md](manage.md), [admin](../admin/README.md), [mypage](../mypage/README.md) |
| [#46](https://github.com/likelion-backendschool-9th/pawmento/issues/46) | 반려동물 등록·관리(MVP, **백엔드 API**) | [manage.md](manage.md) |
| [#72](https://github.com/likelion-backendschool-9th/pawmento/issues/72) | pet 예외의 공통 기본 예외 전환 계획 | [exception-migration.md](exception-migration.md) |

## 도메인 담당이 쓰는 방법

### 소유·모드 확인

인증은 [member](../member/README.md)가 “누가 로그인했는가”만 알려 준다. “이 반려동물이 그 회원의 것인가”, “현재 모드는 무엇인가”는 pet 도메인이 확인한다. (#46)

다른 도메인(question, diary, map 등)은 pet 패키지 내부나 JPA를 직접 쓰지 않고, pet의 **입력 포트**만 부른다. 가족 멤버십(OWNER/EDITOR/VIEWER) 포트는 가족 담당과 맞춰 후속으로 연다. 1차(#46)는 소유자(`ownerMemberId`) 기준이다.

### 모드

모드는 `ACTIVE` | `GARDEN`이다. 질문 배정·화면 톤은 이 값을 따른다. 전환은 **소유자가 반려동물 관리에서만** 하며, 헤더의 아이 선택은 저장된 모드를 불러올 뿐 바꾸지 않는다. (#46, 앱 데모·디자인 토큰)

## 정한 것

- 패키지는 `com.pawmento.pet`이고 [백엔드 구조](../backend-architecture.md)의 헥사고날을 따른다. 도메인 객체는 순수 자바(Spring·JPA·웹 DTO import 없음). 샘플 `com.pawmento.record`는 쓰지 않는다. (#46)
- 삭제 시 연관 기록 여부는 pet 출력 포트 `HasPetRecordsPort`로 확인한다. diary 등 실제 기록 도메인이 어댑터로 연결한다. (#46)
- API 경로는 `/api/v1/pets…`이다. 컨트롤러에는 `/pets`만 쓰고 `/api/v1`은 `WebConfig`가 붙인다. (#22, #46)
- 로그인 회원 id는 `@LoginMember`로 받아 Command·Query의 `memberId`로 넘긴다. (#14, #46)
- MVP 범위는 **등록과 관리**까지다. 실소유 인증·인가는 범위 밖이다. (회의 2026-11-16)
- 이름이 같아도 가입할 수 있고, **태그로 구분**한다. (회의 2026-11-16) 태그 생성 규칙은 [manage.md](manage.md) 가정을 팀 확인 후 확정한다.
- 기록이 있으면 삭제하지 않고 409 `CONFLICT`로 거절한다. 보관·내보내기는 미정. (#46)
- 생일은 Asia/Seoul 기준 오늘보다 뒤면 거절한다. (#46)
- 예외는 [공통 예외 문서](../backend-exceptions.md)의 기본 예외를 상속하고 `GlobalExceptionHandler`로 연결한다. pet 전환 범위와 작은 작업 단위는 [#72 계획](exception-migration.md)을 따른다. (고침 #72: #62로 결정된 처리 방식을 미정 항목에서 구분)

## 데이터 구조 요약

`pet` 테이블(제안, 상세는 [manage.md](manage.md))

| 컬럼 | 설명 |
| --- | --- |
| `id` | 반려동물 id |
| `owner_member_id` | 소유자 회원 id |
| `name` | 이름 1~20자 |
| `tag` | 이름 중복 구분용 태그 |
| `species` | `DOG` / `CAT` / `OTHER` |
| `breed` | 품종. 비어 있을 수 있음 |
| `birthday` | 생일(DATE). 비어 있을 수 있음 |
| `photo_asset_id` 또는 URL | 프로필 사진. media 정책 확정 전 미정 |
| `mode` | `ACTIVE` / `GARDEN` |
| `created_at`, `updated_at` | 시각 |
| `version` | 동시 수정 충돌용(낙관적 잠금). 이름 수정과 모드 변경이 겹치면 409 (#46, PR #48) |

## 아직 정하지 않은 것

- 태그 자동 생성 규칙(형식·유니크 범위: 전역 vs 소유자 단위) — 가정은 소유자+tag
- 대표(선택) 아이를 서버에 둘지, 클라이언트만 둘지
- 프로필 사진 업로드(media 초기 용도는 diary/question 첨부. 프로필은 별도 정책)
- 가족 멤버십·역할 입력 포트의 최종 위치와 시그니처(question/diary #30 설계가 기대)
- 기록이 있는 프로필의 보관·내보내기
- pet 예외 전환의 공용 아키텍처 검사 변경 범위 — [#72 계획의 Q2](exception-migration.md#질문)에서 합의한다. 삭제 불가의 애플리케이션 분류와 기본 예외 4종 사용은 [확인된 결정](exception-migration.md#확인된-결정)을 따른다.
