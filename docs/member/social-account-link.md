# 소셜 계정 연결·해제

관련 이슈: [#109](https://github.com/likelion-backendschool-9th/pawmento/issues/109)

## 한눈에
- 끝나면: 로그인한 보호자가 마이페이지에서 구글·카카오·네이버 계정을 연결하고 해제한다. 연결한 방법이면 무엇으로 들어와도 같은 회원이다.
- 결정 필요: 개발 서버 DB 이전을 직접 SQL로 할지, 이번에 마이그레이션 도구를 들일지(Q1)
- 규모: 5단계 (PR 2개)

## 질문
- Q1: 이번 변경은 `member`의 `provider`·`provider_user_id`를 `social_account`로 옮기고 두 컬럼을 지운다. `ddl-auto: update`는 컬럼을 지우지 않고, 남은 `provider`는 NOT NULL이라 새 코드의 회원 저장이 실패한다. 개발 서버에서 이전 SQL을 직접 실행할까요, 이번에 Flyway를 들여 SQL을 저장소에 둘까요? (답에 따라 #109-S2의 확인 방법과 작업 순서 1단계가 달라짐)

## 1. 로그인 방법을 회원에서 분리 (#109-R1)
소셜 계정을 `social_account`에 두고, 기존 소셜 로그인은 그대로 동작한다.

#109-S1 기존 동작 유지 [테스트: SocialLoginScenarioTest #40-S1·S3·S5~S7·S9·S10]
- Given #40의 소셜 로그인 시나리오
- Then  응답·쿠키·상태 코드가 이전과 같다
- Then  처음 소셜 로그인하면 `member` 한 줄과 `social_account` 한 줄이 함께 생긴다

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

#109-S6 정상 [테스트]
- Given 이메일 회원 A가 로그인했다
- When  `POST /api/v1/members/me/social-accounts/kakao`에 `{"code":"abc","redirectUri":"…"}`
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

#109-S11 동시 연결 [테스트]
- Given 두 회원이 같은 카카오 계정을, 또는 한 회원이 카카오 두 계정을 동시에 연결한다
- Then  하나만 성공하고 나머지는 409다 (`(provider, provider_user_id)`, `(member_id, provider)` 유니크 제약)

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

## 6. 같은 이메일 안내 (#109-R6)
처음 소셜 로그인이 같은 이메일 때문에 막히면 연결 방법을 안내한다.

#109-S15 안내 문구 [테스트]
- Given 이메일 회원 A와 같은 이메일의 구글 계정
- When  그 구글 계정으로 처음 소셜 로그인한다
- Then  409이고 메시지에 "로그인한 뒤 마이페이지에서 연결" 안내가 있다

## 범위 밖
- 소셜 회원의 비밀번호 설정: 이메일 인증과 함께 다룬다
- 같은 이메일 자동 연결: 계정 선점 위험으로 하지 않는다(#40 정한 것)
- 마이페이지 연결 화면, 연결용 콜백 처리, 로그인 방법이 하나뿐일 때의 안내 문구: 프론트엔드 이슈
- 카카오·네이버 클라이언트: #40 남은 작업(S11·S12). 이 문서의 연결 API는 제공자와 상관없이 `LoadSocialProfilePort`를 쓴다

## 작업 순서
1. 저장 구조 분리 (PR 1, 응답 변화 없음) — S1~S4
   - 파일: `member/domain/Member.java`(provider 제거, `isLocal` → `hasPassword`, `register` 해시 검사), 새 `domain/SocialAccount.java`, 새 `adapter/out/persistence/SocialAccountJpaEntity.java`·`SocialAccountJpaRepository.java`, `MemberJpaEntity.java`, `MemberPersistenceAdapter.java`(소셜 회원 저장을 한 트랜잭션으로), `LoginMemberService.java`, `SocialLoginMemberService.java`
   - 확인: `./gradlew test`(CI), 개발 서버 이전 [Q1]
2. 연결 목록 — S5
   - 파일: 새 `ListSocialAccountsUseCase`·서비스, `MemberController.java` 또는 새 `SocialAccountController.java`
3. 연결 — S6~S11
   - 파일: 새 `LinkSocialAccountUseCase`·서비스, `SocialAccountAlreadyLinkedException`(StateConflict)
4. 해제 — S12~S14
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
- 연결할 때는 제공자 이메일을 쓰지 않는다. 회원 이메일과 달라도 연결된다. 로그인은 `(provider, provider_user_id)`로만 찾는다.
- 같은 계정을 다시 연결하면 성공으로 본다(멱등). 연결되지 않은 제공자를 해제하면 204다.
- 연결 응답은 목록 전체를 돌려줘 화면이 다시 조회하지 않아도 되게 한다.
- 처음 소셜 가입은 `member`와 `social_account`를 한 트랜잭션으로 저장한다. 서비스에는 여전히 `@Transactional`을 걸지 않고, 영속성 어댑터의 저장 메서드가 묶는다.

## 참고
- `ddl-auto: update`는 새 테이블·컬럼·제약은 만들지만 컬럼 삭제와 NOT NULL 변경은 하지 않는다. 남은 `member.provider`(NOT NULL) 때문에 새 코드의 저장이 실패한다.
- 개발 서버에서 `docker compose down -v`는 쓰지 않는다(`media-data` 볼륨까지 지워진다, docs/deploy.md).
- 직접 SQL로 할 때의 이전 순서: `social_account` 생성(앱 기동 시 update) → `INSERT INTO social_account (member_id, provider, provider_user_id, linked_at) SELECT id, provider, provider_user_id, created_at FROM member WHERE provider <> 'LOCAL';` → `ALTER TABLE member DROP COLUMN provider, DROP COLUMN provider_user_id;`. 순서상 새 앱이 뜬 뒤 첫 저장 전에 실행해야 한다.
- Hibernate는 `@Enumerated(STRING)`에 허용 값 CHECK 제약을 만든다. 새 제공자를 더하면 그 제약도 바꿔야 한다.