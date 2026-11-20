# 가족 계정: 초대·권한·소유주 이전

관련 이슈: [#133](https://github.com/likelion-backendschool-9th/pawmento/issues/133)

## 한눈에
- 끝나면: 소유주나 가족이 만든 초대 코드로 다른 회원이 그 반려동물의 가족이 되고, 함께 다이어리와 질문 답변을 기록한다. 소유주는 가족에게 소유주를 넘길 수 있다.
- 결정 필요: 소유주의 다른 사람 기록 삭제를 diary·question에 적용하는 순서(Q2)
- 규모: 5단계 (PR 4개 이상, 다른 도메인 교체는 도메인별)

## 질문
- Q2: 소유주가 가족의 다이어리·질문 답변을 삭제할 수 있게 하려면 diary(#32)의 "다른 작성자 변경 403" 규칙이 소유주 삭제에 한해 바뀌고, media의 첨부 참조 해제가 업로더가 아닌 회원에게도 열린다. diary·question·media 담당과 적용 시점을 맞춰 주세요. (답에 따라 작업 순서 4단계가 달라짐)

## 1. 접근 확인 포트 (#133-R1)
다른 도메인이 "이 회원이 이 반려동물의 소유주인가, 가족인가"를 한 입력 포트로 묻는다.

#133-S1 역할 확인 [테스트]
- Given 반려동물 P의 소유주 A, 가족 B, 관계없는 C
- When  `CheckPetAccessUseCase.check(P, 회원)`
- Then  A는 `OWNER`, B는 `FAMILY`, C는 `NONE`이다
- Then  없는 반려동물이면 `NONE`이다
- 소유주는 pet의 새 입력 포트 `GetPetOwnerUseCase.ownerOf(petId)`로 읽는다. 요청자를 검사하지 않고, 반려동물이 있으면 소유주 회원 id를, 없으면 빈 값을 돌려준다. 지금 `GetPetUseCase`는 요청자가 소유주일 때만 성공해서 역할 판정에 쓸 수 없다

## 2. 초대 코드 (#133-R2)
소유주와 가족은 초대 코드를 만들고, 다른 회원은 그 코드로 가족이 된다.

#133-S2 코드 만들기 [테스트]
- Given 소유주 A 또는 가족 B가 로그인했다
- When  `POST /api/v1/families/invite-codes`에 `{"petId": P}`
- Then  201과 `{"code": "K7Q2M9XA", "expiresAt": "…"}`를 받는다
- Then  관계없는 회원이 부르면 403, 없는 반려동물이면 404다

#133-S3 코드로 가족 되기 [테스트]
- Given 회원 D가 로그인했고 유효한 코드가 있다
- When  `POST /api/v1/families/members`에 `{"code": "K7Q2M9XA"}`
- Then  200과 `{"petId": P, "name": "보리", "tag": "…"}`를 받고, D는 P의 `FAMILY`다
- Then  같은 코드를 다시 쓰면 404다 (한 번만 쓸 수 있음)

#133-S4 쓸 수 없는 코드 [테스트]
- Given 만료됐거나 없는 코드
- Then  404 `NOT_FOUND`이고 가족이 생기지 않는다
- Given 이미 P의 소유주나 가족인 회원이 P의 코드를 쓴다
- Then  409 `CONFLICT`이고 코드는 소비되지 않는다

#133-S5 같은 코드 동시 사용 [테스트]
- Given 회원 D와 E가 같은 코드로 동시에 요청한다
- Then  한 명만 가족이 되고 다른 한 명은 404다

## 3. 가족 목록 (#133-R3)
소유주와 가족은 그 반려동물의 소유주와 가족을 본다.

#133-S6 목록 [테스트]
- When  `GET /api/v1/families?petId=P`
- Then  200과 `{"owner": {"memberId": 1, "nickname": "다윗", "tag": "0427"}, "members": [{"memberId": 2, "nickname": "수민", "tag": "1093", "joinedAt": "…"}]}`
- Then  관계없는 회원은 403이다

## 4. 기록 권한 (#133-R4)
가족은 다이어리·질문 답변을 보고 쓰며, 본인 글만 수정·삭제한다. 소유주는 다른 사람의 글을 삭제할 수 있지만 수정은 못 한다.

#133-S7 가족이 기록 [테스트: diary·question 시나리오 테스트]
- Given B가 P의 가족이다
- Then  B는 P의 다이어리·답변 목록·상세를 보고, 새 다이어리·답변을 쓴다
- Then  B는 자기 글을 수정·삭제한다
- Then  B가 A나 다른 가족의 글을 수정·삭제하면 403이다

#133-S8 소유주의 삭제 [테스트: diary·question 시나리오 테스트]
- Given B가 사진 M1을 올려 첨부한 다이어리 D1
- When  소유주 A가 D1을 삭제한다
- Then  204이고 D1과 첨부 연결이 없어지며, M1의 참조 수가 줄어 다른 곳에서 쓰지 않으면 삭제 작업이 등록된다
- When  A가 D1을 수정한다(첨부 변경 포함)
- Then  403이고 D1과 M1의 참조 수는 그대로다
- Given B가 사진 M2를 첨부한 질문 답변 Q1
- Then  A의 Q1 삭제는 204이고 M2의 참조가 풀리며, A의 Q1 수정은 403이다 (질문 답변 첨부가 들어온 뒤 적용)

#133-S9 반려동물 관리는 소유주만 [테스트: pet 시나리오 테스트]
- Given B가 P의 가족이다
- When  P의 정보 수정·모드 변경·삭제를 요청한다
- Then  403이다

## 5. 나가기·내보내기 (#133-R5)
가족은 스스로 나가고, 소유주만 가족을 내보낸다.

#133-S10 내보내기 [테스트]
- When  소유주 A가 `DELETE /api/v1/families/members/{memberId}?petId=P`로 B를 내보낸다
- Then  204이고 B는 P에 접근할 수 없다(목록 403)
- Then  B가 만들고 아직 쓰지 않은 P의 초대 코드는 404가 된다
- Then  가족 B가 다른 가족을 내보내면 403이다

#133-S11 나가기 [테스트]
- When  가족 B가 `DELETE /api/v1/families/members/me?petId=P`
- Then  204이고 B는 P에 접근할 수 없으며, B가 만든 쓰지 않은 코드는 404가 된다
- Then  소유주 A가 나가기를 요청하면 409다 (먼저 소유주를 넘겨야 함)

#133-S12 나간 뒤 글 [테스트: diary 시나리오 테스트]
- Given B가 쓴 다이어리가 있고 B가 나갔다
- Then  그 다이어리는 남고 작성자 표시도 유지된다

## 6. 소유주 이전 (#133-R6)
소유주는 가족 중 한 명을 골라 소유주를 넘긴다.

#133-S13 이전 [테스트]
- Given A가 소유주, B가 가족이다
- When  A가 `POST /api/v1/families/owner-transfers`에 `{"petId": P, "memberId": B}`
- Then  200이고 B가 `OWNER`, A는 `FAMILY`가 된다
- Then  B는 모드 변경을 할 수 있고, A는 403이다

#133-S14 이전 실패 [테스트]
- Given 가족이 아닌 회원 C를 고른다
- Then  400이고 소유주가 바뀌지 않는다
- Given 가족 B가 이전을 요청한다
- Then  403이다

#133-S15 넘겨받는 쪽에 같은 태그 [테스트]
- Given B가 이미 태그가 P와 같은 반려동물 Q의 소유주다
- When  A가 P를 B에게 넘긴다
- Then  200이고 P는 새 태그를 받아 B의 반려동물 안에서 태그가 겹치지 않는다. Q는 그대로다

#133-S16 이전은 한 번에 [테스트]
- Given 이전 중 소유주 변경까지 끝난 뒤 A의 가족 등록에서 저장이 실패하도록 테스트용 포트를 둔다
- When  A가 P를 B에게 넘긴다
- Then  오류 응답이고 P의 소유주는 A, B는 가족, A는 가족 목록에 없다(전부 롤백)
- 테스트 메서드에는 `@Transactional`을 걸지 않는다. 걸면 테스트 트랜잭션에 묶여 롤백을 확인할 수 없다

## 범위 밖
- 회원 탈퇴, 탈퇴 시 소유주 이전 강제와 가족이 없을 때 반려동물 데이터 삭제: 별도 이슈. 지금 pet은 기록이 있으면 삭제를 409로 막아서, 탈퇴 삭제는 diary·question·media·map의 데이터를 함께 지우는 흐름이 필요하다
- 커뮤니티 글의 가족 권한
- 나간 가족이 쓴 글을 지울지(우선 남긴다, 추후 결정)
- 가족 화면, 삭제 확인 팝업: 프론트엔드
- 데이터 내보내기(백업 파일)

## 작업 순서
1. 멤버십 저장과 접근 확인 포트 — S1
   - 파일: 새 `family` 도메인(`domain/PetFamilyMember`, `domain/PetAccess`(`OWNER`·`FAMILY`·`NONE`), `application/port/in/CheckPetAccessUseCase`, `adapter/out/persistence/PetFamilyMemberJpaEntity`(`pet_family_members`))
   - pet: 새 입력 포트 `GetPetOwnerUseCase`(`Optional<Long> ownerOf(Long petId)`, 요청자 검사 없음). pet 담당과 합의
   - 다른 도메인은 아직 바꾸지 않는다(동작 변화 없음)
2. 초대 코드와 가족 되기·목록 — S2~S6
   - 파일: `family/adapter/in/web/FamilyController`(`@RequestMapping("/families")`), `PetInviteCode`(`pet_invite_codes`, 코드 해시와 발급자 저장), 초대·가입·목록 유스케이스
3. 나가기·내보내기 — S10, S11
   - 멤버십 삭제와 그 회원이 발급한 P의 초대 코드 삭제를 한 트랜잭션으로 한다
4. 다른 도메인 접근 교체 — S7~S9, S12 (도메인별 PR, 담당과 순서 합의)
   - diary: `CheckDiaryPetAccessPort`가 역할(`OWNER`·`FAMILY`)을 돌려주게 하고 `OwnerDiaryPetAccessAdapter`를 가족 포트로 바꾼다. `DeleteDiaryEntryService`는 `requireAuthor` 대신 "작성자이거나 소유주"를 확인하고, 수정은 `requireAuthor`를 유지한다
     - 기존 시나리오 고침: #32-S2의 "타 작성자 변경 403" 끝에 `(고침 #133: 소유주의 삭제는 허용)`, #32-S5의 VIEWER 문구 끝에 `(고침 #133: 역할은 OWNER·FAMILY 둘)`
   - media: `ChangeMediaReferencesService.lock`의 업로더 검사는 새로 연결하는 첨부(`requested`에만 있는 id)에만 적용하고, 해제만 하는 첨부(`previous`에만 있는 id)는 아이와 용도만 확인한다. 소유주가 가족 일기를 지울 때 가족이 올린 첨부의 참조를 풀기 위해서다. media 담당과 합의
   - question: `OwnerQuestionPetAccessAdapter`를 가족 포트로 바꾼다. 답변 삭제·첨부가 들어오는 PR에서 diary와 같은 규칙을 쓴다
     - 기존 시나리오 고침: #31-S12의 VIEWER·다른 작성자 변경 문구 끝에 `(고침 #133: 역할은 OWNER·FAMILY 둘, 소유주의 삭제는 허용)`
   - pet: 관리 동작은 소유주만 그대로(S9)
5. 소유주 이전 — S13~S16
   - pet: 새 입력 포트 `ChangePetOwnerUseCase`(소유주 변경, 새 소유주 안에서 태그가 겹치면 `PetTagGenerator`로 다시 뽑음). pet 담당과 합의
   - HTTP 경계는 `FamilyController`가 맡는다. family 서비스가 한 트랜잭션에서 B의 멤버십 삭제(한 문장, 0줄이면 400) → pet 소유주 변경 → A의 멤버십 저장을 한다. pet 서비스는 같은 트랜잭션에 참여하고, 어디서 실패해도 전부 롤백된다

## 정한 것
- 역할은 소유주(`OWNER`)와 가족(`FAMILY`) 둘뿐이다. pet·프론트 문서의 EDITOR·VIEWER는 쓰지 않는다.
- 가족은 다이어리·질문 답변을 보고 쓰며, 본인 글만 수정·삭제한다.
- 소유주는 본인 반려동물의 다른 사람 글을 삭제할 수 있고 수정은 할 수 없다. 첨부가 있어도 삭제할 수 있다. 화면은 삭제 전에 한 번 더 확인한다.
- 커뮤니티 글은 이 권한에 들어가지 않는다.
- 초대는 초대 코드로 한다. 소유주와 가족 모두 코드를 만들 수 있다.
- 발급한 가족이 나가거나 내보내지면, 그 가족이 만든 쓰지 않은 코드도 함께 쓸 수 없게 된다.
- 다른 가족을 내보내는 것은 소유주만 한다. 가족은 스스로 나가기만 할 수 있다.
- 반려동물 정보 수정·모드 변경·삭제는 소유주만 한다.
- 소유주가 떠날 때는 소유주가 가족 중 이전 대상을 고른다. 가족이 없으면 반려동물 데이터를 삭제한다(탈퇴 이슈에서 구현).
- 소유주 이전 때 새 소유주에게 같은 태그의 반려동물이 있으면, 넘겨받는 반려동물의 태그를 새로 뽑는다.
- 나간 가족의 글은 우선 남기고 추후 결정한다.
- 가족 수 제한은 없다.
- 가족 API는 `/api/v1/families`에 두고 반려동물은 `petId` 입력으로 받는다.

## 가정
- 초대 코드는 헷갈리는 글자(0·O·1·I 등)를 뺀 8자리이고, 24시간 동안 한 번만 쓸 수 있다. DB에는 코드 해시만 저장한다.
- 쓸 수 없는 코드(없음·만료·이미 사용)는 모두 404로 같게 응답한다. 어떤 코드가 있었는지 드러내지 않기 위해서다.
- 코드 소비는 조회와 삭제를 나누지 않고 한 문장으로 한다(동시 사용, S5). #109의 연결 state와 같은 방식이다.
- 소유주는 계속 pet의 `owner_member_id`에 두고, 가족만 `pet_family_members`에 저장한다. 소유주 이전 때 새 소유주는 가족 목록에서 빠지고 이전 소유주가 가족으로 들어간다.
- 가족 목록은 닉네임과 계정 태그로 회원을 구분한다(계정 태그 #137이 먼저 들어간다).
- 접근이 없는 회원이 반려동물 하위 자원을 부르면 지금처럼 403이다. 404로 바꿀지는 diary 문서의 제안과 함께 각 도메인이 정한다.
- 이전 중 새 소유주의 반려동물 등록이 동시에 같은 태그로 들어오면 태그 유니크 제약으로 전체가 롤백되고 409다. 드물어서 재시도하지 않는다.

## 참고
- diary(`OwnerDiaryPetAccessAdapter`), question(`OwnerQuestionPetAccessAdapter`), media(`OwnerMediaPetAccessAdapter`)는 pet 소유 확인을 출력 어댑터로 분리해 두었다. 다만 diary 삭제의 작성자 검사(`requireAuthor`)와 media의 업로더 검사는 어댑터 밖에 있어 4단계에서 함께 바꾼다.
- diary 문서는 "다른 작성자 변경 403"을 정해 두었다. 소유주 삭제는 이 규칙의 예외다(Q2).
- pet은 기록이 있으면 삭제를 막는다(`PetHasRecordsException`, 409).
- pet 테이블은 `(owner_member_id, tag)`가 유일하다(`uk_pets_owner_tag`). 소유주가 바뀌면 이 조합도 바뀐다.
- pet README는 가족 멤버십을 `OWNER/EDITOR/VIEWER`로 적어 두었다. 이 계획은 두 역할로 정했다.