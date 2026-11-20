# 회원 탈퇴

관련 이슈: [#NNN](https://github.com/likelion-backendschool-9th/pawmento/issues/NNN)

## 한눈에
- 끝나면: 회원이 탈퇴하면 바로 로그인이 풀린다. 30일 안에 다시 로그인하면 "복구하시겠습니까?"로 되살릴 수 있고, 30일이 지나면 스케줄러가 회원과 연결 정보를 지우고 제공자 연결도 끊는다.
- 결정 필요: Q1·Q2(다른 도메인의 작성자 처리, 팀 합의), Q3·Q4(반려동물·가족 처리 시점)
- 규모: 6단계

## 질문
- Q1 (팀): 하드 삭제하면 `member` 행이 사라져 글·기록의 작성자 FK가 삭제를 막는다. community·diary·question의 작성자 컬럼을 nullable + `ON DELETE SET NULL`로 바꾸고, 작성자가 없으면 화면에 "탈퇴한 회원입니다"로 보여 주는 방식에 동의하는지. 각 도메인 담당의 스키마가 바뀐다. (답에 따라 #NNN-S14가 달라짐)
- Q2 (팀): 보관 중(30일)에도 작성자를 "탈퇴한 회원입니다"로 보여 줄지, 하드 삭제 뒤에만 그렇게 보여 줄지. 보관 중에도 바꾸면 각 도메인이 작성자 상태를 함께 조회해야 한다.
- Q3: 가족이 없는 반려동물의 데이터 삭제를 탈퇴 요청 때 할지, 하드 삭제 때 할지. 요청 때 지우면 복구해도 반려동물 기록이 없다. (제안: 하드 삭제 때. 보관 중에는 그대로 두고, 복구하면 그대로 돌아온다) (답에 따라 #NNN-S5가 달라짐)
- Q4: 다른 회원의 반려동물에 가족으로 참여 중인 회원이 탈퇴하면, 그 가족 목록에서 바로 빠질지(복구해도 다시 초대받아야 함), 보관 중에는 남겨 둘지.

## 1. 탈퇴 요청 (#NNN-R1)
로그인한 회원이 탈퇴하면 탈퇴 대기 상태가 되고 로그인이 풀린다.

#NNN-S1 정상 [테스트]
- Given 이메일 회원 A가 로그인했다(소유한 반려동물 없음)
- When  `DELETE /api/v1/members/me`에 `Content-Type: application/json`으로 보낸다
- Then  204이고 `ACCESS_TOKEN` 쿠키를 지우는 `Set-Cookie`가 있다
- Then  `member`의 `status`가 `WITHDRAWN`, `withdrawn_at`이 요청 시각이다
- Then  `member`·`social_account` 행은 그대로 남아 있다

#NNN-S2 기존 토큰 거부 [테스트]
- Given A가 탈퇴하기 전에 받은 `ACCESS_TOKEN`(만료 전)
- When  그 쿠키로 `GET /api/v1/members/me`를 부른다
- Then  401 `UNAUTHORIZED`다

#NNN-S3 로그인 안 함·형식 [테스트]
- 쿠키 없이 부르면 401
- `Content-Type: application/json` 없이 부르면 415이고 상태가 바뀌지 않는다

## 2. 반려동물 소유권 넘기기 (#NNN-R2)
소유한 반려동물이 있으면 탈퇴할 때 소유권을 가족에게 넘긴다. 복구해도 돌려받지 않는다.

#NNN-S4 고른 가족에게 넘김 [테스트]
- Given A가 반려동물 보리의 소유자이고, 가족 B·C가 있다
- When  `DELETE /api/v1/members/me`에 `{"transfers":[{"petId":1,"toMemberId":"C의 id"}]}`를 보낸다
- Then  204이고 보리의 소유자가 C다
- Then  A는 보리의 구성원이 아니다

#NNN-S5 넘길 가족이 없음 (Q3) [테스트]
- Given A가 반려동물 콩이의 소유자이고 가족이 없다
- When  탈퇴한다
- Then  204이고 콩이와 기록은 하드 삭제 때 함께 지워진다(Q3 제안안 기준)

#NNN-S6 잘못 고름 [테스트]
- Given 보리의 가족이 아닌 회원 D를 넘길 대상으로 보낸다
- Then  400 `VALIDATION`이고 A의 상태와 보리의 소유자가 그대로다

#NNN-S7 고르지 않음 [테스트]
- Given A가 보리의 소유자이고 가족 B(먼저 가입)·C가 있다
- When  `transfers` 없이 탈퇴한다
- Then  보리의 소유자는 가장 먼저 연결된 가족 B다

## 3. 보관 중 로그인 → 복구 안내 (#NNN-R3)
30일 안에 본인 확인(비밀번호·소셜 인증)을 마치면, 로그인 대신 복구를 물어본다.

#NNN-S8 이메일 로그인 [테스트]
- Given A가 탈퇴한 지 10일 지났다
- When  `POST /api/v1/auth/login`에 A의 이메일과 맞는 비밀번호를 보낸다
- Then  409 `WITHDRAWAL_PENDING`이고 `ACCESS_TOKEN` 쿠키가 없다
- Then  `MEMBER_RESTORE` 쿠키(HttpOnly, SameSite=Lax, HTTPS면 Secure, Path=`/api/v1/auth/restore`, 10분)를 받는다

#NNN-S9 소셜 로그인 [테스트]
- Given 구글로만 가입한 회원 G가 탈퇴한 지 10일 지났다
- When  `POST /api/v1/auth/oauth/google`로 G의 구글 계정 인증을 마친다
- Then  S8과 같이 409 `WITHDRAWAL_PENDING`과 `MEMBER_RESTORE` 쿠키다(새 가입이 아니다)

#NNN-S10 비밀번호 틀림 [테스트]
- Given 탈퇴 대기 중인 A
- When  틀린 비밀번호로 로그인한다
- Then  401이고 `MEMBER_RESTORE` 쿠키가 없다(탈퇴 대기인지 알려 주지 않는다)

## 4. 복구 (#NNN-R4)
복구 안내를 받은 브라우저에서 복구하면 탈퇴 전 상태로 돌아오고 로그인된다.

#NNN-S11 정상 [테스트]
- Given S8로 `MEMBER_RESTORE` 쿠키를 받았다
- When  `POST /api/v1/auth/restore`에 `Content-Type: application/json`으로 보낸다
- Then  200과 회원 정보, `ACCESS_TOKEN` 쿠키를 받는다
- Then  `status`가 `ACTIVE`, `withdrawn_at`이 비어 있다
- Then  S4에서 넘긴 보리의 소유자는 그대로 C다

#NNN-S12 복구 확인 실패 [테스트]
- When  쿠키 없이, 만료된 쿠키로, 이미 한 번 쓴 쿠키로 각각 복구한다
- Then  모두 403 `FORBIDDEN`이고 상태가 바뀌지 않는다

## 5. 보관 중 가입 막기 (#NNN-R5)
보관 중인 회원의 이메일로는 새로 가입하지 않고 복구로 안내한다.

#NNN-S13 같은 이메일 [테스트]
- Given 탈퇴 대기 중인 A(`a@pawmento.dev`)
- When  `POST /api/v1/auth/signup`에 `a@pawmento.dev`로 가입한다
- Then  409 `CONFLICT`이고 메시지에 "로그인하면 복구할 수 있어요" 안내가 있다
- 같은 이메일의 다른 제공자 계정으로 처음 소셜 로그인해도 같은 409다

## 6. 하드 삭제 (#NNN-R6)
탈퇴하고 30일이 지나면 스케줄러가 제공자 연결을 끊고 회원을 지운다.

#NNN-S14 정상 (Q1) [테스트]
- Given A가 탈퇴한 지 31일 지났고, 카카오가 연결돼 있으며, 커뮤니티 글 1개를 썼다
- When  하드 삭제 작업을 실행한다
- Then  카카오 연결 끊기 포트가 A의 카카오 id로 한 번 불린다
- Then  `member`·`social_account`에 A가 없다
- Then  A의 글은 남아 있고 작성자가 비어 있다(Q1 기준)

#NNN-S15 아직 30일 전 [테스트]
- Given B가 탈퇴한 지 29일 지났다
- When  하드 삭제 작업을 실행한다
- Then  B는 그대로 남아 있다

#NNN-S16 끊기 실패 [테스트]
- Given A의 카카오 연결 끊기가 실패한다
- When  하드 삭제 작업을 실행한다
- Then  A는 지워지고, `social_unlink_retry`에 `(KAKAO, A의 카카오 id, 시도 1회)`가 남는다
- When  다음 날 작업에서 끊기가 성공한다
- Then  `social_unlink_retry`에서 그 행이 없어진다
- Given 3회 모두 실패한다
- Then  행을 지우고 에러 로그를 남긴다

#NNN-S17 이미 끊긴 연결 [테스트]
- Given 사용자가 제공자 쪽에서 이미 연결을 끊었다(카카오 "연결되지 않은 사용자", 구글 `invalid_token`)
- Then  성공으로 보고 `social_unlink_retry`에 남기지 않는다

#NNN-S18 삭제 직전 복구 [테스트]
- Given A가 탈퇴한 지 30일이 지났고, 삭제 작업과 A의 복구 요청이 동시에 온다
- Then  복구가 먼저 끝나면 A는 지워지지 않는다. 삭제가 먼저 끝나면 복구는 403이다

## 범위 밖
- 탈퇴 확인 화면, 복구 안내 팝업, "탈퇴한 회원입니다" 표시: 프론트엔드 이슈
- 각 도메인의 작성자 컬럼 변경: Q1이 정해지면 도메인별 이슈
- 네이버 연결 끊기: 네이버 로그인(#40-S12) 때 함께 다룬다
- 탈퇴 사유 수집, 관리자 강제 탈퇴

## 작업 순서
1. 탈퇴 상태와 탈퇴 API — S1~S3
   - 파일: `member/domain/Member.java`(`status`, `withdrawnAt`, `withdraw()`·`restore()`), `MemberJpaEntity.java`, 새 `WithdrawMemberUseCase`·서비스, `MemberController.java`, 토큰 검증 후 회원 상태를 보는 곳(`global/security`)
   - 확인: `./gradlew test`
2. 소유권 넘기기 — S4~S7
   - 파일: 새 출력 포트 `TransferPetOwnershipPort`(pet·family 입력 포트에 연결)
   - 확인: 같은 명령
3. 복구 안내와 복구 — S8~S12
   - 파일: `LoginMemberService.java`, `SocialLoginMemberService.java`, 새 `member_restore_token` 저장(`social_link_state`와 같은 원자적 소비), 새 `RestoreMemberUseCase`·서비스, `AuthController.java`, `PublicEndpoints.java`(`POST /api/v1/auth/restore`), `ErrorCode`에 `WITHDRAWAL_PENDING`
   - 확인: 같은 명령
4. 보관 중 가입 막기 — S13
   - 파일: `SignUpMemberService.java`, `SocialLoginMemberService.java`
5. 구글 토큰 저장
   - 파일: `social_account`에 `encrypted_refresh_token`, 구글 프로필 어댑터, 암호화 어댑터(키는 환경 변수 `SOCIAL_TOKEN_KEY`)
6. 하드 삭제 스케줄러 — S14~S18
   - 파일: 새 `HardDeleteWithdrawnMembersService`, `@Scheduled` 어댑터(`adapter/in/scheduler`), 새 `UnlinkSocialAccountPort`와 카카오·구글 어댑터, 새 `social_unlink_retry` 저장
   - 확인: 같은 명령

## 정한 것
- 탈퇴하면 30일 동안 보관하고, 그 뒤 스케줄러가 하드 삭제한다.
- 보관 중 로그인(이메일·소셜)하면 "복구하시겠습니까?"를 띄운다. 복구하면 탈퇴 전 상태로 돌아간다. 복구는 본인 확인(비밀번호·소셜 인증)을 마친 뒤에만 할 수 있다.
- 보관 중에는 같은 이메일·같은 소셜 계정으로 새로 가입할 수 없다. 재가입이 아니라 복구로 안내한다.
- 탈퇴하면 `ACCESS_TOKEN` 쿠키를 지우고, 이미 발급된 토큰도 탈퇴 회원이면 거부한다.
- 소유한 반려동물은 탈퇴할 때 가족에게 넘긴다. 넘길 대상은 소유자가 고르고, 고르지 않으면 가장 먼저 연결된 가족이다. 복구해도 소유권은 돌려받지 않는다(가족으로도 남지 않는다).
- 제공자 연결 끊기는 하드 삭제 때 한다. 보관 중에는 소셜로 복구해야 하기 때문이다.
- 연결 끊기가 실패해도 회원은 30일 약속대로 지우고, 끊기만 `social_unlink_retry`로 하루 한 번, 최대 3회 다시 시도한다.
- 구글은 연결 끊기(revoke)에 토큰이 필요하므로 refresh 토큰을 암호화해 저장한다.
- 탈퇴 회원이 쓴 글·기록은 남기고 작성자 이름만 "탈퇴한 회원입니다"로 보여 준다(시점은 Q2).

## 가정
- 탈퇴는 로그인 상태에서 비밀번호를 다시 묻지 않고 한다. 실수 방지는 화면의 확인 팝업으로 한다.
- 탈퇴 경로는 `DELETE /api/v1/members/me`이고, 본문이 없어도 `Content-Type: application/json`을 요구한다(기존 인증 규칙).
- "30일"은 `withdrawn_at + 30일`이 지난 뒤 처음 도는 작업에서 지운다. 작업은 하루 한 번 새벽 4시(Asia/Seoul)에 돈다.
- 탈퇴 회원 토큰 거부는 토큰 검증 뒤 회원 상태를 한 번 조회해 확인한다. 요청마다 DB 조회가 하나 늘어난다.
- 복구 토큰은 `link-state`처럼 해시만 DB에 두고, 10분 동안 한 번만 쓸 수 있다.
- 복구 안내 응답은 새 에러 코드 `WITHDRAWAL_PENDING`(409)을 쓴다. 같은 이메일 충돌(`CONFLICT`)과 화면이 구별할 수 있어야 하기 때문이다. 공통 에러 규칙([backend-exceptions.md](../backend-exceptions.md))에 맞춰 추가한다.
- 보관 중에도 닉네임·태그는 그대로 유지한다.
- 삭제 작업과 복구가 겹치지 않게, 삭제 작업은 회원 행을 잠그고 상태를 다시 확인한 뒤 지운다.

## 참고
- 카카오는 어드민 키(`KAKAO_ADMIN_KEY`)와 회원번호로 연결 끊기를 할 수 있어 토큰을 저장하지 않아도 된다(`POST https://kapi.kakao.com/v1/user/unlink`, `target_id_type=user_id`). 붙이기 전에 카카오 문서로 다시 확인한다.
- 구글 revoke는 `POST https://oauth2.googleapis.com/revoke`에 토큰을 보낸다. 액세스 토큰은 1시간 뒤 만료돼 30일 뒤에는 쓸 수 없으므로 refresh 토큰이 필요하다.
- 구글은 인가 URL에 `access_type=offline`이 있어야 refresh 토큰을 주고, 보통 처음 동의할 때만 준다. 인가 URL은 프론트엔드가 만들므로 함께 바꿔야 한다. 이 변경 전에 가입한 구글 회원은 refresh 토큰이 없어 revoke를 건너뛴다.
- 다른 도메인의 FK가 `member`를 참조하면 하드 삭제가 막힌다. `ddl-auto: update`는 기존 FK의 `ON DELETE` 동작을 바꾸지 않으므로 Q1이 정해지면 개발 서버에는 직접 SQL로 바꾼다([소셜 계정 연결의 이전 절차](social-account-link.md#개발-서버-이전-절차) 참고).
