# 반려동물 등록·관리

관련 이슈: [#46](https://github.com/likelion-backendschool-9th/pawmento/issues/46)  
설계 문서 작업: [#45](https://github.com/likelion-backendschool-9th/pawmento/issues/45)

양식: [계획 문서 양식](../plan-template.md). 도메인 요약: [README](README.md).

## 한눈에
- 끝나면: 백엔드 `/api/v1/pets`로 등록·조회·수정·모드 변경·삭제가 동작하고, 시나리오 테스트로 확인한다.
- 결정 필요: 없음
- 규모: 6단계 (이번 계획은 **백엔드만**. 프론트 연동은 후속)

## 1. 등록 (#46-R1)
로그인한 보호자가 이름·종으로 반려동물을 등록한다. 품종·생일은 선택이다.

#46-S1 정상 [테스트]
- Given 회원 A가 로그인했다
- When  `POST /api/v1/pets`에 `{"name":"콩이","species":"DOG","breed":"코기","birthday":"2015-03-02"}`를 보낸다
- Then  201과 본문에 `petId`, `name`이 `"콩이"`, `species`가 `"DOG"`, `mode`가 `"ACTIVE"`인 상세를 받는다
- Then  `GET /api/v1/pets`에 콩이가 있다

#46-S2 실패 [테스트]
- Given 회원 A가 로그인했고, 오늘(Asia/Seoul)이 2026-11-17이다
- When  `POST /api/v1/pets`에 이름이 `""`이거나 길이 21자, 또는 `species`가 `"BIRD"`, 또는 `birthday`가 `"2026-12-15"`(오늘보다 뒤)인 본문을 보낸다
- Then  400과 `code: "VALIDATION"`을 받는다
- Then  목록에 반려동물이 늘지 않는다

## 2. 조회·수정 (#46-R2)
소유자는 자신의 반려동물 목록·상세를 보고 이름·종·품종·생일을 수정한다.

#46-S3 목록·상세 [테스트]
- Given 회원 A가 콩이를 등록했다
- When  A가 `GET /api/v1/pets`와 `GET /api/v1/pets/{petId}`를 부른다
- Then  목록과 상세에 콩이의 이름·종·모드가 같다

#46-S4 수정 [테스트]
- Given 회원 A가 콩이(`petId=1`)를 등록했다
- When  A가 `PATCH /api/v1/pets/1`에 `{"name":"콩콩","species":"DOG"}`를 보낸다
- Then  200과 상세의 `name`이 `"콩콩"`이다
- Then  `GET /api/v1/pets/1`에도 `"콩콩"`이다

## 3. 이름 태그 (#46-R3)
이름이 같아도 등록할 수 있으며, 태그로 구분한다.

#46-S5 같은 이름 [테스트]
- Given 회원 A가 `"보리"` 이름으로 한 마리를 등록했다
- When  A가 다시 `"보리"` 이름으로 등록한다
- Then  둘 다 201로 성공한다
- Then  두 응답의 `tag` 값이 서로 다르다
- Then  `GET /api/v1/pets`에 보리가 두 마리 있다

## 4. 모드 (#46-R4)
모드는 `ACTIVE` 또는 `GARDEN`이다. 등록 직후는 `ACTIVE`다. 소유자만 `PATCH .../mode`로 바꾼다.

#46-S6 모드 전환 [테스트]
- Given 회원 A가 콩이(`petId=1`, `mode=ACTIVE`)를 등록했다
- When  A가 `PATCH /api/v1/pets/1/mode`에 `{"mode":"GARDEN"}`을 보낸다
- Then  200과 상세의 `mode`가 `"GARDEN"`이다
- When  A가 `{"mode":"ACTIVE"}`로 다시 보낸다
- Then  200과 `mode`가 `"ACTIVE"`이다

#46-S7 잘못된 모드 [테스트]
- Given 회원 A가 콩이(`petId=1`)를 등록했다
- When  A가 `PATCH /api/v1/pets/1/mode`에 `{"mode":"PARK"}`을 보낸다
- Then  400과 `code: "VALIDATION"`을 받는다
- Then  상세의 `mode`는 그대로다

## 5. 권한 (#46-R5)
로그인 없이는 401이다. 다른 회원의 반려동물은 조회·수정·모드 변경·삭제할 수 없다(403).

#46-S8 비로그인 [테스트]
- Given 로그인 쿠키가 없다
- When  `GET /api/v1/pets`를 부른다
- Then  401과 JSON 에러를 받는다
- Then  로그인 페이지로 넘어가지 않는다

#46-S9 다른 회원 [테스트]
- Given 회원 A가 콩이(`petId=1`)를 등록했고 회원 B가 로그인했다
- When  B가 `GET /api/v1/pets/1`, `PATCH /api/v1/pets/1`, `PATCH /api/v1/pets/1/mode`, `DELETE /api/v1/pets/1`을 부른다
- Then  각 요청이 403이다
- Then  A의 콩이 상세·모드는 바뀌지 않았다

## 6. 삭제 (#46-R6)
연관 기록이 없는 프로필만 삭제한다. 기록이 있으면 삭제하지 않는다.

#46-S10 삭제 [테스트]
- Given 회원 A의 콩이(`petId=1`)에는 연관 기록이 없고, 보리(`petId=2`)에는 연관 기록이 있다(`HasPetRecordsPort`가 true)
- When  A가 `DELETE /api/v1/pets/1`을 보낸다
- Then  204이다
- Then  `GET /api/v1/pets/1`은 404이다
- When  A가 `DELETE /api/v1/pets/2`를 보낸다
- Then  409와 `code: "CONFLICT"`를 받는다
- Then  `GET /api/v1/pets/2`에 보리가 남아 있다

## 범위 밖
- **프론트엔드 화면·HTTP 어댑터 연동** — 이번 계획·#46 구현 범위 밖. API가 준비된 뒤 후속 이슈에서 다룬다
- 실소유 인증·인가 — 회의상 MVP 밖, 추후 설계
- 가족 초대·역할 API — 계정/가족 담당. 멤버십 포트는 후속
- 프로필 사진 media 업로드 — media 초기 용도는 diary/question 첨부
- 기록이 있는 프로필의 보관·내보내기 정책 — 삭제 거절만 이번 범위
- 관리자·마이페이지 본문 — [admin](../admin/README.md), [mypage](../mypage/README.md) 골격

## 작업 순서
1. 도메인·등록·목록·상세를 가장 얇게 연결한다 — S1, S3
   - 파일: `backend/.../pet/domain/Pet.java`, `PetMode.java`, `CreatePetService`, `PetController`, `PetPersistenceAdapter`, …
   - 확인: `./gradlew test --tests '*PetScenarioTest'`
2. 입력 검증을 더한다 — S2
   - 파일: `CreatePetRequest` 등 Request Bean Validation
   - 확인: 같은 명령
3. 수정을 붙인다 — S4
   - 파일: `EditPetService`, `PATCH` 매핑
   - 확인: 같은 명령
4. 태그를 발급한다 — S5
   - 파일: `Pet` 생성 규칙, 저장소 유니크(소유자+tag 가정)
   - 확인: 같은 명령
5. 모드 API와 권한을 붙인다 — S6, S7, S8, S9
   - 파일: `ChangePetModeService`, 소유 검사
   - 확인: 같은 명령
6. 삭제를 붙인다 — S10
   - 파일: `DeletePetService`, `HasPetRecordsPort`(diary 등 실제 기록 도메인이 어댑터로 연결. 샘플 `com.pawmento.record`는 쓰지 않음)
   - 확인: 같은 명령

## 정한 것
- 이번 계획(#46)은 **백엔드 API·도메인·테스트만** 다룬다. 프론트는 후속
- MVP 기능 범위는 등록·관리·모드까지. 실소유 인증은 밖. (회의 2026-11-16)
- 이름 중복은 태그로 구분. (회의)
- 모드 변경은 소유자·`PATCH /pets/{id}/mode`만. (제품 규칙·데모와 맞춤; 화면 구현은 후속)
- 기록이 있으면 삭제하지 않음(정책 확정 전). (데모)
- 생일은 Asia/Seoul 기준 **오늘보다 뒤면 거절**(400 `VALIDATION`). 비우거나 오늘·과거는 허용
- 연관 기록이 있어 삭제를 거절하면 **409 `CONFLICT`**. (요청은 이해했지만 현재 상태와 충돌. member 중복 이메일도 409)
- 패키지 `com.pawmento.pet`, 순수 자바 domain([백엔드 구조](../backend-architecture.md)). `Member` 도메인 JPA 패턴·샘플 `com.pawmento.record`는 따르지 않음
- 컨트롤러 경로는 `/pets`만. `/api/v1`은 `WebConfig`(#22)
- `@LoginMember`로 `memberId`를 Command·Query에 담음. (#14)

## 가정
- 종은 `DOG`, `CAT`, `OTHER` 세 가지로 시작한다. (FE 데모)
- 이름 1~20자, 저장 전 trim. (FE `validatePet`)
- 생일을 모르면 필드를 비울 수 있다. 미래일은 거절(정한 것)
- 등록 시 `mode`는 항상 `ACTIVE`. 요청 본문으로 모드를 받지 않는다
- 1차 “내 것” 기준은 `owner_member_id == memberId`. 가족 역할은 후속
- 태그 유니크는 `(owner_member_id, tag)`로 두고, 서버가 소유자 범위에서 짧은 태그를 발급한다(팀 확인 전 가정)
- 1차 목록은 페이지 없이 내 소유 전체. 프로필 사진 필드는 생략 가능
- 기록 존재 확인은 pet 출력 포트 `HasPetRecordsPort`로 한다. diary 등 실제 기록 도메인이 어댑터로 연결하고, 샘플 `com.pawmento.record`는 쓰지 않는다. 어댑터가 없으면 기록이 없다고 본다
- API 확인은 통합 테스트(쿠키 로그인)로 한다. 프론트 프록시·화면은 이번 범위에 넣지 않는다

## 참고
- 양식·Given/When/Then 규칙: [plan-template.md](../plan-template.md)
- 인증·에러 JSON: [member](../member/README.md), [auth-base](../member/auth-base.md)
- 필드·검증 참고(제품 규칙 출처, 이번 PR에서 FE를 구현하지 않음): [models.ts](../../frontend/features/workspace/domain/models.ts), [useCases.ts](../../frontend/features/workspace/application/useCases.ts)
- 구조: [backend-architecture.md](../backend-architecture.md)
- question/diary가 기대하는 pet 모드·멤버십: 원격 설계 #30
- 회의: [2026-11-16](../meetings/2026-11-16.md)
