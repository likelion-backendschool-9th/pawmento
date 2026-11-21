# 회원 탈퇴

관련 이슈: [#155](https://github.com/likelion-backendschool-9th/pawmento/issues/155)

## 한눈에
- 끝나면: 회원이 탈퇴하면 바로 로그인이 풀리고 탈퇴 전 토큰도 다시 쓸 수 없다. 30일 안에 다시 로그인하면 "복구하시겠습니까?"로 되살릴 수 있고, 30일이 지나면 스케줄러가 가족 없는 반려동물의 기록과 회원 정보를 지우고 제공자 연결도 끊는다.
- 결정 필요: 없음 (Q1의 question 구현 방식은 question 담당과 맞춤)
- 규모: 7단계

## 질문
- Q1 (정함): 작성자 id는 하드 삭제 뒤에도 그대로 둔다. community·diary·question 모두 작성자 컬럼이 `member`를 FK로 참조하지 않아 회원을 지워도 막히지 않고, 세 도메인 모두 작성자 id가 비어 있으면 불러오지 못하기 때문이다. 질문 답변은 `author_name_snapshot`에 이름을 따로 저장하므로, 하드 삭제 때 question의 정리 입력 포트로 그 회원 답변의 스냅샷만 "탈퇴한 회원입니다"로 바꾸고 답변 본문·등록일·질문 스냅샷·가족 공통 진행은 보존한다. 스냅샷 필드가 지금은 바꿀 수 없게(`final`) 되어 있어 question에 익명화 입력 포트를 새로 두는 방식과 `updated_at`·`version` 처리는 question 담당과 맞춘다. (고침 #155: 리뷰 — 작성자 FK가 없고, NULL로 바꾸면 도메인이 기록을 복원하지 못함)
- Q2 (정함): 보관 중(30일)에도 작성자를 "탈퇴한 회원입니다"로 보여 준다. 보관 중에는 데이터를 바꾸지 않고 표시할 때 작성자가 탈퇴 대기인지 확인한다(R7). 그래서 복구하면 원래 이름이 그대로 돌아온다. 질문 답변 스냅샷은 하드 삭제 때 한 번만 바꾼다.
- Q3 (정함): 가족이 없는 반려동물의 데이터는 탈퇴 30일 뒤 회원과 함께 지운다([가족 계정의 정한 것](../family/membership.md), PR #136 리뷰 합의). 보관 중에는 그대로 두어 복구하면 그대로 돌아온다. (고침 #155: 리뷰 — 기존 합의에 맞춤)
- Q4 (정함): 다른 회원의 반려동물에 가족으로 참여 중인 회원이 탈퇴하면 그 가족 목록에서 바로 빠진다. 복구해도 돌아오지 않고 다시 초대받아야 한다. 그래서 탈퇴 대기 중인 회원은 양도 후보가 될 수 없다.

## 1. 탈퇴 요청 (#155-R1)
로그인한 회원이 탈퇴하면 탈퇴 대기 상태가 되고, 탈퇴 전에 발급된 모든 토큰이 무효가 된다.

#155-S1 정상 [테스트]
- Given 이메일 회원 A가 로그인했다(소유한 반려동물 없음, `auth_version` 1)
- When  `DELETE /api/v1/members/me`에 `Content-Type: application/json`으로 보낸다
- Then  204이고 `ACCESS_TOKEN` 쿠키를 지우는 `Set-Cookie`가 있다
- Then  `member`의 `status`가 `WITHDRAWN`, `withdrawn_at`이 요청 시각, `auth_version`이 2다
- Then  `member`·`social_account` 행은 그대로 남아 있다

#155-S2 탈퇴 전 토큰 거부 [테스트]
- Given A가 탈퇴하기 전에 받은 `ACCESS_TOKEN`(만료 전, `ver` 1)
- When  그 쿠키로 `GET /api/v1/members/me`를 부른다
- Then  401 `UNAUTHORIZED`다
- 복구한 뒤에도 같은 결과다(#155-S11) (고침 #155: 리뷰 — 상태만 보면 복구 뒤 이전 토큰이 다시 통과함)

#155-S3 로그인 안 함·형식 [테스트]
- 쿠키 없이 부르면 401
- `Content-Type: application/json` 없이 부르면 415이고 상태가 바뀌지 않는다

#155-S21 다른 반려동물의 가족에서 빠짐 (Q4) [테스트]
- Given A가 회원 E의 반려동물 하루의 가족이다
- When  A가 탈퇴한다
- Then  하루의 가족 목록에 A가 없다
- Then  A가 발급하고 쓰지 않은 하루의 초대 코드는 404이고, E가 발급한 코드는 그대로 쓸 수 있다
- Then  A가 복구해도(#155-S11) 하루의 가족 목록에 A가 없다

## 2. 반려동물 소유권 넘기기 (#155-R2)
소유한 반려동물에 가족이 있으면 탈퇴할 때 소유권을 넘긴다. 탈퇴하는 소유주는 가족으로도 남지 않고, 복구해도 돌려받지 않는다.

탈퇴용 양도는 #133의 소유주 이전과 다르다. #133의 명시적 이전(`POST /api/v1/families/owner-transfers`)은 이전 소유주를 `FAMILY`로 남기지만, 탈퇴용 양도는 이전 소유주를 구성원에서 뺀다. 그래서 같은 입력 포트를 쓰지 않는다.
- 고른 가족에게: family의 새 입력 포트 `HandOverPetOnWithdrawalUseCase(petId, toMemberId)`. 이전 소유주를 구성원에서 빼고, 넘겨받는 회원의 가족 멤버십을 지우고 소유주로 바꾼다
- 고르지 않으면: #133의 `HandOverPetUseCase`(가장 먼저 가족이 된 회원이 이어받음)를 그대로 쓴다. 반려동물 삭제의 이어받기와 같은 규칙이다
- 넘겨받는 회원에게 같은 태그의 반려동물이 있으면 태그를 새로 뽑는다(#133 정한 것)
- 탈퇴자가 발급하고 아직 쓰지 않은 초대 코드(`pet_invite_codes`)는 같은 트랜잭션에서 지운다. 고른 가족에게 넘길 때, 자동으로 이어받을 때, 넘길 가족이 없어 반려동물을 보관할 때(S5), 다른 반려동물의 가족에서 빠질 때(S21) 모두 같다. 다른 구성원이 발급한 코드는 그대로 둔다. #133처럼 반려동물 행을 잠근 뒤 멤버십 변경과 코드 삭제를 함께 한다 (고침 #155: 리뷰 — 발급자가 빠지면 그 코드를 쓸 수 없다는 #133 규칙)

#155-S4 고른 가족에게 넘김 [테스트]
- Given A가 반려동물 보리의 소유자이고, 가족 B·C가 있다
- When  `DELETE /api/v1/members/me`에 `{"transfers":[{"petId":1,"toMemberId":"C의 id"}]}`를 보낸다
- Then  204이고 보리의 소유자가 C다
- Then  A는 보리의 구성원이 아니고, B는 그대로 가족이다
- Then  A가 발급하고 쓰지 않은 보리의 초대 코드는 404이고, B가 발급한 코드는 그대로 쓸 수 있다

#155-S5 넘길 가족이 없음 (Q3) [테스트]
- Given A가 반려동물 콩이의 소유자이고 가족이 없으며, 콩이의 다이어리 2개가 있다
- When  탈퇴한다
- Then  204이고 콩이의 소유자는 그대로 A, 다이어리 2개도 그대로 있다
- Then  A가 발급하고 쓰지 않은 콩이의 초대 코드는 404다(보관 중인 반려동물에 새 가족이 들어오지 않는다)
- When  10일 뒤 복구한다(#155-S11)
- Then  콩이와 다이어리 2개를 그대로 볼 수 있다
- 30일이 지난 뒤의 삭제는 #155-S20에서 확인한다

#155-S6 잘못 고름 [테스트]
- Given 보리의 가족이 아닌 회원 D를 넘길 대상으로 보낸다
- Then  400 `VALIDATION`이고 A의 상태와 보리의 소유자가 그대로다

#155-S7 고르지 않음 [테스트]
- Given A가 보리의 소유자이고 가족 B(먼저 가족이 됨)·C가 있다
- When  `transfers` 없이 탈퇴한다
- Then  보리의 소유자는 가장 먼저 가족이 된 B다

#155-S19 여러 반려동물 중 하나 실패 [테스트]
- Given A가 보리(가족 C)와 하늘(가족 F)의 소유자다
- When  `{"transfers":[{"petId":"보리","toMemberId":"C"},{"petId":"하늘","toMemberId":"D(가족 아님)"}]}`로 탈퇴한다
- Then  400이고 보리의 소유자는 그대로 A, C는 그대로 가족이다
- Then  A의 `status`·`auth_version`과 다른 반려동물의 가족 멤버십(#155-S21)도 바뀌지 않는다(한 트랜잭션으로 함께 롤백)
- (고침 #155: 리뷰 — #133 이전과 포트 계약 구분, 여러 양도의 원자성)

## 3. 보관 중 로그인 → 복구 안내 (#155-R3)
마감(`withdrawn_at + 30일`) 전에 본인 확인(비밀번호·소셜 인증)을 마치면, 로그인 대신 복구를 물어본다.

복구 안내는 예외로 만들지 않는다. 공통 `GlobalExceptionHandler`는 상태 충돌을 `CONFLICT`로만 응답하고 쿠키를 붙이지 못하기 때문이다. 로그인 입력 포트(`LoginMemberUseCase`, `SocialLoginMemberUseCase`)의 결과(`LoginMemberResult`)가 "로그인됨"과 "복구 필요(복구 토큰 포함)"를 구분해 돌려주고, `AuthController`가 "복구 필요"면 409 본문 `{"code":"WITHDRAWAL_PENDING","message":"…"}`과 `MEMBER_RESTORE` 쿠키를 함께 만든다. (고침 #155: 리뷰 — 에러 코드 추가만으로는 409와 쿠키가 함께 내려가지 않음)

#155-S8 이메일 로그인 [테스트]
- Given A가 탈퇴한 지 10일 지났다
- When  `POST /api/v1/auth/login`에 A의 이메일과 맞는 비밀번호를 보낸다
- Then  409 `WITHDRAWAL_PENDING`이고 `ACCESS_TOKEN` 쿠키가 없다
- Then  `MEMBER_RESTORE` 쿠키(HttpOnly, SameSite=Lax, HTTPS면 Secure, Path=`/api/v1/auth/restore`)를 받는다. 유효 시간은 10분과 마감까지 남은 시간 중 짧은 쪽이다

#155-S9 소셜 로그인 [테스트]
- Given 구글로만 가입한 회원 G가 탈퇴한 지 10일 지났다
- When  `POST /api/v1/auth/oauth/google`로 G의 구글 계정 인증을 마친다
- Then  S8과 같이 409 `WITHDRAWAL_PENDING`과 `MEMBER_RESTORE` 쿠키다(새 가입이 아니다)

#155-S10 비밀번호 틀림 [테스트]
- Given 탈퇴 대기 중인 A
- When  틀린 비밀번호로 로그인한다
- Then  401이고 `MEMBER_RESTORE` 쿠키가 없다(탈퇴 대기인지 알려 주지 않는다)

#155-S22 마감 뒤 로그인 [테스트]
- Given A가 탈퇴한 지 30일 1시간 지났고, 하드 삭제 작업은 아직 돌지 않았다
- When  맞는 비밀번호로 로그인하거나, A의 소셜 계정으로 소셜 로그인한다
- Then  401이고 `MEMBER_RESTORE` 쿠키가 없다(없는 계정과 같다)

## 4. 복구 (#155-R4)
복구 안내를 받은 브라우저에서 마감 전에 복구하면 탈퇴 전 상태로 돌아오고, 새 토큰으로 로그인된다.

#155-S11 정상 [테스트]
- Given S8로 `MEMBER_RESTORE` 쿠키를 받았다
- When  `POST /api/v1/auth/restore`에 `Content-Type: application/json`으로 보낸다
- Then  200과 회원 정보, 새 `ACCESS_TOKEN` 쿠키(`ver` 2)를 받는다
- Then  `status`가 `ACTIVE`, `withdrawn_at`이 비어 있다
- Then  새 토큰으로 `GET /api/v1/members/me`는 200, 탈퇴 전 토큰(`ver` 1)으로는 401이다
- Then  S4에서 넘긴 보리의 소유자는 그대로 C다

복구 토큰은 발급할 때의 탈퇴 세대에 묶는다. `member_restore_token`에 발급 시점의 `auth_version`을 함께 저장하고, 복구는 아래 조건을 모두 만족할 때만 한 번 성공한다. 복구하면 그 회원의 남은 복구 토큰을 모두 지우고, 다시 탈퇴하면 `auth_version`이 올라 이전 탈퇴의 토큰은 조건에 맞지 않는다. (고침 #155: 리뷰 — 재탈퇴 뒤 이전 복구 토큰 재사용)
- 토큰이 있고 만료 전이며 지금 소비된다(`social_link_state`와 같은 원자적 삭제)
- 토큰의 `auth_version`이 회원의 현재 `auth_version`과 같다
- 회원이 `WITHDRAWN`이고 마감 전이다. 상태 변경은 `status = 'WITHDRAWN' AND auth_version = ?` 조건부 갱신으로 하고, 바뀐 행이 1개일 때만 성공이다

#155-S12 복구 확인 실패 [테스트]
- When  쿠키 없이, 만료된 쿠키로, 이미 한 번 쓴 쿠키로 각각 복구한다
- Then  모두 403 `FORBIDDEN`이고 상태가 바뀌지 않는다
- Given A가 탈퇴한 뒤 브라우저 1·2에서 로그인해 복구 토큰 T1·T2를 받았다
- When  T1으로 복구하고, 10분 안에 다시 탈퇴한 뒤, T2로 복구한다
- Then  T2는 403이고 A는 `WITHDRAWN` 그대로다
- Given 같은 탈퇴에서 받은 T1·T2로 복구 요청이 동시에 온다
- Then  하나만 200이고 다른 하나는 403이며, 복구는 한 번만 일어난다(`ACCESS_TOKEN` 발급도 한 번)

#155-S18 마감 뒤 복구 [테스트]
- Given A가 마감 5분 전에 `MEMBER_RESTORE` 쿠키를 받았다
- When  마감이 지난 뒤 그 쿠키로 복구한다
- Then  하드 삭제 작업이 아직 돌지 않았어도 403이고 상태가 바뀌지 않는다
- Given 마감 직전에 복구 요청과 하드 삭제 작업이 동시에 온다
- Then  복구가 먼저 끝나면 A는 지워지지 않는다. 삭제 작업은 회원 행을 잠근 뒤 `status`와 마감을 다시 확인한다
- (고침 #155: 리뷰 — 마감은 스케줄러가 아니라 복구 시점에 확인)

## 5. 보관 중 가입 막기 (#155-R5)
보관 중인 회원의 이메일로는 새로 가입하지 않고 복구로 안내한다.

#155-S13 같은 이메일 [테스트]
- Given 탈퇴 대기 중인 A(`a@pawmento.dev`)
- When  `POST /api/v1/auth/signup`에 `a@pawmento.dev`로 가입한다
- Then  409 `CONFLICT`이고 메시지에 "로그인하면 복구할 수 있어요" 안내가 있다
- 같은 이메일의 다른 제공자 계정으로 처음 소셜 로그인해도 같은 409다

## 6. 하드 삭제 (#155-R6)
마감이 지난 회원을 스케줄러가 지운다. 가족 없는 반려동물과 그 기록, 질문 답변의 이름 스냅샷도 함께 정리하고, 제공자 연결을 끊는다.

한 회원의 삭제 순서
1. 회원 행을 잠그고 `status`가 `WITHDRAWN`이며 마감이 지났는지 다시 확인한다. 아니면 건너뛴다
2. 그 회원이 아직 소유한 반려동물(가족이 없어 넘기지 못한 것)을 정리한다. 기존 `DeletePetService`는 기록이 있으면 409이므로 쓰지 않는다. 각 도메인의 새 정리 입력 포트가 그 반려동물의 데이터를 지운다
   - diary: 다이어리와 첨부 연결, 위치(지도 핀)
   - question: 답변과 답변 첨부 연결, 배정된 질문(`question_prompt`), 진행 위치(`question_progress`)
   - map: 산책 기록
   - 첨부 연결을 지울 때 media의 기존 계약을 그대로 쓴다. media 입력 포트로 참조 수를 줄이고, 마지막 참조가 사라진 자산은 `DELETING`으로 표시하고 삭제 작업을 등록한다. 자산·삭제 작업 행은 지우지 않고, 파일은 이 트랜잭션에서 지우지 않는다([media](../media/README.md))
   - 마지막으로 반려동물 행을 지운다(다이어리·질문 답변·질문 배정이 `pets`를 `ON DELETE RESTRICT`로 참조하므로 마지막이다)
3. 다른 반려동물에 남은 그 회원의 질문 답변은 `author_name_snapshot`만 "탈퇴한 회원입니다"로 바꾼다(Q1). 답변 본문·등록일·질문 스냅샷·가족 공통 진행은 그대로 둔다. 다이어리·커뮤니티 글은 작성자 id를 그대로 둔다
   - 그 회원이 올린 파일(`uploader_member_id`)이라도 다른 반려동물의 기록에 연결돼 있으면 지우지 않는다. 미디어 정리는 2의 반려동물 기록에 연결된 것만 대상이다
4. 연결 끊기 대상(제공자, 제공자 사용자 id, 암호화 토큰)을 `social_unlink_retry`에 시도 0회로 옮겨 적는다
5. `member_restore_token`·`social_account`·`member`를 지운다
6. 1~5를 한 트랜잭션으로 커밋한 뒤, 커밋 밖에서 `social_unlink_retry`의 행마다 제공자 연결 끊기를 부른다. 제공자 HTTP를 DB 트랜잭션 안에서 부르지 않기 위해서다(#109와 같은 방식)
   - 제공자 연결은 우리 회원이 아니라 제공자 계정 단위라, 같은 제공자 계정으로 새로 가입한 회원이 있으면 끊으면 안 된다. 그래서 끊기를 부르기 직전에 그 `(provider, provider_user_id)`가 `social_account`에 있는지 확인하고, 있으면 부르지 않고 그 재시도 행을 지운다
   - 처음 소셜 가입·연결(`social_account` 저장)은 같은 트랜잭션에서 그 `(provider, provider_user_id)`의 `social_unlink_retry` 행을 지운다. 새 회원이 생기면 예전 탈퇴의 끊기는 더 하지 않는다 (고침 #155: 리뷰 — 해제 대기 중 재가입한 회원의 연결이 끊기는 문제)
7. `DELETING` 자산의 실제 파일은 커밋 뒤 기존 media 작업자가 지우고, 저장소 장애는 media 규칙대로 다시 시도한다 (고침 #155: 리뷰 — 미디어는 참조 해제·삭제 예약과 비동기 파일 삭제로 나눔)

#155-S14 정상 (Q1) [테스트]
- Given A가 탈퇴한 지 31일 지났고, 카카오가 연결돼 있다. A는 커뮤니티 글 1개와, 회원 E의 반려동물 하루에 다이어리 1개(A가 올린 사진 1장 첨부)·질문 답변 1개를 남겼다
- When  하드 삭제 작업을 실행한다
- Then  카카오 연결 끊기 포트가 A의 카카오 id로 한 번 불린다
- Then  `member`·`social_account`·`social_unlink_retry`에 A가 없다
- Then  A의 글은 `GET /api/v1/posts/{id}`로 200이고 작성자 id는 그대로다
- Then  하루의 다이어리 목록에서 A의 다이어리를 계속 조회할 수 있고, 첨부 사진은 `READY`이며 참조 수가 그대로다(올린 사람이 A여도 지우지 않는다)
- Then  A의 질문 답변 `author_name_snapshot`이 "탈퇴한 회원입니다"이고, 답변 본문·등록일·질문 스냅샷과 하루의 현재 질문 진행은 그대로다
- (고침 #155: 리뷰 — 작성자를 비우지 않고 기존 기록 조회를 확인)

#155-S15 아직 마감 전 [테스트]
- Given B가 탈퇴한 지 29일 지났다
- When  하드 삭제 작업을 실행한다
- Then  B는 그대로 남아 있다

#155-S20 가족 없는 반려동물 DB 정리 (Q3) [테스트]
- Given #155-S5의 A가 복구하지 않고 31일이 지났다(콩이에 다이어리 2개·첨부 사진 1개·질문 배정 3회·답변 1개·진행 위치·산책 1개)
- When  하드 삭제 작업을 실행한다
- Then  콩이와 그 다이어리·첨부 연결·질문 배정·답변·진행 위치·산책·지도 핀이 모두 없다
- Then  첨부 사진 자산은 `DELETING`이고 삭제 작업이 하나 등록돼 있다(자산·작업 행은 남아 있다)
- Then  다른 회원의 반려동물과 기록, 자산은 그대로다
- Given 정리 중 하나(예: 질문 진행 삭제)가 실패한다
- Then  A의 삭제 전체가 롤백된다. A와 콩이·기록이 그대로이고, 사진 자산은 `READY`에 참조 수가 그대로이며 삭제 작업이 없다. 다음 작업에서 다시 시도한다

#155-S23 커밋 뒤 파일 삭제 [테스트: media #33-S9]
- Given S20이 커밋돼 사진 자산이 `DELETING`이다
- When  media 작업자가 삭제 작업을 처리한다
- Then  최종 파일·썸네일이 지워지고 자산이 `DELETED`다
- Given 저장소 장애로 파일 삭제가 실패한다
- Then  삭제 작업이 다시 시도되고, 회원·반려동물 삭제는 되돌리지 않는다. 이미 없는 파일은 성공으로 본다

#155-S16 끊기 실패 [테스트]
- Given A의 카카오 연결 끊기가 실패한다
- When  하드 삭제 작업을 실행한다
- Then  A는 지워지고, `social_unlink_retry`에 `(KAKAO, A의 카카오 id, 시도 1회)`가 남는다
- When  다음 날 작업에서 끊기가 성공한다
- Then  `social_unlink_retry`에서 그 행이 없어진다
- Given 3회 모두 실패한다
- Then  행을 지우고 에러 로그를 남긴다
- Given A의 카카오 끊기가 실패해 재시도 행이 남은 상태에서, 같은 카카오 계정으로 처음 소셜 로그인해 새 회원 N이 가입했다
- Then  가입과 함께 그 재시도 행이 없어지고, 다음 작업에서 카카오 연결 끊기 포트는 불리지 않는다
- Then  N은 같은 카카오 계정으로 계속 로그인된다
- Given 재시도 행이 남아 있고 그 카카오 계정이 이미 `social_account`에 있다(가입과 작업이 엇갈림)
- Then  작업은 끊기를 부르지 않고 그 행을 지운다

#155-S17 이미 끊긴 연결 [테스트]
- Given 사용자가 제공자 쪽에서 이미 연결을 끊었다(카카오 "연결되지 않은 사용자", 구글 `invalid_token`)
- Then  성공으로 보고 `social_unlink_retry`에서 지운다

## 7. 작성자 표시 (#155-R7)
탈퇴 대기 중이거나 삭제된 회원이 남긴 글·기록은 작성자를 "탈퇴한 회원입니다"로 보여 준다.

member가 입력 포트 `FindWithdrawnMembersUseCase(memberIds)`를 연다. 회원 id 목록을 받아 탈퇴 대기(`WITHDRAWN`)이거나 이미 없는 회원 id를 돌려준다. community·diary·question은 목록·상세 응답을 만들 때 작성자 id를 한 번에 물어보고, 해당하면 작성자 표시 이름을 "탈퇴한 회원입니다"로 내린다. 저장된 데이터(작성자 id, 질문 답변 스냅샷)는 보관 중에 바꾸지 않는다.

#155-S24 보관 중 표시 (Q2) [테스트]
- Given A가 커뮤니티 글 1개, 회원 E의 반려동물 하루에 다이어리 1개·질문 답변 1개를 남기고 탈퇴했다(10일 지남)
- When  다른 회원이 글 상세를, E가 하루의 다이어리 목록과 질문 답변을 조회한다
- Then  세 응답 모두 작성자 표시 이름이 "탈퇴한 회원입니다"다
- Then  `question_answer.author_name_snapshot`은 원래 이름 그대로다

#155-S25 복구 후 표시 [테스트]
- Given S24의 A가 복구했다(#155-S11)
- When  같은 글·다이어리·답변을 조회한다
- Then  작성자 표시 이름이 탈퇴 전과 같다(질문 답변은 원래 스냅샷 이름)

#155-S26 하드 삭제 후 표시 (Q1) [테스트]
- Given A가 하드 삭제됐다(#155-S14)
- When  같은 글·다이어리·답변을 조회한다
- Then  작성자 표시 이름이 "탈퇴한 회원입니다"다(회원이 없어 확인 포트가 A를 돌려줌, 답변은 익명화된 스냅샷)

## 범위 밖
- 탈퇴 확인 화면, 복구 안내 팝업, "탈퇴한 회원입니다" 표시: 프론트엔드 이슈
- 네이버 연결 끊기: 네이버 로그인(#40-S12) 때 함께 다룬다
- 탈퇴 사유 수집, 관리자 강제 탈퇴
- 로그아웃한 토큰의 즉시 폐기: 이번 `auth_version`으로 할 수 있지만, refresh 토큰과 함께 member 후속 이슈에서 정한다

## 작업 순서
1. 탈퇴 상태와 탈퇴 API, 토큰 세대 — S1~S3, S21
   - 파일: `member/domain/Member.java`(`status`, `withdrawnAt`, `authVersion`, `withdraw()`·`restore()`), `MemberJpaEntity.java`, 새 `WithdrawMemberUseCase`·서비스, `MemberController.java`, `JwtTokenIssuer.java`(`ver` 클레임), 토큰 검증 뒤 `ver`와 회원의 `auth_version`을 비교하는 곳(`global/security`), family의 회원 멤버십 제거 입력 포트, `docs/member/README.md`(토큰 클레임 정한 것에 `(고침 #155: 탈퇴 전 토큰 폐기)`)
   - 확인: `./gradlew test --tests '*WithdrawalScenarioTest' --tests '*AuthScenarioTest'`
2. 소유권 넘기기 — S4~S7, S19
   - 파일: pet의 새 출력 포트 `WithdrawalPetHandOverPort`와 family 어댑터(`pet/adapter/out/family`), family의 새 `HandOverPetOnWithdrawalUseCase`, 기존 `HandOverPetUseCase` 재사용, 탈퇴자가 발급한 `pet_invite_codes` 삭제
   - 확인: `./gradlew test --tests '*WithdrawalScenarioTest'`
3. 복구 안내와 복구 — S8~S12, S18(복구 쪽), S22
   - 파일: `LoginMemberResult`(로그인됨·복구 필요), `LoginMemberService.java`, `SocialLoginMemberService.java`, 새 `member_restore_token` 저장(`social_link_state`와 같은 원자적 소비, `expires_at`은 10분과 마감 중 이른 쪽, 발급 시 `auth_version` 저장, 복구 때 회원의 토큰 전부 삭제), 새 `RestoreMemberUseCase`·서비스, `AuthController.java`(409 본문과 `MEMBER_RESTORE` 쿠키), `PublicEndpoints.java`(`POST /api/v1/auth/restore`), `ErrorCode`에 `WITHDRAWAL_PENDING`
   - 확인: `./gradlew test --tests '*WithdrawalRestoreScenarioTest' --tests '*AuthScenarioTest' --tests '*SocialLoginScenarioTest'`
4. 보관 중 가입 막기 — S13
   - 파일: `SignUpMemberService.java`, `SocialLoginMemberService.java`
   - 확인: `./gradlew test --tests '*WithdrawalRestoreScenarioTest'`
5. 구글 토큰 저장
   - 파일: `social_account`에 `encrypted_refresh_token`, 구글 프로필 어댑터, 암호화 어댑터(키는 환경 변수 `SOCIAL_TOKEN_KEY`)
   - 확인: `./gradlew test --tests '*SocialLoginScenarioTest' --tests '*MemberPersistenceTest'`
6. 하드 삭제 스케줄러 — S14~S17, S18(삭제 쪽), S20, S23
   - 파일: 새 `HardDeleteWithdrawnMembersService`, `@Scheduled` 어댑터(`adapter/in/scheduler`), diary·question·map·pet의 반려동물 정리 입력 포트(첨부 연결 해제는 media의 기존 참조 감소·삭제 예약 입력 포트 재사용), question의 답변 이름 스냅샷 갱신 입력 포트(Q1), 새 `UnlinkSocialAccountPort`와 카카오·구글 어댑터, 새 `social_unlink_retry` 저장(소셜 가입·연결 저장 때 같은 계정의 행 삭제 포함). 파일 삭제는 기존 media 작업자를 그대로 쓴다
   - 확인: `./gradlew test --tests '*HardDeleteWithdrawnMembersTest'`
7. 작성자 표시 — S24~S26
   - 파일: member의 새 `FindWithdrawnMembersUseCase`·서비스, community·diary·question의 응답 조립(작성자 id를 한 번에 조회하는 출력 포트와 member 어댑터). 각 도메인 담당과 응답 필드 이름을 맞춘다
   - 확인: `./gradlew test --tests '*WithdrawalDisplayScenarioTest'`

## 정한 것
- 탈퇴하면 30일 동안 보관하고, 그 뒤 스케줄러가 하드 삭제한다.
- 보관 중 로그인(이메일·소셜)하면 "복구하시겠습니까?"를 띄운다. 복구하면 탈퇴 전 상태로 돌아간다. 복구는 본인 확인(비밀번호·소셜 인증)을 마친 뒤에만 할 수 있다.
- 마감(`withdrawn_at + 30일`)은 복구·로그인 시점에 직접 확인한다. 마감이 지나면 하드 삭제 전이라도 로그인은 401, 복구는 403이다. (고침 #155: 리뷰)
- 보관 중에는 같은 이메일·같은 소셜 계정으로 새로 가입할 수 없다. 재가입이 아니라 복구로 안내한다.
- 탈퇴하면 `ACCESS_TOKEN` 쿠키를 지우고 `auth_version`을 올린다. 토큰의 `ver`가 회원의 `auth_version`과 다르면 거부하므로, 탈퇴 전 토큰은 복구한 뒤에도 쓸 수 없다. (고침 #155: 리뷰)
- 소유한 반려동물은 탈퇴할 때 가족에게 넘긴다. 넘길 대상은 소유자가 고르고, 고르지 않으면 가장 먼저 가족이 된 회원이 이어받는다. 탈퇴하는 소유주는 가족으로도 남지 않고, 복구해도 소유권을 돌려받지 않는다.
- 가족이 없는 반려동물의 데이터는 30일 뒤 함께 지운다([가족 계정](../family/membership.md), PR #136 리뷰 합의).
- 다른 반려동물의 가족으로 참여 중이면 탈퇴할 때 바로 빠지고, 복구해도 돌아오지 않는다.
- 탈퇴 대기 중이거나 삭제된 회원의 글·기록은 작성자를 "탈퇴한 회원입니다"로 보여 준다. 보관 중에는 표시할 때 확인하고 데이터는 바꾸지 않는다(Q2).
- 작성자 id는 하드 삭제 뒤에도 그대로 둔다. 질문 답변의 이름 스냅샷만 "탈퇴한 회원입니다"로 바꾸고, 답변 본문·등록일·질문 스냅샷·가족 공통 진행은 보존한다(Q1).
- 반려동물 정리의 미디어는 기존 media 계약을 따른다. 첨부 연결 삭제·참조 감소·`DELETING` 표시·삭제 작업 등록은 회원 삭제와 같은 DB 트랜잭션에서, 실제 파일 삭제는 커밋 뒤 media 작업자가 재시도와 함께 한다. 탈퇴자가 올린 파일이라도 다른 반려동물의 기록에 연결된 것은 지우지 않는다. (고침 #155: 리뷰)
- 복구 토큰은 발급 시점의 탈퇴 세대(`auth_version`)에 묶고, 복구하면 그 회원의 복구 토큰을 모두 지운다. (고침 #155: 리뷰)
- 제공자 연결 끊기는 하드 삭제 때 한다. 보관 중에는 소셜로 복구해야 하기 때문이다.
- 연결 끊기가 실패해도 회원은 마감대로 지우고, 끊기만 `social_unlink_retry`로 하루 한 번, 최대 3회 다시 시도한다. 같은 제공자 계정으로 새 회원이 가입하면 그 재시도는 취소한다.
- 탈퇴하면 탈퇴자가 발급하고 쓰지 않은 초대 코드를 모두 지운다. 다른 구성원의 코드는 유지한다.
- 구글은 연결 끊기(revoke)에 토큰이 필요하므로 refresh 토큰을 암호화해 저장한다.

## 가정
- 탈퇴는 로그인 상태에서 비밀번호를 다시 묻지 않고 한다. 실수 방지는 화면의 확인 팝업으로 한다.
- 탈퇴 경로는 `DELETE /api/v1/members/me`이고, 본문이 없어도 `Content-Type: application/json`을 요구한다(기존 인증 규칙).
- 하드 삭제 작업은 하루 한 번 새벽 4시(Asia/Seoul)에 돈다. 마감이 지난 뒤 처음 도는 작업에서 지운다.
- 마감이 지났지만 아직 지워지지 않은 회원의 이메일로 가입하면 이메일 유니크 때문에 409다. 길어야 다음 작업까지(하루 이내)다.
- 끊기 직전 확인과 실제 제공자 호출 사이에 같은 계정으로 가입하는 아주 짧은 틈은 남는다. 그때 끊기면 그 회원은 다음 로그인 때 제공자 동의를 한 번 더 하게 되고, `social_account`가 그대로라 같은 회원으로 로그인된다.
- 토큰 검증 뒤 회원의 `auth_version`을 한 번 조회한다. 요청마다 DB 조회가 하나 늘어난다.
- 탈퇴 요청(양도·가족 멤버십 제거·상태 변경)은 제공자 호출이 없으므로 탈퇴 유스케이스 하나의 트랜잭션으로 묶는다.
- 복구 토큰은 `link-state`처럼 해시만 DB에 두고 한 번만 쓸 수 있다.
- 복구 안내의 `WITHDRAWAL_PENDING`은 같은 이메일 충돌(`CONFLICT`)과 화면이 구별할 수 있도록 새로 둔다. 공통 에러 규칙([backend-exceptions.md](../backend-exceptions.md))의 코드 목록에 더한다.
- 보관 중에도 닉네임·태그는 그대로 유지한다.

## 참고
- 작성자 컬럼(`post.author_id`, `diary_entries.created_by`, `question_answer.author_member_id`)은 모두 NOT NULL이고 `member`를 FK로 참조하지 않는다. 도메인 객체도 작성자 id가 비어 있으면 만들거나 복원하지 못한다(`Post`, `DiaryEntry`).
- `diary_entries`·`question_answer`·`question_prompt`는 `pets`를, 다이어리·질문 답변 첨부는 `media_assets`를 `ON DELETE RESTRICT`로 참조한다. 반려동물 정리는 참조하는 쪽부터 지우고, 자산 행은 지우지 않는다(파일 키와 삭제 재시도 근거가 자산·작업 행에 있다).
- media는 파일 I/O를 DB 트랜잭션 밖에서 한다. 트랜잭션 안에서 파일을 지우면 뒤이은 DB 실패를 되돌릴 수 없다([media](../media/README.md)).
- 카카오는 어드민 키(`KAKAO_ADMIN_KEY`)와 회원번호로 연결 끊기를 할 수 있어 토큰을 저장하지 않아도 된다(`POST https://kapi.kakao.com/v1/user/unlink`, `target_id_type=user_id`). 붙이기 전에 카카오 문서로 다시 확인한다.
- 구글 revoke는 `POST https://oauth2.googleapis.com/revoke`에 토큰을 보낸다. 액세스 토큰은 1시간 뒤 만료돼 30일 뒤에는 쓸 수 없으므로 refresh 토큰이 필요하다.
- 구글은 인가 URL에 `access_type=offline`이 있어야 refresh 토큰을 주고, 보통 처음 동의할 때만 준다. 인가 URL은 프론트엔드가 만들므로 함께 바꿔야 한다. 이 변경 전에 가입한 구글 회원은 refresh 토큰이 없어 revoke를 건너뛴다.
