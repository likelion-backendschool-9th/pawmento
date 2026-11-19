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
1. 연결 시작: `POST /api/v1/members/me/social-accounts/{provider}/link-state` → 200 `{"state": "…"}`. 서버는 추측할 수 없는 일회용 값을 만들어 `(state, member_id, provider, expires_at)`으로 저장한다(유효 10분).
2. 화면은 이 state를 제공자 인가 URL에 실어 보내고, 콜백에서 받은 `code`·`state`를 연결 API에 넘긴다.
3. 연결: 서버는 제공자에 code를 보내기 **전에** state를 꺼내 지운다(한 번만 쓰임). 없거나, 만료됐거나, 회원·제공자가 다르면 403이고 아무것도 저장하지 않는다.
- JWT 쿠키 방식이라 서버 세션이 없으므로 state는 DB(`social_link_state`)에 둔다.

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

#109-S11 동시 연결 [테스트]
- Given 두 회원이 같은 카카오 계정을, 또는 한 회원이 카카오 두 계정을 동시에 연결한다
- Then  하나만 성공하고 나머지는 409다 (`(provider, provider_user_id)`, `(member_id, provider)` 유니크 제약)

#109-S17 연결 요청 확인 실패 [테스트]
- Given 회원 A가 로그인했다
- When  state 없이, 회원 B가 받은 state로, 구글용으로 받은 state로 카카오를, 만료된 state로, 이미 한 번 쓴 state로 각각 연결한다
- Then  모두 403 `FORBIDDEN`이고, A의 목록과 `social_account`는 그대로다
- Then  제공자에 code를 보내지 않는다(가짜 포트 호출 0번)

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

## 범위 밖
- 소셜 회원의 비밀번호 설정: 이메일 인증과 함께 다룬다
- 같은 이메일 자동 연결: 계정 선점 위험으로 하지 않는다(#40 정한 것)
- 마이페이지 연결 화면, 연결용 콜백 처리(서버가 준 state를 인가 URL에 싣고 콜백 값을 넘기기), 로그인 방법이 하나뿐일 때의 안내 문구: 프론트엔드 이슈
- 카카오·네이버 클라이언트: #40 남은 작업(S11·S12). 이 문서의 연결 API는 제공자와 상관없이 `LoadSocialProfilePort`를 쓴다

## 작업 순서
1. 저장 구조 분리 (PR 1, 응답 변화 없음) — S1~S4, S18
   - 파일: `member/domain/Member.java`(provider 제거, `isLocal` → `hasPassword`, `register` 해시 검사), 새 `domain/SocialAccount.java`, 새 `adapter/out/persistence/SocialAccountJpaEntity.java`·`SocialAccountJpaRepository.java`, `MemberJpaEntity.java`, `MemberPersistenceAdapter.java`(소셜 회원 저장을 한 트랜잭션으로), `LoginMemberService.java`, `SocialLoginMemberService.java`
   - 테스트: `SocialLoginScenarioTest`의 #40-S1 확인 대상 변경, `MemberPersistenceTest`에 S18 추가
   - 확인: `./gradlew test`(CI), [개발 서버 이전 절차](#개발-서버-이전-절차)
2. 연결 목록 — S5
   - 파일: 새 `ListSocialAccountsUseCase`·서비스, `MemberController.java`에 엔드포인트 추가 (컨트롤러는 도메인에 하나, docs/backend-architecture.md #73). 연결·해제 엔드포인트도 같은 컨트롤러에 둔다
3. 연결 — S6~S11, S17
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
- 연결 state는 10분 동안 한 번만 쓸 수 있다. 확인은 제공자 호출 전에 하고, 실패하면 403(`PermissionDeniedException`)이다.
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

준비
1. PR 1 머지 전에 자동 배포를 끈다: GitHub 환경 `develop`의 `DEPLOY_HOST`를 잠시 지운다(docs/deploy.md). 머지하면 검사·이미지 업로드만 되고 서버는 그대로다.
2. 지금 서버에서 도는 이미지 태그(`sha-<이전 커밋>`)를 적어 둔다.
3. `social_account` 생성 SQL은 PR 1의 `SocialAccountJpaEntity`로 로컬에서 `ddl-auto: update`가 만든 DDL(컬럼·유니크 제약 2개·외래 키·CHECK)을 그대로 복사해 둔다.

이전 (서버)
1. 백엔드 컨테이너만 멈춘다: `docker compose -f compose.yaml -f compose.prod.yaml stop backend`
2. DB를 백업한다: `pg_dump -Fc`로 `member-before-109.dump`를 서버에 남긴다
3. 한 트랜잭션으로 실행한다:
   ```sql
   BEGIN;
   -- 준비 3의 social_account CREATE TABLE
   INSERT INTO social_account (member_id, provider, provider_user_id, linked_at)
   SELECT id, provider, provider_user_id, created_at FROM member WHERE provider <> 'LOCAL';
   -- 검증: 두 수가 같아야 한다
   SELECT (SELECT count(*) FROM member WHERE provider <> 'LOCAL') AS before,
          (SELECT count(*) FROM social_account) AS after;
   ALTER TABLE member DROP COLUMN provider, DROP COLUMN provider_user_id;
   COMMIT;   -- 수가 다르면 COMMIT 대신 ROLLBACK
   ```
4. 새 이미지로 띄운다: `IMAGE_TAG=sha-<PR 1 커밋> docker compose -f compose.yaml -f compose.prod.yaml up -d --no-build`
5. #109-S2를 확인한다(이전 전에 가입한 소셜 회원이 같은 id로 로그인).
6. `DEPLOY_HOST`를 다시 넣는다.

실패하면
- 3에서 실패(검증 수가 다름, SQL 오류): `ROLLBACK`하면 DB는 그대로다. 이전 이미지로 다시 띄운다: `IMAGE_TAG=sha-<이전 커밋> … up -d --no-build`
- 4·5에서 실패: 백엔드를 멈추고 백업으로 DB를 되돌린 뒤(`pg_restore --clean -d <DB> member-before-109.dump`), 이전 이미지로 다시 띄운다. DB와 앱을 **함께** 되돌려야 한다(새 앱은 옛 스키마에서, 옛 앱은 새 스키마에서 저장이 실패한다).
- 되돌린 뒤 원인을 고쳐 PR을 다시 올리고, 이 절차를 처음부터 다시 한다.
