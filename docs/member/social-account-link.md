# 소셜 계정 연결·해제

관련 이슈: [#109](https://github.com/likelion-backendschool-9th/pawmento/issues/109)

## 한눈에
- 끝나면: 로그인한 보호자가 마이페이지에서 구글·카카오·네이버 계정을 연결하고 해제한다. 연결한 방법이면 무엇으로 들어와도 같은 회원이다.
- 정한 것: 개발 서버 DB 이전은 직접 SQL로 한다(Q1, 리뷰 동의). 절차는 [개발 서버 이전 절차](#개발-서버-이전-절차)
- 규모: 5단계 (PR 2개)

## 질문
- Q1 (정함): 이번 변경은 `member`의 `provider`·`provider_user_id`를 `social_account`로 옮기고 두 컬럼을 지운다. `ddl-auto: update`는 컬럼을 지우지 않고, 남은 `provider`는 NOT NULL이라 새 코드의 회원 저장이 실패한다. → 이번 개발 서버 이전은 직접 SQL로 한다. 새 앱이 요청을 받기 전에 이전·검증을 끝내고, 실패하면 DB와 앱을 함께 되돌린다([개발 서버 이전 절차](#개발-서버-이전-절차)). Flyway는 다음 스키마 변경 때 다시 논의한다.

## 1. 로그인 방법을 회원에서 분리 (#109-R1)
소셜 계정을 `social_account`에 두고, 기존 소셜 로그인은 그대로 동작한다.

#109-S1 기존 동작 유지 [테스트: SocialLoginScenarioTest #40-S1·S3·S5~S7·S9·S10]
- Given #40의 소셜 로그인 시나리오
- Then  응답·쿠키·상태 코드가 이전과 같다 (응답·쿠키 회귀 검증은 기존 테스트를 그대로 쓴다)
- #40-S1 테스트는 `Member`의 제공자 필드를 확인하므로, `social_account`에 `(provider, provider_user_id)` 한 줄이 생겼는지 확인하도록 고친다
- 두 줄이 함께 저장되는지와 롤백은 #109-S18에서 검증한다

#109-S18 소셜 가입 저장은 한 트랜잭션 [테스트: MemberPersistenceTest]
- When  영속성 어댑터의 소셜 회원 저장 메서드로 처음 소셜 회원을 저장한다
- Then  `member` 한 줄과 `social_account` 한 줄이 함께 생긴다
- Given 같은 `(provider, provider_user_id)`의 `social_account`가 이미 있다
- When  다른 새 회원을 그 제공자 계정으로 저장한다
- Then  유니크 제약 예외가 나고, 새 `member` 줄도 남지 않는다(롤백)
- 테스트 메서드에는 `@Transactional`을 걸지 않는다. 걸면 테스트 트랜잭션에 묶여 롤백을 확인할 수 없다

기존 동시 첫 로그인 계약 유지 (#40-S15·S16)
- 저장 경계: `SaveMemberPort.save(member)` 대신 처음 소셜 가입용 `SaveMemberPort.saveSocial(member, provider, providerUserId)`를 둔다. 영속성 어댑터가 `member`·`social_account`를 한 트랜잭션으로 `saveAndFlush`한다.
- 오류 변환은 지금처럼 어댑터가 제약 이름으로 한다. `uk_member_email` → `DuplicateEmailException`, 같은 소셜 계정은 `member`의 `uk_member_provider_user` 대신 `social_account`의 `(provider, provider_user_id)` 제약(`uk_social_account_provider_user`) → `DuplicateSocialAccountException`. 두 예외 모두 트랜잭션 전체가 롤백된다.
- 서비스(`SocialLoginMemberService.register`)는 그대로다. 두 예외를 받으면 `loadByProvider`로 다시 찾아 있으면 그 회원으로 로그인하고(#40-S15), 없으면 그대로 409다(#40-S16). `loadByProvider`만 `social_account`를 거쳐 찾도록 바뀐다.
- 검사 위치: `SocialLoginScenarioTest`의 "#40-S15 #40-S16 DB 제약은 … 다른 예외로 알려준다"를 `saveSocial`로 바꿔 두 예외 변환을 그대로 확인한다. `SocialLoginMemberServiceTest`의 #40-S15·S16(재조회·409)은 포트 이름만 바꾸고 기대 결과는 유지한다.

#109-S2 데이터 이전 [명령]
- Given 개발 서버에 `provider`가 `LOCAL`이 아닌 회원이 n명 있다
- When  이전을 실행한다
- Then  `social_account`에 n줄이 생기고, `member`에 `provider`·`provider_user_id` 컬럼이 없다
- Then  이전 전에 가입한 소셜 회원이 같은 id로 로그인된다

## 2. 비밀번호 로그인 기준 (#109-R2)
비밀번호 로그인은 가입 방식이 아니라 비밀번호가 있는지로 정한다.

#109-S3 소셜을 연결한 이메일 회원 [테스트]
- Given 이메일로 가입한 회원 A가 카카오를 연결했다
- When  `POST /api/v1/auth/login`에 A의 이메일과 비밀번호를 보낸다
- Then  200과 쿠키를 받는다

#109-S4 소셜로만 가입한 회원 [테스트: SocialLoginScenarioTest #40-S10]
- Given 구글로만 가입한 회원(`password_hash: null`)
- When  비밀번호 `"absent-member-password"`로 로그인한다
- Then  401이다

## 3. 연결 목록 (#109-R3)
로그인한 회원은 자기 로그인 방법을 본다.

#109-S5 정상 [테스트]
- Given 이메일로 가입하고 구글을 연결한 회원
- When  `GET /api/v1/members/me/social-accounts`
- Then  200과 `{"hasPassword": true, "socialAccounts": [{"provider": "GOOGLE", "linkedAt": "…"}]}`
- Then  제공자 사용자 id(`sub`)는 응답에 없다
- Given 구글로만 가입하고 다른 연결이 없는 회원
- Then  `{"hasPassword": false, "socialAccounts": [{"provider": "GOOGLE", …}]}`로, 화면이 "다른 로그인 방법이 없음"을 알 수 있다

## 4. 연결 (#109-R4)
로그인한 회원이 제공자 인증을 한 번 거쳐 그 계정을 붙인다.

연결 요청 확인: 연결은 로그인 수단을 더하는 동작이라, 콜백이 **지금 로그인한 회원이 그 제공자로 시작한 연결 요청**인지 서버가 확인한다.
1. 연결 시작: `POST /api/v1/members/me/social-accounts/{provider}/link-state` → 200 `{"state": "…"}`. 서버는 추측할 수 없는 일회용 값 두 개를 만든다.
   - `state`: 응답 본문으로 화면에 준다.
   - 브라우저 확인값: `SOCIAL_LINK` 쿠키(HttpOnly, SameSite=Lax, HTTPS면 Secure, Path=`/api/v1/members/me/social-accounts`, 10분)로만 준다. 화면 코드는 읽을 수 없다. `Secure`는 `ACCESS_TOKEN`과 같이 요청이 HTTPS일 때 붙인다(docs/member/README.md).
   - DB(`social_link_state`)에는 `(state 해시, 브라우저 확인값 해시, member_id, provider, expires_at)`을 저장한다(유효 10분). 원래 값은 저장하지 않는다.
   - 본문이 없어도 `Content-Type: application/json`을 요구한다. 해제(`DELETE`)도 같다. 데이터를 바꾸는 요청은 JSON만 받는다는 기존 인증 규칙(SameSite=Lax 전제, docs/member/README.md)을 따른다. 없으면 415다(`AuthController`의 로그아웃처럼 `consumes = MediaType.APPLICATION_JSON_VALUE`).
   - 브라우저당 연결은 한 번에 하나만 진행한다. 요청에 이미 `SOCIAL_LINK` 쿠키가 있으면 그 쿠키의 `social_link_state` 행을 먼저 지우고 새로 발급한다. 같은 브라우저에서 새로 시작하면 이전에 시작한 연결은 무효다.
2. 화면은 이 state를 제공자 인가 URL에 실어 보내고, 콜백에서 받은 `code`·`state`를 연결 API에 넘긴다. 같은 브라우저라 `SOCIAL_LINK` 쿠키가 함께 간다.
3. 연결: 서버는 제공자에 code를 보내기 **전에** 아래 한 문장으로 state를 확인하면서 지운다. 지운 행이 1개일 때만 계속하고, 0개면 403이고 제공자를 부르지 않으며 아무것도 저장하지 않는다. 이 삭제는 **제공자 호출 전에 따로 커밋한다.** 영속성 어댑터의 소비 메서드가 자기 트랜잭션으로 끝내고, 서비스에는 `@Transactional`을 걸지 않는다(제공자 HTTP 때문에 지금도 걸지 않는 것과 같은 방식). 그래서 뒤에서 제공자가 거절하거나 저장이 실패해도 state는 되살아나지 않는다.
   ```sql
   DELETE FROM social_link_state
   WHERE state_hash = ? AND browser_hash = ? AND member_id = ? AND provider = ? AND expires_at > ?
   ```
   - 조회와 삭제를 나누지 않으므로, 같은 state로 동시에 와도 DB가 한 요청만 행을 지우게 한다(원자적 소비). JPA에서는 `@Modifying @Query`의 반환 행 수로 판단한다.
   - 같은 회원이라도 다른 브라우저(쿠키 없음·다른 값)에서 제출하면 `browser_hash`가 달라 거부된다.
   - 연결 완료 응답은 성공·실패와 상관없이 `SOCIAL_LINK` 쿠키를 **지우지 않는다**. 쿠키는 10분 뒤 저절로 만료된다. 쿠키 이름이 하나라, 완료 응답이 쿠키를 지우면 그사이 같은 브라우저에서 새로 시작한 연결의 쿠키까지 지워질 수 있기 때문이다. state 행은 이미 소비됐으므로 남은 쿠키로는 아무것도 할 수 없다.
- JWT 쿠키 방식이라 서버 세션이 없으므로 state는 DB에 둔다. 로그인 세션 대신 이 브라우저 확인 쿠키로 발급한 곳과 완료하는 곳을 묶는다. (고침 #109: 리뷰 — 같은 회원의 다른 브라우저 구별, 동시 소비, 소비 커밋 시점, 브라우저당 한 연결, 기존 인증 규칙)

#109-S6 정상 [테스트]
- Given 이메일 회원 A가 로그인했고 `…/kakao/link-state`로 state `s-1`을 받았다
- When  `POST /api/v1/members/me/social-accounts/kakao`에 `{"code":"abc","state":"s-1","redirectUri":"…"}`
- Then  200과 S5 형식의 목록(카카오 포함)
- Then  같은 카카오 계정으로 `POST /api/v1/auth/oauth/kakao`하면 A로 로그인된다

#109-S7 다른 회원의 계정 [테스트]
- Given 그 카카오 계정이 회원 B에 연결돼 있다
- Then  409 `CONFLICT`이고 A의 목록은 그대로다

#109-S8 같은 제공자의 다른 계정 [테스트]
- Given A에 카카오 계정 k-1이 연결돼 있다
- When  카카오 계정 k-2를 연결한다
- Then  409 `CONFLICT`

#109-S9 같은 계정 다시 연결 [테스트]
- Given A에 k-1이 연결돼 있다
- When  k-1을 다시 연결한다
- Then  200이고 목록에 카카오가 한 번만 있다

#109-S10 로그인 안 함·제공자 거절 [테스트]
- 쿠키 없이 부르면 401, 제공자가 code를 거절하면 401 `UNAUTHORIZED`
- `link-state`·연결·해제를 `Content-Type: application/json` 없이 부르면 415이고, 발급·저장·삭제가 일어나지 않는다

#109-S11 동시 연결 [테스트]
- Given 두 회원이 각자 브라우저에서 같은 카카오 계정을, 또는 한 회원이 서로 다른 두 브라우저에서 카카오 두 계정을 동시에 연결한다(각 브라우저에 자기 `SOCIAL_LINK`가 있다)
- Then  하나만 성공하고 나머지는 409다 (`(provider, provider_user_id)`, `(member_id, provider)` 유니크 제약)

#109-S17 연결 요청 확인 실패 [테스트]
- Given 회원 A가 로그인했다
- When  state 없이, 회원 B가 받은 state로, 구글용으로 받은 state로 카카오를, 만료된 state로, 이미 한 번 쓴 state로 각각 연결한다
- When  A가 브라우저 1에서 받은 state를 `SOCIAL_LINK` 쿠키 없이, 또는 브라우저 2에서 따로 받은 쿠키와 함께 제출한다(같은 회원의 다른 브라우저)
- Then  모두 403 `FORBIDDEN`이고, A의 목록과 `social_account`는 그대로다
- Then  제공자에 code를 보내지 않는다(가짜 포트 호출 0번)

#109-S19 같은 state 동시 완료 [테스트]
- Given 회원 A가 같은 브라우저에서 카카오 state `s-1`을 받았다
- When  `s-1`과 같은 쿠키로 연결 요청 두 개가 동시에 온다
- Then  하나만 200이고 다른 하나는 403이다
- Then  제공자 포트 호출은 한 번뿐이고, `social_link_state`에 `s-1` 행이 남지 않는다

#109-S20 제공자 거절 뒤 같은 state [테스트]
- Given 회원 A가 카카오 state `s-1`을 받았고, 제공자가 그 code를 거절한다
- When  `s-1`로 연결한다
- Then  401이다
- When  같은 브라우저에서 `s-1`로 다시 연결한다
- Then  403이고, 제공자 포트 호출은 두 요청을 합쳐 한 번뿐이다
- Then  `social_link_state`에 `s-1` 행이 없고, `SOCIAL_LINK` 쿠키는 지워지지 않았다

#109-S21 같은 브라우저에서 두 연결 [테스트]
- Given 회원 A가 같은 브라우저에서 카카오 state `s-1`을 받은 뒤, 네이버 state `s-2`를 받았다
- When  `s-1`로 카카오를 연결한다
- Then  403이고 제공자를 부르지 않는다(새로 시작하면 이전 연결은 무효)
- When  `s-2`로 네이버를 연결한다
- Then  200이다(앞의 403이 쿠키를 지우지 않았다)
- Given 회원 A가 카카오 state `s-1`로 연결을 요청했고, 서버가 `s-1`을 소비한 뒤 제공자 응답을 기다리는 중이다
- When  그사이 같은 브라우저에서 네이버 연결을 시작해 새 `SOCIAL_LINK`와 `s-2`를 받고, 그 뒤 카카오 요청이 200으로 끝난다
- Then  카카오 200 응답에 `SOCIAL_LINK`를 지우는 `Set-Cookie`가 없다
- When  `s-2`로 네이버를 연결한다
- Then  200이다(먼저 끝난 응답이 새 쿠키를 지우지 않았다)

## 5. 해제 (#109-R5)
로그인 방법이 하나 이상 남을 때만 해제한다.

#109-S12 정상 [테스트]
- Given 비밀번호가 있고 구글이 연결된 회원
- When  `DELETE /api/v1/members/me/social-accounts/google`
- Then  204이고 목록에 구글이 없다
- Then  그 구글 계정으로 로그인하면 처음 소셜 로그인으로 다뤄진다

#109-S13 마지막 로그인 방법 [테스트]
- Given 구글로만 가입한 회원
- When  구글을 해제한다
- Then  409 `CONFLICT`이고 구글이 그대로 남는다

#109-S14 동시 해제 [테스트]
- Given 구글·카카오만 연결된 소셜 회원(비밀번호 없음)
- When  구글 해제와 카카오 해제가 동시에 온다
- Then  하나만 204이고 다른 하나는 409, 로그인 방법이 하나 남는다

#109-S16 소셜로만 가입한 회원의 연결·해제 (#109 다섯 번째 시나리오) [테스트: 제공자 프로필은 가짜 포트]
- Given 구글로만 가입한 회원 G(회원 id `g`, 비밀번호 없음)
- When  네이버를 연결한다
- Then  200이고 목록에 구글·네이버가 있다
- When  구글을 해제한다
- Then  204이고 목록에 네이버만 있다
- When  그 네이버 계정으로 `POST /api/v1/auth/oauth/naver`
- Then  200이고 회원 id는 `g`다

## 6. 같은 이메일 안내 (#109-R6)
처음 소셜 로그인이 같은 이메일 때문에 막히면 연결 방법을 안내한다.

#109-S15 안내 문구 [테스트]
- Given 이메일 회원 A와 같은 이메일의 구글 계정
- When  그 구글 계정으로 처음 소셜 로그인한다
- Then  409이고 메시지에 "로그인한 뒤 마이페이지에서 연결" 안내가 있다
- 기존 #40-S5의 문구를 바꾸는 것이다(docs/member/social-login.md에 고침 표시). 고칠 검사: `SocialLoginScenarioTest`의 #40-S5(409·`CONFLICT`·쿠키 미발급 검증은 그대로 두고 `containsString` 문구만 바꿈), 문구 원본은 `SocialEmailConflictException`

## 범위 밖
- 소셜 회원의 비밀번호 설정: 이메일 인증과 함께 다룬다
- 같은 이메일 자동 연결: 계정 선점 위험으로 하지 않는다(#40 정한 것)
- 마이페이지 연결 화면, 연결용 콜백 처리(서버가 준 state를 인가 URL에 싣고 콜백 값을 넘기기), 로그인 방법이 하나뿐일 때의 안내 문구: 프론트엔드 이슈
- 카카오·네이버 클라이언트: #40 남은 작업(S11·S12). 이 문서의 연결 API는 제공자와 상관없이 `LoadSocialProfilePort`를 쓴다

## 작업 순서
1. 저장 구조 분리 (PR 1, 응답 변화 없음) — S1~S4, S18
   - 파일: `member/domain/Member.java`(provider 제거, `isLocal` → `hasPassword`, `register` 해시 검사), 새 `domain/SocialAccount.java`, 새 `adapter/out/persistence/SocialAccountJpaEntity.java`·`SocialAccountJpaRepository.java`, `MemberJpaEntity.java`, `MemberPersistenceAdapter.java`(소셜 회원 저장을 한 트랜잭션으로), `LoginMemberService.java`, `SocialLoginMemberService.java`
   - 테스트: `SocialLoginScenarioTest`의 #40-S1 확인 대상 변경·#40-S15·S16 제약 변환 검사를 `saveSocial`로 변경, `SocialLoginMemberServiceTest`의 #40-S15·S16 포트 이름 변경, `MemberPersistenceTest`에 S18 추가
   - 확인: `./gradlew test`(CI), [개발 서버 이전 절차](#개발-서버-이전-절차)
2. 연결 목록 — S5
   - 파일: 새 `ListSocialAccountsUseCase`·서비스, `MemberController.java`에 엔드포인트 추가 (컨트롤러는 도메인에 하나, docs/backend-architecture.md #73). 연결·해제 엔드포인트도 같은 컨트롤러에 둔다
3. 연결 — S6~S11, S17, S19~S21
   - 파일: 새 `IssueSocialLinkStateUseCase`·서비스, 새 `SocialLinkStateJpaEntity`·`SocialLinkStateJpaRepository`(`social_link_state`), 새 `LinkSocialAccountUseCase`·서비스, `SocialAccountAlreadyLinkedException`(StateConflict), `InvalidSocialLinkStateException`(PermissionDenied)
4. 해제 — S12~S14, S16
   - 파일: 새 `UnlinkSocialAccountUseCase`·서비스(회원 행 잠금), `LastLoginMethodException`(StateConflict)
5. 같은 이메일 안내 — S15
   - 파일: `SocialEmailConflictException.java`

## 정한 것
- 소셜 계정 연동은 MVP에 넣는다. (11/18 회의)
- 이메일로 가입한 회원도 소셜 계정을 연결할 수 있다. 비밀번호를 잊어도 연결한 소셜로 들어올 수 있다.
- 소셜로만 가입한 회원은 MVP에서 비밀번호를 만들 수 없다.
- 그래서 그 회원이 제공자 계정에 접근할 수 없게 되면, 미리 연결해 둔 다른 소셜 계정으로만 다시 로그인할 수 있다. 마이페이지 연동 화면은 `hasPassword: false`이고 연결이 하나뿐일 때 "다른 로그인 방법을 연결해 두라"는 안내를 보여준다.
- 연결·해제 API와 저장 구조는 member 담당(다윗)이 맡는다.
- 연결은 로그인한 상태에서만 한다. 본인 확인이 끝난 회원에게만 붙이므로 같은 이메일 자동 연결과 달리 계정 선점 위험이 없다.

## 가정
- 로그인 방법이 하나도 없는 회원은 만들지 않는다. 해제는 비밀번호나 다른 소셜 계정이 남을 때만 된다.
- 해제할 때 회원 행을 잠가(`loadForNicknameChange`와 같은 방식) 동시 해제로 로그인 방법이 0개가 되지 않게 한다.
- 연결 state는 발급한 브라우저에서 10분 동안 한 번만 쓸 수 있고, 브라우저당 하나만 유효하다. 확인은 제공자 호출 전에 하고, 실패하면 403(`PermissionDeniedException`)이다.
- 연결할 때는 제공자 이메일을 쓰지 않는다. 회원 이메일과 달라도 연결된다. 로그인은 `(provider, provider_user_id)`로만 찾는다.
- 같은 계정을 다시 연결하면 성공으로 본다(멱등). 연결되지 않은 제공자를 해제하면 204다.
- 연결 응답은 목록 전체를 돌려줘 화면이 다시 조회하지 않아도 되게 한다.
- 처음 소셜 가입은 `member`와 `social_account`를 한 트랜잭션으로 저장한다. 서비스에는 여전히 `@Transactional`을 걸지 않고, 영속성 어댑터의 저장 메서드가 묶는다.

## 참고
- `ddl-auto: update`는 새 테이블·컬럼·제약은 만들지만 컬럼 삭제와 NOT NULL 변경은 하지 않는다. 남은 `member.provider`(NOT NULL) 때문에 새 코드의 저장이 실패한다.
- 개발 서버에서 `docker compose down -v`는 쓰지 않는다(`media-data` 볼륨까지 지워진다, docs/deploy.md).
- Hibernate는 `@Enumerated(STRING)`에 허용 값 CHECK 제약을 만든다. 새 제공자를 더하면 그 제약도 바꿔야 한다.

## 개발 서버 이전 절차
새 앱이 요청을 받기 **전에** 이전과 검증을 끝낸다. 새 앱을 먼저 띄우면 복사가 끝나기 전 기존 소셜 회원 로그인이 새 테이블에서 연결 정보를 못 찾는다. 컬럼을 지운 뒤에는 이전 앱 이미지만 다시 띄워서는 되돌릴 수 없으므로, 백업을 먼저 뜬다.

서버 `~/pawmento`에서 실행한다. `.env`의 `COMPOSE_FILE`로 `-f`는 생략한다(docs/deploy.md). `.env`에는 `IMAGE_TAG`가 없고 `compose.prod.yaml`이 필수로 읽으므로, **모든 `docker compose` 명령 전에 `IMAGE_TAG`를 넣는다**(stop·exec도 설정을 읽는다).

준비 (로컬·GitHub)
1. PR 1 머지 전에 자동 배포를 끈다: GitHub 환경 `develop`의 `DEPLOY_HOST`를 잠시 지운다. 머지하면 검사·이미지 업로드만 되고 서버는 그대로다.
2. 지금 서버에서 도는 이미지 태그(`sha-<이전 커밋>`)와 PR 1 머지 커밋 태그(`sha-<PR 1 커밋>`)를 적어 둔다.
3. 배포 워크플로가 하던 설정 복사를 직접 한다. PR 1 머지 커밋의 `compose.yaml`, `compose.prod.yaml`, `nginx/proxy.conf`를 꺼내 서버에 올려 둔다(아직 덮어쓰지 않음).
   꺼내기에 실패하면 `>`가 빈 파일을 남기므로, `set -euo pipefail`로 실행하고 파일이 비어 있지 않은지(`test -s`) 확인한 뒤에만 서버로 보낸다.
   ```bash
   set -euo pipefail
   git fetch origin
   mkdir -p after-109/nginx
   for f in compose.yaml compose.prod.yaml nginx/proxy.conf; do
     git show <PR 1 커밋>:$f > after-109/$f
     test -s after-109/$f
   done
   scp -r after-109 <서버>:~/pawmento/
   ```
4. 이전 SQL `migrate-109.sql`을 만들어 서버 `~/pawmento`에 올린다. `social_account` 생성문은 PR 1의 `SocialAccountJpaEntity`로 로컬에서 `ddl-auto: update`가 만든 DDL(컬럼·유니크 제약 2개·외래 키·CHECK)을 그대로 복사한다. 건수가 다르면 `RAISE`로 멈추므로 `COMMIT`까지 가지 않고 전부 롤백된다.
   ```sql
   BEGIN;
   -- social_account CREATE TABLE (로컬 DDL 복사)
   INSERT INTO social_account (member_id, provider, provider_user_id, linked_at)
   SELECT id, provider, provider_user_id, created_at FROM member WHERE provider <> 'LOCAL';
   DO $$
   BEGIN
     IF (SELECT count(*) FROM member WHERE provider <> 'LOCAL') <> (SELECT count(*) FROM social_account) THEN
       RAISE EXCEPTION '이전 건수가 다릅니다';
     END IF;
   END $$;
   ALTER TABLE member DROP COLUMN provider, DROP COLUMN provider_user_id;
   COMMIT;
   ```

이전 (서버)
명령은 `set -euo pipefail`로 실행해 **한 단계라도 실패하면 거기서 멈춘다.** 백업을 검증하지 못하면 이전 SQL을 실행하지 않는다.
```bash
set -euo pipefail
cd ~/pawmento
export IMAGE_TAG=sha-<이전 커밋>

# 1. 지금 설정을 되돌리기용으로 보관
# set -e는 `a && b`에서 앞 명령의 실패로는 멈추지 않으므로 한 줄에 한 명령씩 쓴다
mkdir -p before-109/nginx
cp compose.yaml compose.prod.yaml before-109/
cp nginx/proxy.conf before-109/nginx/

# 2. 백엔드만 멈추고 백업
docker compose stop backend
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"' > member-before-109.dump

# 3. 백업 검증: 파일을 읽을 수 있고 member 데이터가 들어 있어야 다음으로 간다
docker compose exec -T db pg_restore --list < member-before-109.dump | grep -q 'TABLE DATA public member '

# 4. 이전 (건수가 다르거나 SQL이 실패하면 psql이 0이 아닌 코드로 끝나고 DB는 그대로)
docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < migrate-109.sql

# 5. PR 1 커밋의 설정으로 바꾸고 새 이미지로 띄움
cp after-109/compose.yaml after-109/compose.prod.yaml .
cp after-109/nginx/proxy.conf nginx/
export IMAGE_TAG=sha-<PR 1 커밋>
docker compose up -d --no-build
```
6. #109-S2를 확인한다(이전 전에 가입한 소셜 회원이 같은 id로 로그인, 이메일 회원 로그인).
7. `DEPLOY_HOST`를 다시 넣는다. 다음 머지부터는 워크플로가 설정과 이미지를 함께 맞춘다.

실패하면
- 1~3에서 멈춤: DB는 바뀌지 않았다. 설정도 그대로이므로 `IMAGE_TAG=sha-<이전 커밋> docker compose up -d --no-build`로 이전 앱을 다시 띄운다. 백업 문제를 해결하기 전에는 이전을 다시 시작하지 않는다.
- 4에서 멈춤: 이전 SQL이 롤백돼 DB는 그대로다. 위와 같이 이전 앱을 다시 띄운다.
- 5·6에서 실패: DB와 앱·설정을 **함께** 되돌린다. `pg_restore --clean`으로 같은 DB에 덮어쓰면 백업에 없는 `social_account`의 외래 키가 `member` 삭제를 막아 복원되지 않는다. 그래서 **빈 DB에 복원하고, 확인한 뒤에만 DB 이름을 바꾼다.** 여기서도 한 단계라도 실패하면 멈춘다.
  ```bash
  set -euo pipefail
  cd ~/pawmento
  export IMAGE_TAG=sha-<이전 커밋>
  cp before-109/compose.yaml before-109/compose.prod.yaml .
  cp before-109/nginx/proxy.conf nginx/
  docker compose stop backend

  # 1. 빈 DB에 백업 복원 (한 트랜잭션, 오류가 나면 바로 멈춤)
  docker compose exec -T db sh -c 'dropdb -U "$POSTGRES_USER" --if-exists pawmento_restore'
  docker compose exec -T db sh -c 'createdb -U "$POSTGRES_USER" pawmento_restore'
  docker compose exec -T db sh -c 'pg_restore --exit-on-error --single-transaction --no-owner -U "$POSTGRES_USER" -d pawmento_restore' < member-before-109.dump

  # 2. 복원 확인: member.provider가 있어야 이름을 바꾼다
  test "$(docker compose exec -T db sh -c 'psql -tA -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d pawmento_restore -c "select count(*) from information_schema.columns where table_name = '\''member'\'' and column_name = '\''provider'\''"')" = 1

  # 3. 이름 두 번 바꾸기를 한 트랜잭션으로 (두 번째가 실패하면 첫 번째도 되돌아가 원래 DB 이름이 남는다)
  #    psql -1은 -c 두 개를 한 트랜잭션으로 실행한다. $POSTGRES_DB는 db 컨테이너 안에서 채워진다
  docker compose exec -T db sh -c 'psql -1 -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres -c "ALTER DATABASE \"$POSTGRES_DB\" RENAME TO pawmento_failed_109" -c "ALTER DATABASE pawmento_restore RENAME TO \"$POSTGRES_DB\""'

  docker compose up -d --no-build
  ```
  - 1·2에서 멈춤: 원래 DB(이전 후 상태)는 그대로이고 앱은 멈춰 있다. 원인을 확인한 뒤 1부터 다시 한다.
  - 3에서 멈춤: 트랜잭션이 롤백돼 두 DB 이름이 모두 그대로다. 원인을 확인한 뒤 3부터 다시 한다.
  - 확인: `member`에 `provider`·`provider_user_id`가 다시 있고 `social_account`가 없다. 이전 이미지에서 이메일 회원 로그인과, 이전 전에 가입한 소셜 회원 로그인이 같은 id로 되는지 직접 확인한다.
  - 백업 이후 새 앱이 받은 데이터는 되돌린 DB에 없다. 원인 확인용으로 `pawmento_failed_109`를 남겨 두고, 확인이 끝나면 지운다.
- 되돌린 뒤 원인을 고쳐 PR을 다시 올리고, 이 절차를 처음부터 다시 한다. (고침 #109: 리뷰 — 복원 방식, IMAGE_TAG, 설정 파일 맞추기, 실패 시 중단, 이름 바꾸기 원자성)
