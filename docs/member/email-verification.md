# 이메일 인증번호

관련 이슈: [#279](https://github.com/likelion-backendschool-9th/pawmento/issues/279) 로컬 회원가입 이메일 인증, [#280](https://github.com/likelion-backendschool-9th/pawmento/issues/280) 비밀번호 재설정

## 한눈에
- 끝나면: 로컬 가입은 이메일로 받은 6자리 인증번호를 확인한 사람만 끝낼 수 있다. 같은 구조로 #280에서 비밀번호 재설정·설정을 한다
- 결정 필요: 없음 (Q1·Q2 정함)
- 규모: 5단계 (#279 PR 5개, #280은 이어서 백엔드·화면 2개)

## 질문
- Q1(정함): 다시 받기 간격(60초) 안의 요청은 409 `CONFLICT`와 "잠시 후 다시 요청해 주세요."다. 응답은 공통 오류 형식(`code`·`message`)만 쓰고 시각을 싣지 않는다. 화면은 직전 202 응답의 `retryAfter`로 버튼을 막는다. 공통 오류 형식에 필드를 더하거나 429를 새로 두려면 global 담당과 합의해야 해서 이번에는 하지 않는다 (고침 #279: 공통 오류 형식 유지)
- Q2(정함): 메일 서버가 응답하지 않으면 503 `STORAGE_UNAVAILABLE`. 엽서 생성 실패(`PostcardGenerationFailedException`)처럼 `StorageUnavailableException`을 상속해 `ErrorCode`를 바꾸지 않는다

## 1. 인증번호 보내기 (#279-R1)
이메일과 용도를 받아 6자리 인증번호를 메일로 보낸다. 용도는 `SIGNUP`·`PASSWORD_RESET`·`SET_PASSWORD`이고 #279는 `SIGNUP`만 받는다.

#279-S1 보내기 [테스트]
- Given 가입되지 않은 이메일 `a@test.com`
- When  `POST /api/v1/auth/email-verifications`에 `{"email": "A@test.com", "purpose": "SIGNUP"}`
- Then  202와 `{"email": "a@test.com", "expiresAt": "…", "retryAfter": "…"}`를 받는다
- Then  `a@test.com`으로 6자리 숫자가 담긴 메일이 한 통 간다. 응답·로그에는 인증번호가 없다

#279-S2 이미 가입된 이메일 [테스트]
- Given `a@test.com`으로 가입한 회원이 있다
- Then  `SIGNUP` 요청은 409 `CONFLICT` "이미 가입된 이메일입니다."이고 메일이 가지 않는다

#279-S3 다시 받기 [테스트]
- Given S1 직후(60초 안)
- Then  다시 요청하면 Q1의 응답이고 메일이 가지 않는다
- Given 60초가 지났다(테스트용 Clock)
- Then  다시 요청하면 202이고 새 번호가 간다. 이전 번호로 확인하면 S5처럼 실패한다

#279-S4 메일 발송 실패 [테스트]
- Given 메일 발송 포트가 실패하도록 둔다
- Then  Q2의 응답이고, 저장한 인증 줄은 지워져 바로 다시 요청할 수 있다
- Given 메일 서버가 메일을 받은 뒤 연결 닫기만 실패한다(`JavaMailSenderImpl`이 실패 메일 목록 없이 `MailSendException`을 던진다)
- Then  보낸 것으로 보고 202다. 사용자는 이미 번호를 받았으므로 줄을 지우지 않는다

## 2. 인증번호 확인 (#279-R2)
맞는 번호를 넣으면 가입에 한 번 쓸 인증 토큰을 받는다.

#279-S5 확인 [테스트]
- When  `POST /api/v1/auth/email-verifications/confirm`에 `{"email": "a@test.com", "purpose": "SIGNUP", "code": "123456"}`
- Then  200과 `{"verificationToken": "…", "expiresAt": "…"}`를 받는다 (토큰 유효 30분)
- Then  틀린 번호, 만료된 번호(5분, 고침 #279: 6자리를 맞혀 볼 수 있는 시간을 줄이려고 10분에서 줄임), 보낸 적 없는 이메일은 모두 400 `VALIDATION` "인증번호가 맞지 않거나 만료됐어요."로 같다

#279-S6 시도 횟수 [테스트]
- Given 틀린 번호를 5번 넣었다
- Then  6번째부터는 맞는 번호도 400이다. 새로 받으면 횟수가 0부터 다시 센다
- Given 같은 인증에 틀린 번호 확인이 동시에 10번 온다
- Then  실패로 세는 것은 5번까지고, 그 뒤 맞는 번호도 400이다 (횟수는 한 문장 조건부 UPDATE로 올린다)

#279-S7 한 번만 확인 [테스트]
- Given S5로 이미 확인했다
- Then  같은 번호로 다시 확인하면 400이다

## 3. 가입에 인증 요구 (#279-R3)
가입 요청은 S5에서 받은 토큰을 함께 보낸다.

#279-S8 인증한 이메일로 가입 [테스트]
- When  `POST /api/v1/auth/signup`에 `{"email": "a@test.com", "password": "…", "nickname": "…", "verificationToken": "…"}`
- Then  201이고 토큰은 쓴 상태가 된다. 같은 토큰으로 다시 가입하면 400이다

#279-S9 인증 없이 또는 다른 이메일로 가입 [테스트]
- Then  토큰이 없거나, 만료됐거나, 다른 이메일·다른 용도의 토큰이면 400 `VALIDATION` "이메일 인증을 다시 해 주세요."이고 회원이 생기지 않는다

#279-S10 같은 토큰 동시 가입 [테스트]
- Given 실제 PostgreSQL. 같은 토큰으로 가입이 동시에 두 번 온다
- Then  하나만 201이고 다른 하나는 400 또는 409다. 회원은 한 명이다
- Given 태그 충돌로 첫 시도가 롤백되고 다시 시도한다(#137-S10)
- Then  롤백된 시도에서 쓴 토큰도 되돌아가 다시 시도가 성공한다

#279-S11 다른 도메인 테스트의 가입 [테스트]
- 인증은 항상 필수다. 끄는 설정값은 두지 않는다
- `/auth/signup`을 직접 부르던 다른 도메인 시나리오 테스트(map·walk·pet·community·media·postcard 등)는 공용 테스트 도우미 `SignUpTestSupport`로 바꾼다. 도우미는 보내기 → 가짜 메일 포트에서 번호 읽기 → 확인 → 토큰으로 가입을 한 번에 한다
- 각 테스트의 가입 도우미 메서드만 도우미 호출로 바꾸고, 테스트가 확인하는 내용은 바꾸지 않는다. 담당자 동의를 PR에 남긴다 (팀 테스트가 끝난 뒤 진행)

## 4. 가입 화면 (#279-R4)
#279-S12 화면 흐름 [테스트·직접 확인]
- 가입 화면의 이메일 옆에 [인증번호 받기]가 있고, 누르면 인증번호 입력칸과 남은 시간이 보인다
- [확인]이 성공하면 이메일 칸이 잠기고 "인증 완료"가 보인다. 이메일을 바꾸려면 [다시 인증]으로 처음부터 한다
- 인증 전에는 가입 버튼이 눌리지 않는다. 다시 받기는 `retryAfter`까지 비활성이다
- `memberApi.signUp`은 토큰을 함께 보낸다. 서버의 400 문구를 그대로 보여 준다

## 저장
`email_verification` 테이블 하나. 이메일·용도마다 한 줄(unique `(email, purpose)`)이고, 다시 받으면 그 줄을 새 값으로 바꾼다.

| 컬럼 | 뜻 |
|---|---|
| `email`, `purpose` | 소문자 이메일, 용도 |
| `code_hash` | 인증번호의 HMAC-SHA256. 6자리는 경우의 수가 적어 그냥 해시하면 DB만 보고 맞힐 수 있으므로 서버 비밀키로 HMAC한다(초대 코드 `InviteCodes`와 같은 방식, 다른 용도 문자열) |
| `failed_attempts` | 틀린 횟수 |
| `code_expires_at`, `sent_at` | 번호 만료, 보낸 시각(다시 받기 간격 계산) |
| `token_hash`, `token_expires_at` | 확인 뒤 내준 토큰(32바이트 무작위)의 SHA-256, 만료 |
| `verified_at`, `used_at` | 확인한 시각, 가입에 쓴 시각 |

- 확인·사용은 모두 한 문장의 조건부 UPDATE이고 바뀐 줄 수로 성공을 판단한다(가족 초대 코드의 `deleteUsable`과 같은 방식)
- 토큰 사용은 가입의 한 번 시도(`signUpOnce`) 트랜잭션 안에서 한다. 시도가 롤백되면 사용도 롤백된다
- 메일은 줄을 커밋한 뒤 트랜잭션 밖에서 보낸다. 메일 서버를 기다리는 동안 DB 연결을 잡지 않는다

## 메일 발송
- 출력 포트 `SendVerificationMailPort`. 서비스는 메일 형식을 모른다
- 어댑터: `spring-boot-starter-mail`의 `JavaMailSender`로 Gmail SMTP(`smtp.gmail.com:587`, STARTTLS)에 보낸다. 계정 주소와 앱 비밀번호는 환경 변수(`SPRING_MAIL_USERNAME`·`SPRING_MAIL_PASSWORD`)로만 받는다. 저장소와 이미지에는 두지 않는다
- 값이 비어 있어도 서버는 뜨고, 보내기만 Q2의 503이다. 연결·응답 대기는 5초에서 끊는다
- STARTTLS를 필수로 둔다(`mail.smtp.starttls.required`). 서버가 STARTTLS를 주지 않으면 평문으로 계속하지 않고 실패해 계정과 번호를 보호한다
- 보내는 사람은 `PAWMENTO_MAIL_FROM`, 비우면 로그인 계정 주소다
- 테스트: 보낸 메일을 기억하는 가짜 포트(`@Primary` 테스트 빈). 자동 테스트는 실제 메일을 보내지 않는다
- 로컬·개발 서버 모두 실제 메일로 보낸다. 로컬은 루트 `.env`(커밋하지 않음)에 SMTP 계정을 넣고, 개발 서버는 서버 담당과 정한 계정을 서버 `.env`에 넣는다. `compose.yaml`이 값을 백엔드로 넘긴다 (고침 #279: 처음 계획은 로컬 Mailpit이었다. 스팸함 분류·도착 지연·발신자 표시처럼 실제 메일함에서만 보이는 문제를 서버와 같은 경로로 먼저 확인하려고 member 담당이 실제 발송으로 정했다. 계정이 없는 로컬은 서버가 정상으로 뜨고 보내기만 503이며, 다른 기능 개발에는 영향이 없다. 자동 테스트는 가짜 포트를 써 메일을 보내지 않는다)

## 작업 순서
머지할 때마다 서버가 그대로 떠야 하므로, 서비스가 쓰는 저장·메일 구현은 그 서비스와 같은 PR이나 앞 PR에 둔다.

1. 이 문서, 도메인 `EmailVerification`·`VerificationPurpose`, 인증번호 생성·HMAC `VerificationCodes`, 저장(출력 포트, `email_verification` 엔티티·리포지토리·어댑터) (PR 1)
   - 직접 확인: 단위 테스트. 로컬에서 백엔드를 띄운 뒤 psql에서 `\d email_verification`으로 테이블과 unique 제약을 본다
2. 보내기 서비스·API, 메일 발송(`SendVerificationMailPort`, Gmail SMTP 어댑터) — S1~S4 (PR 2)
   - 직접 확인: 루트 `.env`에 Gmail 앱 비밀번호를 넣고 로컬 도커에서 브라우저 콘솔 `fetch`로 보내기를 부른 뒤, 내 메일함에서 번호를 본다
3. 확인 서비스·API — S5~S7 (PR 3)
   - 직접 확인: 2에서 받은 번호로 확인을 불러 토큰을 받는다. 틀린 번호 5번 뒤에는 맞는 번호도 400인지 본다
4. 가입에 인증 요구, 다른 도메인 테스트의 가입 도우미 교체 — S8~S11 (PR 4, 팀 테스트가 끝난 뒤)
   - 직접 확인: 토큰 없이 가입하면 400, 토큰을 넣으면 201. psql로 `used_at`을 본다
5. 가입 화면 — S12 (PR 5)
   - 직접 확인: 로컬 도커에서 가입 → 메일함에서 번호 확인 → 입력 → 가입. 개발 서버에서 한 번 더 한다

## 범위 밖
- 비밀번호 재설정·설정(#280, 같은 테이블과 포트를 쓴다), 소셜 가입 인증, 이메일 변경, 기존 회원 일괄 인증
- IP별 요청 제한. 지금은 이메일마다 60초 간격과 5회 시도만 둔다. 메일 폭탄이 문제되면 따로 이슈를 연다
- 만료된 줄 정리 작업. 줄은 이메일·용도마다 하나라 쌓이지 않는다

## 가정
- 숫자(번호 5분, 토큰 30분, 5회, 60초)는 초안이다. 코드에서는 설정값으로 두지 않고 상수로 둔다
- 인증번호의 HMAC 키는 JWT_SECRET에서 다른 용도 문자열(`"pawmento-email-verification"`)로 파생한다
