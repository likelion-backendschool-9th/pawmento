# 가족 계정: 초대·권한·소유주 이전

관련 이슈: [#133](https://github.com/likelion-backendschool-9th/pawmento/issues/133)

## 한눈에
- 끝나면: 소유주나 가족이 만든 초대 코드로 다른 회원이 그 반려동물의 가족이 되고, 함께 다이어리와 질문 답변을 기록한다. 소유주는 가족에게 소유주를 넘길 수 있고, 가족이 있는 반려동물을 삭제하면 가장 먼저 가족이 된 회원이 소유주를 이어받는다.
- 결정 필요: 소유주의 다른 사람 기록 삭제를 diary·question·media에 적용하는 순서(Q2)
- 규모: 6단계 (PR 5개 이상, 다른 도메인 교체는 도메인별)

## 질문
- Q2: 소유주가 가족의 다이어리·질문 답변을 삭제할 수 있게 하려면 diary(#32)의 "다른 작성자 변경 403" 규칙이 소유주 삭제에 한해 바뀌고, media의 참조 잠금·해제 계약이 바뀐다(4단계). diary·question·media 담당과 적용 시점을 맞춰 주세요. (답에 따라 작업 순서 4단계가 달라짐)

## 1. 접근 확인 포트 (#133-R1)
다른 도메인이 "이 회원이 이 반려동물의 소유주인가, 가족인가"를 한 입력 포트로 묻는다.

#133-S1 역할 확인 [테스트]
- Given 반려동물 P의 소유주 A, 가족 B, 관계없는 C
- When  `CheckPetAccessUseCase.check(new CheckPetAccessQuery(P, 회원))`
- Then  `CheckPetAccessResult`의 역할이 A는 `OWNER`, B는 `FAMILY`, C는 `NONE`이다
- Then  없는 반려동물이면 `NONE`이다
- 소유주는 pet의 새 입력 포트 `GetPetOwnerUseCase.get(new GetPetOwnerQuery(P))`로 읽는다. 요청자를 검사하지 않고, 반려동물이 있으면 `GetPetOwnerResult(ownerMemberId)`를, 없으면 빈 `Optional`을 돌려준다. 지금 `GetPetUseCase`는 요청자가 소유주일 때만 성공해서 역할 판정에 쓸 수 없다

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
    지금(#217)은 `{"petId": P}`만 준다. `name`·`tag`는 가족 목록(S6) PR에서 붙인다
- Then  같은 코드를 다시 쓰면 404다 (한 번만 쓸 수 있음)

#133-S4 쓸 수 없는 코드 [테스트]
- Given 만료됐거나 없는 코드
- Then  404 `NOT_FOUND`이고 가족이 생기지 않는다
- Given 이미 P의 소유주나 가족인 회원이 P의 코드를 쓴다
- Then  409 `CONFLICT`이고 코드는 소비되지 않는다
- Given 코드 K가 유효할 때 가입이 반려동물 행 잠금을 기다리고, 기다리는 동안 K가 만료된다(테스트용 Clock으로 잠금 뒤 시각을 만료 이후로 둔다)
- Then  404이고 멤버십이 생기지 않으며, K는 소비되지 않고 만료 상태로 남는다

#133-S5 같은 코드 동시 사용 [테스트]
- Given 회원 D와 E가 같은 코드로 동시에 요청한다
- Then  한 명만 가족이 되고 다른 한 명은 404다

#133-S19 같은 회원이 다른 코드로 동시 가입 [테스트]
- Given 실제 PostgreSQL. P의 유효한 코드 K1·K2가 있다. 테스트 메서드에는 트랜잭션을 두지 않는다
- When  회원 D가 K1과 K2로 동시에 가입한다
- Then  하나는 200, 다른 하나는 409이고 D의 멤버십은 1줄이다. 409로 끝난 쪽의 코드는 그대로 남아 다른 회원이 쓸 수 있다
- Given 멤버십 저장이 실패하도록 테스트용 포트를 둔다
- Then  가입은 오류 응답이고, 쓴 코드는 롤백되어 다시 쓸 수 있다

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
- Given B가 쓴 첨부 없는 다이어리 D1과 D2
- When  소유주 A가 D2를 수정한다
- Then  403이고 D2는 그대로다
- When  A가 D1을 삭제한다
- Then  204이고 D1이 없어진다
- Given B가 사진 M1을 올려 첨부한 다이어리 D3과, M2를 첨부한 D4
- When  A가 D4를 수정한다(첨부 제거 포함)
- Then  403이고 D4와 M2의 참조 수는 그대로다
- When  A가 D3을 삭제한다
- Then  204이고 D3과 첨부 연결이 없어지며, M1의 참조 수가 줄어 다른 곳에서 쓰지 않으면 삭제 작업이 등록된다
- 질문 답변: 첨부 없는 답변과 첨부 있는 답변의 삭제 204·참조 해제, 수정 403·참조 수 유지를 diary·media와 같은 단계에서 같은 방식으로 함께 확인한다. 첨부 유무에 따라 소유주 삭제 권한이 달라지는 단계는 두지 않는다 (고침 #133: 질문 답변 첨부가 이미 구현되어 텍스트 답변 단계가 따로 없음. [가족 연동 계획](../diary/family-integration.md#권한-계약))

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
- Then  소유주 A가 나가기를 요청하면 409다 (먼저 소유주를 넘기거나 반려동물을 삭제해야 함)

#133-S12 나간 뒤 글 [테스트: diary 시나리오 테스트]
- Given B가 쓴 다이어리가 있고 B가 나갔다
- Then  그 다이어리는 남고 작성자 표시도 유지된다

#133-S18 발급과 내보내기가 겹침 [테스트]
- Given 실제 PostgreSQL. 테스트 메서드에는 트랜잭션을 두지 않는다
- 발급이 먼저: 가족 B의 발급이 반려동물 행을 잠그고 B의 멤버십을 확인한 순간 barrier로 멈추고, A의 B 내보내기를 시작한다
  - Then  내보내기는 잠금을 기다린다. 발급이 201로 커밋한 뒤 내보내기가 멤버십과 그 코드를 함께 지워, B가 만든 P의 코드는 0개다
- 내보내기가 먼저: A의 B 내보내기가 반려동물 행을 잠그고 B의 멤버십을 지운 순간 barrier로 멈추고, B의 발급을 시작한다
  - Then  발급은 잠금을 기다린다. 내보내기가 커밋한 뒤 발급은 B의 멤버십이 없어 403이고, B가 만든 P의 코드는 0개다

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
- Then  403이고, B의 멤버십 조회나 삭제 전에 거절된다

#133-S15 넘겨받는 쪽에 같은 태그 [테스트]
- Given B가 이미 태그가 P와 같은 반려동물 Q의 소유주다
- When  A가 P를 B에게 넘긴다
- Then  200이고 P는 새 태그를 받아 B의 반려동물 안에서 태그가 겹치지 않는다. Q는 그대로다

#133-S16 이전은 한 번에 [테스트]
- Given 이전 중 소유주 변경까지 끝난 뒤 A의 가족 등록에서 저장이 실패하도록 테스트용 포트를 둔다
- When  A가 P를 B에게 넘긴다
- Then  오류 응답이고 P의 소유주는 A, B는 가족, A는 가족 목록에 없다(전부 롤백)
- 테스트 메서드에는 `@Transactional`을 걸지 않는다. 걸면 테스트 트랜잭션에 묶여 롤백을 확인할 수 없다

#133-S17 서로 다른 대상으로 동시 이전 [테스트]
- Given 실제 PostgreSQL. A가 소유주이고 B·C가 가족이다. B로의 이전이 반려동물 행을 잠근 순간 barrier로 멈추고 C로의 이전을 시작한다. 테스트 메서드에는 트랜잭션을 두지 않는다
- When  두 요청이 함께 끝난다
- Then  B로의 이전은 200이다. C로의 이전은 잠금을 기다린 뒤 A가 더 이상 소유주가 아니어서 403이다
- Then  소유주는 B, C는 여전히 가족, A는 가족 목록에 한 번만 있다

#133-S20 가입과 이전이 겹침 [테스트]
- Given 실제 PostgreSQL. A가 소유주, B가 가족이고 P의 유효한 코드 K가 있다. 테스트 메서드에는 트랜잭션을 두지 않는다
- When  A가 P를 B에게 넘기는 요청이 반려동물 행을 잠근 순간 barrier로 멈추고, B가 K로 가입을 요청한다
- Then  이전은 200이고, 가입은 잠금을 기다린 뒤 B가 소유주라 409이며 K는 그대로 남는다
- Then  B는 `pet_family_members`에 없고, 소유주와 멤버십이 겹치는 행이 0개다

## 7. 반려동물 삭제 (#133-R7)
가족이 있으면 반려동물 삭제는 소유주의 나가기와 같다. 가장 먼저 가족이 된 회원이 소유주를 이어받는다. 회원 탈퇴에서 양도하지 않았을 때와 같은 규칙이다.

#133-S21 가족이 있는 반려동물 삭제 [테스트: pet 시나리오 테스트]
- Given A가 소유주이고 B·C가 가족이다. C가 B보다 먼저 가족이 됐다. A가 만든 쓰지 않은 코드 K가 있다
- When  A가 `DELETE /api/v1/pets/{petId}`
- Then  204이고 P와 P의 기록은 남는다. C가 `OWNER`, B는 `FAMILY`이며 A는 P에 접근할 수 없다(목록 403)
- Then  C는 가족 목록에 없고, K는 404다
- Then  C에게 P와 같은 태그의 반려동물이 있으면 P는 새 태그를 받는다
- Then  기록이 있어도 같은 결과다(삭제가 아니라 이어받기라서 409가 아니다)

#133-S22 가족이 없는 반려동물 삭제 [테스트: pet 시나리오 테스트]
- Given A가 소유주이고 가족이 없으며, 쓰지 않은 코드 K가 있다
- When  기록이 없는 P를 A가 삭제한다
- Then  204이고 P가 없어지며 K는 404, P의 `pet_invite_codes` 행은 0개다
- When  기록이 있는 P를 삭제한다
- Then  지금처럼 409이고 P와 K는 그대로다
- Given 실제 PostgreSQL에서 삭제가 반려동물 행을 잠근 순간 barrier로 멈추고 회원 D가 K로 가입한다
- Then  삭제는 204, 가입은 잠금을 기다린 뒤 404이고 멤버십이 생기지 않는다

## 범위 밖
- 회원 탈퇴: 별도 이슈. 팀 합의(PR #136 리뷰)는 정한 것에 적었다. 지금 pet은 기록이 있으면 삭제를 409로 막아서, 30일 뒤 영구 삭제는 diary·question·media·map의 데이터를 함께 지우는 흐름이 필요하다
- 커뮤니티 글의 가족 권한
- 나간 가족이 쓴 글을 지울지(우선 남긴다, 추후 결정)
- 가족 화면, 삭제 확인 팝업: 프론트엔드
- 데이터 내보내기(백업 파일)

## 작업 순서
1. 멤버십 저장과 접근 확인 포트 — S1
   - 파일: 새 `family` 도메인(`domain/PetFamilyMember`, `domain/PetAccess`(`OWNER`·`FAMILY`·`NONE`), `application/port/in/CheckPetAccessUseCase`·`CheckPetAccessQuery`·`CheckPetAccessResult`, `adapter/out/persistence/PetFamilyMemberJpaEntity`(`pet_family_members`))
   - pet: 새 입력 포트 두 개. pet 담당과 합의
     - `GetPetOwnerUseCase`·`GetPetOwnerQuery(petId)`·`GetPetOwnerResult(ownerMemberId)`. 반환은 `Optional<GetPetOwnerResult>`, 요청자 검사와 잠금은 없다(읽기용)
     - `LockPetOwnerUseCase`·`LockPetOwnerQuery(petId)`. 반려동물 행을 `FOR UPDATE`로 읽어 소유주를 돌려준다. 호출자의 트랜잭션 안에서만 부른다(`Propagation.MANDATORY`)
   - 다른 도메인은 아직 바꾸지 않는다(동작 변화 없음)
2. 초대 코드와 가족 되기·목록 — S2~S6, S19
   - 파일: `family/adapter/in/web/FamilyController`(`@RequestMapping("/families")`), `PetInviteCode`(`pet_invite_codes`, 코드 해시와 발급자 저장), 초대·가입·목록 유스케이스
   - 발급: 한 트랜잭션에서 반려동물 행 잠금 → 요청자가 소유주나 가족인지 확인(아니면 403) → 코드 저장
   - 가입: 한 트랜잭션에서 코드 해시로 반려동물 id 조회(없으면 404) → 반려동물 행 잠금 → 잠금 뒤 시각을 읽어 코드가 있고 만료 전인지 다시 확인(아니면 404) → 요청자가 이미 소유주나 가족이면 409(코드는 그대로) → 코드 소비(`code_hash`와 `expires_at > 잠금 뒤 시각`을 조건으로 한 문장 삭제, 0줄이면 404) → 멤버십 저장. 어디서 실패해도 코드 소비까지 롤백된다
   - 목록: 소유주 id → 가족 줄(가입 순, 같으면 id 순) → 요청자가 소유주나 가족인지 확인(아니면 403) → 회원 프로필을 한 번에 조회. 잠그지 않는다
   - member: 새 입력 포트 `ListMemberProfilesUseCase`·`ListMemberProfilesQuery(memberIds)` → `List<MemberProfileResult(memberId, nickname, tag)>`. 다른 회원에게 보여도 되는 값만 주고(이메일·역할 제외), 없는 회원은 예외 없이 빠진다
   - pet: 새 입력 포트 `GetPetSummaryUseCase`·`GetPetSummaryQuery(petId)` → `Optional<PetSummaryResult(petId, name, tag)>`. 요청자 검사가 없다. 부르는 도메인이 권한을 먼저 확인한 뒤에만 부른다. pet 담당과 합의(#236)
3. 나가기·내보내기 — S10, S11, S18
   - 한 트랜잭션에서 반려동물 행 잠금 → 권한 확인 → 멤버십 삭제 → 그 회원이 발급한 P의 코드 삭제
4. 다른 도메인 접근 교체 — S7~S9, S12 (도메인별 PR, 담당과 순서 합의)
   - diary: `CheckDiaryPetAccessPort`가 역할(`OWNER`·`FAMILY`)을 돌려주게 하고 `OwnerDiaryPetAccessAdapter`를 가족 포트로 바꾼다. `DeleteDiaryEntryService`는 `requireAuthor` 대신 "작성자이거나 소유주"를 확인하고, 수정은 `requireAuthor`를 유지한다
     - media에 넘기는 `previous`는 권한 확인을 마친 뒤 `DiaryAttachmentsPort.find(entryId)`로 DB에서 읽은 그 일기의 실제 첨부만 쓴다. 요청 본문의 값은 `previous`로 넘기지 않는다
     - `DiaryMediaPort.lock(memberId, petId, previous, requested)`로 바꾼다. 삭제는 `lock(…, previous, List.of())`, 수정은 `lock(…, previous, requested)`다
     - 기존 시나리오 고침: #32-S2의 "타 작성자 변경 403" 끝에 `(고침 #133: 소유주의 삭제는 허용)`, #32-S5의 VIEWER 문구 끝에 `(고침 #133: 역할은 OWNER·FAMILY 둘)`
   - media — 5단계 pet 작업([#206](https://github.com/likelion-backendschool-9th/pawmento/pull/206))에서 `LockMediaReferencesCommand`와 `ChangeMediaReferencesCommand` 모두 `previous`·`requested`를 구분하고, `PET_PROFILE`의 `previous`에만 업로더 검사 예외를 적용한다. 4단계에서 남은 일은 일기·질문의 실제 연결을 `previous`로 전달하고 `DIARY_ATTACHMENT`·`QUESTION_ANSWER_ATTACHMENT`에도 아래 기준을 적용하는 것이다. 잠금과 `change`는 같은 기준을 쓴다. (고침 #133: 프로필 사진에 적용한 분리와 일기·질문 후속 범위를 구분)
     - `requested`에만 있는 id(새 연결): 지금처럼 업로더가 요청자이고, 아이가 같고, 용도가 `purpose`와 같아야 한다
     - `previous`에 있는 id(유지·해제): 아이와 용도만 확인한다. 이 목록은 호출하는 도메인이 권한 확인 후 DB에서 읽은 실제 연결이라는 계약이다
     - diary는 `DIARY_ATTACHMENT`, question은 `QUESTION_ANSWER_ATTACHMENT`를 넘긴다.
     - media 담당과 합의
   - question: `OwnerQuestionPetAccessAdapter`를 가족 포트로 바꾼다. 소유주 삭제·수정 거부를 첨부 유무와 관계없이 diary·media와 같은 단계에서 같은 규칙으로 넣고, 위 media 계약을 `QUESTION_ANSWER_ATTACHMENT`로 쓴다 (고침 #133: 질문 답변 첨부가 이미 구현되어 텍스트 답변 단계를 먼저 열지 않음). `MediaReferenceAdapter`의 질문 답변 첨부 확인, `ValidateMediaReferencesService`·`LoadMediaAttachmentsService`·`GetAttachedMediaService`(`MediaAsset.requireAttached`)의 `purpose`별 검사, `QuestionMediaAdapter`가 모든 호출에 `QUESTION_ANSWER_ATTACHMENT`를 넘기는 것은 이미 구현되어 있다. 남은 일은 위 media 잠금 계약(`previous`는 업로더 검사 제외)을 question의 삭제·수정 경로에 쓰는 것이다 (고침 #133: 이미 구현된 용도별 검사를 할 일에서 뺌)
     - 기존 시나리오 고침: #31-S12의 VIEWER·다른 작성자 변경 문구 끝에 `(고침 #133: 역할은 OWNER·FAMILY 둘, 소유주의 삭제는 허용)`
   - pet: 관리 동작은 소유주만 그대로(S9)
5. 소유주 이전 — S13~S17, S20
   - pet: 새 입력 포트 `ChangePetOwnerUseCase`·`ChangePetOwnerCommand(petId, currentOwnerId, newOwnerId)`. 소유주가 `currentOwnerId`가 아니면 409(`StateConflictException` 상속)를 던지는 방어 확인을 둔다. 새 소유주 안에서 태그가 겹치면 `PetTagGenerator`로 다시 뽑는다. pet 담당과 합의
   - pet은 소유권을 확인하고 잠근 반려동물 행에서 읽은 기존 프로필 사진을 `previous`로 따로 넘긴다. 이전받은 소유주도 사진을 교체·제거할 수 있고, 기존 삭제 조건을 충족하면 사진이 연결된 반려동물도 삭제할 수 있다. 새로 연결하는 사진은 요청자가 업로드한 자산이어야 한다. (고침 #133: 이전 후에도 기존 프로필 사진 관리가 가능하도록 연결의 출처를 구분)
   - HTTP 경계는 `FamilyController`가 맡는다. family 서비스가 한 트랜잭션에서 반려동물 행 잠금 → 요청자가 소유주인지 확인(아니면 403, 대상 조회 전) → 대상의 멤버십 삭제(한 문장, 0줄이면 400) → pet 소유주 변경 → 이전 소유주의 멤버십 저장을 한다. 어디서 실패해도 전부 롤백된다
6. 반려동물 삭제 — S21, S22
   - pet: `DeletePetService`가 반려동물 행을 `FOR UPDATE`로 읽고 소유주를 확인한 뒤, 새 출력 포트 `PetFamilyPort`로 가족 이어받기를 묻는다
     - 가족이 있으면: 이어받을 회원 id를 받아 소유주를 바꾸고(태그가 겹치면 다시 뽑음) 끝낸다. 기록 검사와 삭제는 하지 않는다
     - 가족이 없으면: 지금처럼 기록이 있으면 409, 없으면 P의 초대 코드를 지우고 반려동물을 삭제한다
   - `PetFamilyPort`의 어댑터는 `pet/adapter/out/family`에 두고 family 입력 포트를 부른다(`DiaryPetRecordsAdapter`와 같은 방식). family의 새 입력 포트 `HandOverPetUseCase`는 가장 먼저 가족이 된 회원(`joined_at`이 가장 이른 행, 같으면 `id`가 작은 행)의 멤버십을 지우고, 이전 소유주가 발급한 코드를 지운 뒤 그 회원 id를 돌려준다. 회원 탈퇴의 자동 이어받기도 이 포트를 쓴다

## 정한 것
- 역할은 소유주(`OWNER`)와 가족(`FAMILY`) 둘뿐이다. pet·프론트 문서의 EDITOR·VIEWER는 쓰지 않는다.
- 가족은 다이어리·질문 답변을 보고 쓰며, 본인 글만 수정·삭제한다.
- 소유주는 본인 반려동물의 다른 사람 글을 삭제할 수 있고 수정은 할 수 없다. 첨부가 있어도 삭제할 수 있다. 화면은 삭제 전에 한 번 더 확인한다.
- 커뮤니티 글은 이 권한에 들어가지 않는다.
- 초대는 초대 코드로 한다. 소유주와 가족 모두 코드를 만들 수 있다.
- 발급한 회원이 나가거나 내보내지면, 그 회원이 만든 쓰지 않은 코드도 함께 쓸 수 없게 된다.
- 다른 가족을 내보내는 것은 소유주만 한다. 가족은 스스로 나가기만 할 수 있다.
- 반려동물 정보 수정·모드 변경·삭제는 소유주만 한다.
- 가족이 있는 반려동물을 소유주가 삭제하면 반려동물은 남고, 가장 먼저 가족이 된 회원이 소유주가 되며 이전 소유주는 나간다. 회원 탈퇴에서 양도하지 않았을 때와 같은 규칙이다. 가족이 없으면 지금처럼 삭제한다.
- 회원 탈퇴(팀 합의, 탈퇴 이슈에서 구현): 탈퇴한 회원의 데이터는 30일 보관한 뒤 영구 삭제한다.
- 회원 탈퇴(팀 합의): 탈퇴하는 회원이 소유주면 소유주임을 알리고 양도를 안내한다.
- 회원 탈퇴(팀 합의): 양도하지 않고 탈퇴하면 가장 먼저 가족이 된 회원이 소유주를 이어받는다. 가족이 없으면 반려동물 데이터도 30일 뒤 함께 삭제한다.
- 소유주 이전 때 새 소유주에게 같은 태그의 반려동물이 있으면, 넘겨받는 반려동물의 태그를 새로 뽑는다.
- 가족 상태를 바꾸는 요청(코드 발급, 가입, 나가기, 내보내기, 이전, 반려동물 삭제)은 모두 반려동물 행을 먼저 잠그고, 소유주·가족 확인은 잠금 뒤에 한다.
- 나간 가족의 글은 우선 남기고 추후 결정한다.
- 가족 수 제한은 없다.
- 가족 API는 `/api/v1/families`에 두고 반려동물은 `petId` 입력으로 받는다.

## 가정
- 초대 코드는 헷갈리는 글자(0·O·1·I 등)를 뺀 8자리이고, 24시간 동안 한 번만 쓸 수 있다. DB에는 코드 해시만 저장한다.
- 코드 해시는 `JWT_SECRET`에서 뽑은 초대 코드 전용 키로 HMAC-SHA256을 쓴다. DB만으로는 코드를 되찾을 수 없게 하기 위해서다.
- 만료된 코드는 같은 반려동물의 코드를 새로 발급할 때 지운다. 발급 횟수 제한은 아직 두지 않는다.
- 쓸 수 없는 코드(없음·만료·이미 사용)는 모두 404로 같게 응답한다. 어떤 코드가 있었는지 드러내지 않기 위해서다.
- 코드 소비는 조회와 삭제를 나누지 않고 한 문장으로 한다(동시 사용, S5). 소비와 멤버십 저장은 한 트랜잭션이다.
- 소유주는 계속 pet의 `owner_member_id`에 두고, 가족만 `pet_family_members`에 저장한다. 두 테이블이 겹치지 않는 것은 DB 제약이 아니라 반려동물 행 잠금으로 지킨다.
- 가족 목록은 닉네임과 계정 태그로 회원을 구분한다(계정 태그 #137이 먼저 들어간다).
- 접근이 없는 회원이 반려동물 하위 자원을 부르면 지금처럼 403이다. 404로 바꿀지는 diary 문서의 제안과 함께 각 도메인이 정한다.
- 이전 중 새 소유주의 반려동물 등록이 동시에 같은 태그로 들어오면 태그 유니크 제약으로 전체가 롤백되고 409다. 드물어서 재시도하지 않는다.
- 같은 반려동물의 가족 변경 요청은 한 줄로 서지만, 가족 변경은 드물어서 대기는 문제가 되지 않는다. 읽기(역할 확인, 목록)는 잠그지 않는다.

## 참고
- diary(`OwnerDiaryPetAccessAdapter`), question(`OwnerQuestionPetAccessAdapter`), media(`OwnerMediaPetAccessAdapter`)는 pet 소유 확인을 출력 어댑터로 분리해 두었다. 다만 diary 삭제의 작성자 검사(`requireAuthor`)와 media의 업로더 검사(`ChangeMediaReferencesService.lock`)는 어댑터 밖에 있어 4단계에서 함께 바꾼다.
- `LockMediaReferencesCommand`는 `assetIds`와 `purpose`만 받아, `ChangeMediaReferencesService.lock`이 유지·해제할 첨부까지 업로더가 요청자인지 검사한다. 그래서 소유주가 다른 작성자의 첨부를 해제하려면 4단계의 잠금 계약 변경이 필요하다. `MediaReferenceAdapter`는 다이어리와 질문 답변 첨부를 모두 확인한다.
- diary 문서는 "다른 작성자 변경 403"을 정해 두었다. 소유주 삭제는 이 규칙의 예외다(Q2).
- pet은 기록이 있으면 삭제를 막는다(`PetHasRecordsException`, 409). 기록 확인은 출력 포트 `HasPetRecordsPort`와 `pet/adapter/out/records/DiaryPetRecordsAdapter`로 한다.
- pet 테이블은 `(owner_member_id, tag)`가 유일하다(`uk_pets_owner_tag`). 소유주가 바뀌면 이 조합도 바뀐다.
- PostgreSQL READ COMMITTED에서 다른 트랜잭션이 잠근 행을 `FOR UPDATE`로 읽으려는 문장은 그 트랜잭션이 끝날 때까지 기다리고, 그 뒤에는 커밋된 최신 값을 읽는다(S17, S18, S20, S22).
- 역할은 이 계획대로 두 역할(`OWNER`·`FAMILY`)이다. pet·media 문서에 남아 있던 `EDITOR`·`VIEWER` 표현도 두 역할로 맞췄다.
