# member

회원과 인증에서 정한 것, 다른 도메인이 인증을 쓰는 방법, 아직 정하지 않은 것을 모은다.

## 이슈와 문서

| 이슈 | 내용 | 문서 |
| --- | --- | --- |
| [#14](https://github.com/likelion-backendschool-9th/pawmento/issues/14) | 인증 기반: 가입, 로그인, 로그아웃, 내 정보 조회, 현재 회원 id 꺼내기 | [auth-base.md](auth-base.md) |

## 도메인 담당이 쓰는 방법

### 현재 회원 id 꺼내기

컨트롤러 메서드의 `Long` 파라미터에 `@LoginMember`를 붙인다. 지금 로그인한 회원의 id가 들어온다.

```java
import com.pawmento.global.security.LoginMember;

@GetMapping("/api/pets")
List<PetResponse> myPets(@LoginMember Long memberId) {
    return petUseCase.findByOwner(memberId);
}
```

- 서비스(애플리케이션 계층)에는 이 id만 넘긴다. 서비스에서 `SecurityContextHolder` 같은 Spring Security 코드를 직접 쓰지 않는다.
- 로그인 여부는 따로 검사하지 않아도 된다. 공개 경로가 아닌 모든 경로는 로그인하지 않으면 컨트롤러에 닿기 전에 401로 끝난다.
- 테스트에서는 가입·로그인 API를 불러 받은 쿠키를 요청에 붙인다. [`AuthScenarioTest`](../../backend/src/test/java/com/pawmento/member/AuthScenarioTest.java)의 `#14-S1`을 참고한다.

### 소유자 확인은 각 도메인이 한다

인증은 "누가 로그인했는가"까지만 알려 준다. "이 반려동물·일기가 그 회원의 것인가"는 각 도메인이 확인한다. 조회·수정·삭제에서 `memberId`와 대상의 소유자를 비교하고, 다르면 403으로 응답한다. (#14)

가족 공유(반려동물 ↔ 구성원 권한)가 들어오면 이 확인이 "소유자인가"에서 "권한이 있는 구성원인가"로 바뀐다. 확인하는 코드를 도메인마다 한곳에 모아 두면 그때 고치기 쉽다.

### 공개 경로

아래 경로만 로그인 없이 부를 수 있다. 나머지는 모두 로그인이 필요하다. 새 경로를 공개하려면 [`PublicEndpoints`](../../backend/src/main/java/com/pawmento/global/security/PublicEndpoints.java)와 이 표를 함께 고친다. (#14)

| 경로 | 용도 |
| --- | --- |
| `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/logout` | 가입, 로그인, 로그아웃 |
| `GET /v3/api-docs/**`, `GET /swagger-ui/**`, `GET /swagger-ui.html` | API 문서 (Springdoc 기본 경로. 도구는 백엔드 구조 작업에서 더한다) |

### API

| 요청 | 본문 | 성공 응답 |
| --- | --- | --- |
| `POST /api/auth/signup` | `email`, `password`(8~72자), `nickname`(1~20자) | 201, 회원 정보 |
| `POST /api/auth/login` | `email`, `password` | 200, 회원 정보. 토큰은 `ACCESS_TOKEN` 쿠키로 내린다 |
| `POST /api/auth/logout` | 없음 | 204, 쿠키 삭제 |
| `GET /api/members/me` | 없음 | 200, 회원 정보 |

회원 정보는 `{ "id": 1, "email": "…", "nickname": "…", "role": "USER" }`이다. 가입은 로그인 상태를 만들지 않는다. 가입한 뒤 로그인을 부른다.

에러 응답은 `{ "code": "…", "message": "…" }`이다.

| 상태 | code | 경우 |
| --- | --- | --- |
| 400 | `VALIDATION` | 입력값이 규칙에 맞지 않음(여러 항목이 틀리면 본문에 선언된 순서로 가장 앞 항목의 이유), 본문이 JSON 형식이 아님 |
| 401 | `UNAUTHORIZED` | 로그인하지 않음, 토큰 만료·위조, 이메일 또는 비밀번호가 틀림 |
| 403 | `FORBIDDEN` | 로그인했지만 권한이 없음. 역할로 막는 경로는 아직 없다 |
| 404 | `NOT_FOUND` | 회원을 찾을 수 없음 |
| 409 | `CONFLICT` | 이미 가입된 이메일 |

## 정한 것

- 인증 방식은 JWT다. 서버가 로그인 상태를 저장하지 않아, 재배포로 서버가 다시 떠도 로그인이 유지된다(`develop`에 머지할 때마다 개발 서버가 다시 뜬다). (#14)
- 토큰은 응답 본문이 아니라 `HttpOnly` 쿠키(`ACCESS_TOKEN`)로만 내린다. 자바스크립트가 토큰을 읽을 수 없어 XSS로 토큰이 새지 않는다. 프론트엔드는 토큰을 저장하거나 헤더에 싣지 않고, 요청에 쿠키가 실리게만 하면 된다. (#14)
- 쿠키는 `SameSite=Lax`다. 다른 사이트가 보낸 POST·PUT·DELETE 요청에는 쿠키가 붙지 않으므로 CSRF 토큰을 따로 쓰지 않는다. 전제는 두 가지이고 모든 도메인이 지킨다. (#14)
  - **GET 요청은 데이터를 바꾸지 않는다.** 링크를 눌러 들어오는 GET에는 쿠키가 붙는다.
  - **데이터를 바꾸는 요청은 JSON 본문으로 받는다.** 브라우저의 폼은 JSON을 보낼 수 없어, 다른 사이트의 폼으로는 이 요청을 만들 수 없다. 파일 업로드처럼 `multipart/form-data`를 받는 API를 만들 때는 [아직 정하지 않은 것](#아직-정하지-않은-것)의 CSRF 항목을 먼저 정한다.
- 쿠키의 `Secure`는 요청이 HTTPS일 때 붙는다. 로컬(HTTP)에서는 붙지 않는다. 개발 서버는 nginx 뒤에 있어, 백엔드가 forwarded 헤더를 믿도록 설정해야 HTTPS로 인식한다([아직 정하지 않은 것](#아직-정하지-않은-것)의 개발 서버 설정). (#14)
- 토큰에는 회원 id(`sub`)와 역할(`role`)만 담는다. 토큰은 누구나 내용을 읽을 수 있어 이메일 같은 개인정보는 넣지 않는다. 유효 시간은 1시간이다(`pawmento.auth.access-token-ttl`). (#14)
- 서명 키는 환경 변수 `JWT_SECRET`으로만 받고 저장소에 두지 않는다. 32바이트 이상이어야 하고, 짧으면 서버가 시작하지 않는다. 비어 있으면 시작할 때 임시 키를 만든다. 로컬에서는 설정 없이 실행되지만 재시작하면 로그인이 풀린다. 고정된 기본 키를 두지 않는 이유는, 설정이 빠진 서버가 저장소에 공개된 키로 토큰을 서명하게 되기 때문이다. (#14)
- 로그아웃은 쿠키를 지운다. 서버가 토큰을 저장하지 않으므로 이미 발급된 토큰 자체는 만료 시간까지 유효하다. (#14)
- 만료된 쿠키가 남아 있어도 다시 로그인할 수 있도록, 가입·로그인·로그아웃 경로에서는 쿠키의 토큰을 읽지 않는다. (#14)
- 비밀번호는 BCrypt 해시로만 저장한다. BCrypt는 72바이트까지만 쓰므로 가입할 때 72자와 72바이트를 함께 검사한다(한글은 한 글자가 3바이트다). (#14)
- 이메일은 앞뒤 공백을 지우고 소문자로 바꿔 저장·조회한다. 대소문자만 다른 이메일로 두 번 가입할 수 없다. (#14)
- 로그인 실패는 이메일이 없는 경우와 비밀번호가 틀린 경우에 같은 응답을 준다. 가입된 이메일인지 알려 주지 않기 위해서다. (#14)
- 가입 검증 규칙과 회원 응답 모양은 프론트엔드 데모(#12)에 맞췄다. (#14)
- 패키지는 `member/`(`domain`, `application`, `adapter`)와 여러 도메인이 함께 쓰는 `global/security`, `global/error`로 나눴다. 애플리케이션 계층은 포트(`MemberRepository`, `PasswordHasher`, `TokenIssuer`)만 알고 JPA·BCrypt·JWT 구현은 어댑터에 있다. (#14)

## 데이터 구조 요약

`member` 테이블

| 컬럼 | 설명 |
| --- | --- |
| `id` | 회원 id. 자동 증가 |
| `email` | 로그인 이메일. 소문자로 저장, 유니크(`uk_member_email`) |
| `password_hash` | BCrypt 해시 |
| `nickname` | 20자 이내 |
| `role` | `USER`, `ADMIN`. 가입하면 `USER` |
| `created_at` | 가입 시각 |

로그인에 필요한 최소 필드만 둔다. 프로필 사진 같은 나머지 필드는 스프린트 1의 member 설계에서 더한다.

## 아직 정하지 않은 것

- **API 응답·에러 형식, 패키지 구조**: 백엔드 구조 작업에서 정한다. 지금의 에러 형식(`code`, `message`)과 패키지 배치는 임시이고, 정해지면 맞춘다.
- **스키마 관리**: 테이블이 만들어지도록 `ddl-auto: update`를 임시로 켰다. 데이터를 지우지는 않지만 컬럼 이름 변경·삭제는 반영하지 못한다. `ddl-auto`를 계속 쓸지 Flyway 같은 마이그레이션 도구를 쓸지 백엔드 구조 작업에서 정한다.
- **개발 서버 설정**: 개발 서버 배포(#10)가 머지되면 서버 `.env`와 `.env.develop.example`에 `JWT_SECRET`을, `compose.develop.yaml`의 backend 환경 변수에 `JWT_SECRET`과 `SERVER_FORWARD_HEADERS_STRATEGY: native`를 더한다. 없으면 재배포마다 로그인이 풀리고 쿠키에 `Secure`가 붙지 않는다.
- **파일 업로드 API의 CSRF**: `SameSite`는 사이트(등록 도메인) 단위로 판단한다. 개발 서버는 다른 팀과 함께 쓰는 공용 서버라, 다른 팀의 주소가 같은 도메인의 하위 주소면 그쪽에서 보낸 요청에도 쿠키가 붙는다. JSON 요청은 브라우저가 막지만 폼으로 보낼 수 있는 `multipart/form-data` 요청은 막지 못한다. 사진 업로드 API를 만들기 전에 CSRF 토큰이나 출처(Origin) 검사를 더할지 정한다.
- **로그인 시도 제한**: 같은 계정·IP에서 로그인 실패가 반복될 때 막는 장치가 없다. 외부 테스터를 받기 전에 정한다.
- **refresh 토큰**: 지금은 액세스 토큰(1시간)만 있어, 만료되면 다시 로그인한다. 재발급(기능 명세 AUTH-05)은 스프린트 1의 member에서 다룬다. 로그아웃한 토큰을 바로 막는 방법도 그때 함께 정한다.
- **로컬에서 프론트엔드 연결**: 개발 서버는 같은 도메인에서 `/api/`만 백엔드로 넘겨(#10) 쿠키가 그대로 동작한다. 로컬은 프론트엔드(3000)와 백엔드(8080)의 포트가 달라, Next.js가 `/api/` 요청을 백엔드로 넘기는 설정(rewrites)이 필요하다. 프론트엔드 구조를 정할 때 함께 정한다. CORS는 열지 않았다.
- **가입 직후 로그인**: 지금은 가입한 뒤 로그인을 따로 부른다. 가입 응답에서 바로 쿠키를 내릴지는 가입 화면을 붙일 때 정한다.
- **소셜 로그인, 비밀번호 변경·찾기, 이메일 인증, 회원 탈퇴, 프로필 수정, 가족 공유 권한, 관리자 계정**: 스프린트 1 이후의 member에서 다룬다.

## 참고

- Spring Security의 리소스 서버는 요청에 토큰이 있으면 공개 경로에서도 먼저 검증하고, 실패하면 401로 끝낸다. 그래서 공개 경로에서는 토큰을 읽지 않게 했다(`CookieBearerTokenResolver`).
- 토큰 검증은 만료 시각에 60초의 허용 오차를 둔다(Spring Security 기본값). 만료된 토큰이 최대 60초 더 통과할 수 있다.
- `ddl-auto: update`로 `member` 테이블을 처음 만들 때 `constraint "uk_member_email" … does not exist, skipping` 경고가 한 번 남는다. 오류가 아니다. 테이블이 이미 있으면 남지 않는다.
- `SERVER_FORWARD_HEADERS_STRATEGY=native`는 Tomcat이 내부망 주소(Docker 네트워크 포함)에서 온 `X-Forwarded-Proto`만 믿게 한다. 이 설정을 켜고 `X-Forwarded-Proto: https`로 요청했을 때 쿠키에 `Secure`가 붙는 것을 확인했다.
