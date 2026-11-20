# family

한 반려동물을 소유주와 가족이 함께 기록하는 기능이다. 가족은 소유주나 다른 가족이 만든 초대 코드로 들어온다. 가족 멤버십과 "이 회원이 이 반려동물의 소유주인가, 가족인가"를 확인하는 입력 포트는 이 도메인이 맡고, 다른 도메인(diary, question, media 등)은 이 포트로 접근을 확인한다.

## 이슈와 문서

| 이슈 | 내용 | 문서 |
| --- | --- | --- |
| [#133](https://github.com/likelion-backendschool-9th/pawmento/issues/133) | 가족 계정: 초대·권한·소유주 이전 | [membership.md](membership.md) |

## 데이터 구조 요약

`pet_family_members` — 가족 멤버십 (#133)

| 컬럼 | 설명 |
| --- | --- |
| `id` | 멤버십 id |
| `pet_id` | 반려동물 id |
| `member_id` | 가족 회원 id |
| `joined_at` | 가족이 된 시각 |
| 제약 | `(pet_id, member_id)` 유일 |

`pet_invite_codes` — 초대 코드 (#133)

| 컬럼 | 설명 |
| --- | --- |
| `id` | 코드 id |
| `pet_id` | 반려동물 id |
| `code_hash` | 코드 해시(유일). 원문은 저장하지 않는다 |
| `issuer_member_id` | 발급한 회원 id. 이 회원이 나가거나 내보내지면 코드도 지운다 |
| `expires_at` | 만료 시각(발급 후 24시간) |
| `created_at` | 발급 시각 |

소유주는 이 도메인에 저장하지 않고 pet의 `owner_member_id`를 읽는다.

## 정한 것
- 소유주는 pet의 `owner_member_id`에 두고, 가족만 이 도메인에 저장한다. (#133)
- 커뮤니티 글은 가족 권한에 들어가지 않는다. (#133)
- 가족 API는 `/api/v1/families`이고 반려동물은 `petId` 입력으로 받는다. (#133)

## 아직 정하지 않은 것
- 나간 가족이 쓴 글을 지울지 (지금은 남긴다)
- 회원 탈퇴 때 가족이 없는 반려동물의 데이터 삭제 흐름 (탈퇴 이슈)