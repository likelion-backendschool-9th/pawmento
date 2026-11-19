# 소셜 로그인 (구글·카카오·네이버)

관련 이슈: [#40](https://github.com/likelion-backendschool-9th/pawmento/issues/40)

## 한눈에
- 끝나면: 보호자가 구글·카카오·네이버 계정으로 처음 들어오면 가입과 동시에 로그인되고, 다시 들어오면 같은 회원으로 로그인된다. 로그인 결과는 이메일 로그인과 같은 `ACCESS_TOKEN` 쿠키다.
- 결정 필요: 없음
- 규모: 7단계

## 1. 처음 소셜 로그인 (#40-R1)
제공자의 인가 코드로 로그인하면, 처음 온 사람은 소셜 회원으로 가입되고 로그인 쿠키를 받는다.

#40-S1 정상 [테스트]
- Given 구글이 `sub: "g-123"`, `email: "dawit@gmail.com"`(확인됨), `name: "다윗"`을 돌려준다
- When  `POST /api/v1/auth/oauth/google`에 `{"code":"abc","state":"x7k2","redirectUri":"http://localhost:3000/auth/callback/google"}`를 보낸다
- Then  200과 회원 정보(`id`, `email`, `nickname: "다윗"`, `role: "USER"`)를 받는다
- Then  `ACCESS_TOKEN` 쿠키(HttpOnly, SameSite=Lax)를 받고, 그 쿠키로 `GET /api/v1/members/me`가 된다
- Then  `member`에 `provider: GOOGLE`, `provider_user_id: "g-123"`, `password_hash: null`인 행이 하나 생긴다

#40-S2 닉네임 맞추기 [테스트]
- Given 제공자가 이름을 주지 않거나 21자 이상을 준다
- When  처음 소셜 로그인한다
- Then  닉네임이 "보호자"이거나 앞 20자로 저장되고 가입은 성공한다

## 2. 다시 소셜 로그인 (#40-R2)
같은 제공자 계정으로 다시 로그인하면 새 회원이 생기지 않는다.

#40-S3 정상 [테스트]
- Given S1로 가입한 회원이 있다
- When  같은 `sub: "g-123"`으로 다시 로그인한다
- Then  같은 `id`를 받고 `member` 행 수가 늘지 않는다

#40-S4 이메일이 바뀐 경우 [테스트]
- Given S1로 가입한 회원이 있고, 구글이 같은 `sub`에 다른 이메일을 준다
- When  다시 로그인한다
- Then  같은 회원으로 로그인된다

## 3. 이메일 하나에 계정 하나 (#40-R3)
가입 방법(로컬·구글·카카오·네이버)과 상관없이 같은 이메일로는 회원이 하나만 있다. 이미 있는 이메일이면 연결하지 않고 막는다.

#40-S5 소셜 로그인 실패 [테스트]
- Given `dawit@gmail.com`으로 이메일 가입한 회원이 있다
- When  같은 이메일의 구글 계정으로 처음 소셜 로그인한다
- Then  409와 `code: "CONFLICT"`, "처음 가입한 방법으로 로그인해 주세요" 문구를 받는다
- Then  쿠키를 받지 못하고 `member` 행 수가 늘지 않는다
- Given 같은 이메일로 구글 가입한 회원이 있고, 카카오로 처음 로그인해도 같다

#40-S6 로컬 가입 실패 [테스트]
- Given `dawit@gmail.com`으로 구글 가입한 회원이 있다
- When  `POST /api/v1/auth/signup`에 같은 이메일로 가입한다
- Then  409와 `code: "CONFLICT"`를 받는다

## 4. 로그인 실패 (#40-R4)
제공자 인증에 실패하거나 필요한 정보가 없으면 로그인되지 않는다.

#40-S7 제공자가 거절 [테스트]
- Given 구글이 code를 거절한다(만료, 재사용, redirectUri 불일치)
- Then  401과 `code: "UNAUTHORIZED"`를 받고 쿠키를 받지 못한다

#40-S8 확인된 이메일 없음 [테스트]
- Given 제공자가 이메일을 주지 않거나, 확인된 이메일이 아니다(구글 `email_verified: false`, 카카오 `is_email_valid`·`is_email_verified` 중 하나라도 false, 네이버 `@naver.com`이 아닌 주소)
- Then  401과 "이메일 제공에 동의해야 가입할 수 있습니다" 문구를 받고 가입되지 않는다

#40-S9 지원하지 않는 제공자 [테스트]
- When  `POST /api/v1/auth/oauth/facebook` 또는 `/oauth/local`을 보낸다
- Then  400과 `code: "VALIDATION"`을 받는다

## 5. 로컬과 소셜 분리 (#40-R5)
소셜로만 가입한 회원은 이메일·비밀번호로 로그인할 수 없다.

#40-S10 실패 [테스트]
- Given S1로 가입한 소셜 회원(`password_hash: null`)이 있다
- When  `POST /api/v1/auth/login`에 그 이메일과 비밀번호 `"wrong-password"`를 보낸다
- Then  이메일 로그인 실패와 같은 401을 받는다
- When  비밀번호를 `"absent-member-password"`(더미 비교에 쓰는 문자열)로 보낸다
- Then  역시 401을 받고 쿠키를 받지 못한다

## 6. 카카오·네이버 (#40-R6)
카카오·네이버도 R1~R5와 같이 동작한다. 제공자별 응답 차이는 어댑터 안에서 끝난다.

#40-S11 카카오 [테스트]
- Given 카카오가 `id`, `kakao_account.email`, `is_email_valid: true`, `is_email_verified: true`, `profile.nickname`을 준다
- Then  S1과 같이 `provider: KAKAO`로 가입된다
- Given `is_email_verified: false`면 S8과 같이 가입되지 않는다

#40-S12 네이버 [테스트]
- Given 네이버가 `response.id`, `response.email: "dawit@naver.com"`, `response.nickname`을 준다
- Then  S1과 같이 `provider: NAVER`로 가입된다. 토큰 교환 요청에 `state`가 함께 간다
- Given `response.email`이 `@naver.com`이 아니면 S8과 같이 가입되지 않는다

## 7. 설정 (#40-R7)
제공자 키는 환경 변수로만 받고, 키가 없어도 앱은 켜진다.

#40-S13 키 없음 [명령]
- Given `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`이 비어 있다
- When  `./gradlew bootRun`으로 켜고 구글 로그인을 부른다
- Then  앱은 정상으로 켜지고, 구글 로그인만 401로 실패한다

#40-S14 실제 구글 [직접]
- Given 로컬에 Google Cloud Console의 OAuth 클라이언트 키를 넣었다
- When  브라우저로 구글 로그인 후 받은 code로 S1 요청을 보낸다
- Then  가입되고 쿠키를 받는다

#40-S15 같은 계정 동시 첫 로그인 [테스트]
- Given 가입하지 않은 구글 계정 `sub: "g-123"`이 있다
- When  같은 계정으로 첫 소셜 로그인 요청 두 개가 동시에 온다
- Then  두 요청 모두 200과 같은 `id`를 받는다
- Then  `member` 행은 하나만 생긴다

#40-S16 같은 이메일 동시 가입 [테스트]
- Given `dawit@gmail.com`으로 가입한 회원이 없다
- When  같은 이메일의 구글 계정과 카카오 계정으로 첫 소셜 로그인이 동시에 온다
- Then  한쪽만 200으로 가입되고, 다른 쪽은 409와 `code: "CONFLICT"`를 받는다
- Then  `member` 행은 하나만 생긴다

## 범위 밖
- 계정 연결·해제(한 회원에 여러 로그인 방법 붙이기): [소셜 계정 연결·해제](social-account-link.md)에서 다룬다 (고침 #109: 11/18 회의에서 MVP에 넣기로 함)
- 소셜 회원의 비밀번호 설정, refresh 토큰, 회원 탈퇴: 스프린트 1 이후 member에서 다룬다
- 로그인 화면과 콜백 화면(`/auth/callback/{provider}`), state 생성·비교: 프론트엔드 이슈에서 다룬다
- iOS 홈 화면 PWA에서 리다이렉트 동작 확인: 프론트엔드 이슈에서 다룬다
- 네이버 로그인(#40-S12): 이번 범위에서 빼고 후속으로 다룬다. 정한 것의 네이버 이메일 기준과 참고의 네이버 응답 형식은 그때 그대로 쓴다 (고침 #40: 팀 계획 범위는 구글·카카오)

## 작업 순서
1. 회원에 가입 방식과 소셜 계정 정보 추가, 소셜 회원의 비밀번호 로그인 차단 — S10의 기반
   - 파일: `member/domain/Member.java`, `AuthProvider.java`, `adapter/out/persistence/MemberJpaEntity.java`, `LoadMemberPort.java`, `application/service/LoginMemberService.java`, `adapter/out/security/BCryptPasswordHasher.java`
   - 확인: `./gradlew test` (기존 가입·로그인 테스트가 그대로 통과)
2. 소셜 로그인 유스케이스와 예외 — S5, S7~S9의 응답 형식
   - 파일: `port/in/SocialLoginMember{Command,UseCase}.java`, `application/exception/Social…`, `MemberExceptionHandler.java`
   - 확인: 같은 명령
3. 구글 프로필 어댑터 — S7, S8, S13
   - 파일: `adapter/out/oauth/*`, `application.yml`
   - 확인: `./gradlew test --tests '*GoogleProfileClientTest'`
4. 소셜 로그인 서비스 — S1~S6, S15, S16
   - 파일: `application/service/SocialLoginMemberService.java`, `adapter/out/persistence/MemberPersistenceAdapter.java`, `application/exception/DuplicateSocialAccountException.java`
5. 소셜 로그인 API와 공개 경로 — S1, S9
   - 파일: `adapter/in/web/AuthController.java`, `SocialLoginMemberRequest.java`, `global/security/PublicEndpoints.java`, `docs/member/README.md`
   - 확인: 같은 명령
6. 카카오·네이버 클라이언트 — S11, S12
   - 파일: `adapter/out/oauth/KakaoProfileClient.java`, `NaverProfileClient.java`
   - 확인: `./gradlew test --tests '*ProfileClientTest'`
7. 실제 구글 계정으로 확인 — S14

## 정한 것
- 제공자가 확인한 이메일만 쓴다. 구글은 `email_verified`, 카카오는 `is_email_valid`와 `is_email_verified`가 모두 true일 때다. 네이버는 확인 표시를 주지 않으므로 `@naver.com` 주소만 확인된 이메일로 보고, 다른 주소면 가입을 막는다. 확인된 이메일이 없으면 가입하지 않는다. 확인되지 않은 이메일을 받으면 다른 사람의 이메일을 먼저 선점해 그 사람의 가입을 막을 수 있기 때문이다.
- 소셜 회원(비밀번호 없음)은 이메일 로그인에서 없는 회원과 같이 다룬다. 응답 시간을 맞추는 더미 비교는 하되 결과와 상관없이 401이다.
- 같은 소셜 계정의 첫 로그인이 동시에 오면 진 쪽은 저장된 회원을 다시 찾아 같은 회원으로 로그인한다. 같은 이메일의 다른 제공자 가입이 동시에 오면 진 쪽은 409다.
- 소셜 로그인은 Spring Security의 `oauth2Login()` 대신 인가 코드 방식으로 처리한다. 프론트가 받은 code를 백엔드가 교환해서, 제공자마다 출력 어댑터 하나가 되게 한다.
- 로컬 회원과 소셜 회원은 생성 메서드부터 나눈다. 소셜 회원은 비밀번호 해시가 없고(`password_hash` nullable), 로컬 가입은 도메인에서 해시를 반드시 받는다.
- 소셜 회원은 이메일이 아니라 `(provider, provider_user_id)`로 찾는다. 제공자에서 이메일이 바뀌어도 사용자 id는 그대로라서다. DB에도 이 둘에 유니크 제약을 둔다.
- 제공자가 확인한 이메일만 쓴다(구글 `email_verified`). 이메일이 없으면 가입하지 않는다.
- 소셜 로그인 서비스에는 `@Transactional`을 걸지 않는다. 제공자 응답을 기다리는 동안 DB 연결을 붙잡지 않기 위해서다. 저장은 저장 메서드 안에서 끝난다.
- 토큰 발급은 이메일 로그인과 같은 `IssueTokenPort`와 `ACCESS_TOKEN` 쿠키를 쓴다. 다른 도메인은 로그인 방법을 몰라도 된다.
- 포트(`LoadSocialProfilePort`) 구현체는 하나(`SocialProfileAdapter`)만 두고, 제공자별 클라이언트를 모아 provider로 골라 부른다.

## 가정
- 소셜 닉네임은 제공자 값을 쓰되, 없으면 "보호자", 20자가 넘으면 앞 20자로 자른다. 마이페이지에서 바꿀 수 있다.
- 예외는 지금 `MemberExceptionHandler`로 응답한다. 공통 기본 예외(#60, #33)가 develop에 들어오면 409·400 예외는 기본 예외 상속으로 옮긴다. 소셜 로그인 실패 401은 `InvalidCredentialsException`과 함께 정한다.
- 테스트는 실제 제공자를 부르지 않는다. 서비스는 가짜 포트, 어댑터는 가짜 서버(`MockRestServiceServer`)로 확인한다.

## 참고
- 구글 토큰 교환은 `POST https://oauth2.googleapis.com/token`에 폼 형식(`application/x-www-form-urlencoded`)으로 `grant_type=authorization_code`, `code`, `client_id`, `client_secret`, `redirect_uri`를 보낸다. `redirect_uri`는 로그인할 때 쓴 값과 같아야 한다.
- 구글 사용자 정보는 `GET https://openidconnect.googleapis.com/v1/userinfo`에 `Authorization: Bearer <구글 액세스 토큰>`으로 받는다. `sub`는 바뀌지 않는 사용자 id다.
- 네이버는 토큰 교환 때 `state`도 보내야 한다. 응답이 `response` 객체 안에 한 번 더 감싸져 있다.
- 카카오는 이메일을 필수 동의로 받으려면 비즈 앱 전환이 필요할 수 있다. 콘솔에서 확인한다.
- `ddl-auto: update`는 기존 컬럼의 NOT NULL을 풀어 주지 않는다. 이 변경을 받은 뒤 로컬 DB는 `docker compose down -v`로 비우고, 개발 서버 DB도 따로 처리해야 한다.
- 환경 변수 이름은 `GOOGLE_CLIENT_ID`·`GOOGLE_CLIENT_SECRET`(카카오·네이버도 같은 형식)이다. 개발 서버 `.env.prod.example`에도 더한다.
- 카카오 사용자 정보의 `kakao_account`에는 `has_email`, `email_needs_agreement`, `is_email_valid`, `is_email_verified`, `email`이 온다.
- 네이버 프로필 응답의 `response.email`에는 확인 여부 필드가 없다.