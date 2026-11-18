# 백엔드 구조 (헥사고날 아키텍처)

백엔드 코드를 어떤 패키지에 어떤 클래스로 나누는지 정한 규칙이다. 사람과 AI 모두 새 기능을 만들 때 이 문서대로 만든다.

> 팀이 정한 것은 [정한 것](#정한-것)에, 남은 것은 [아직 정하지 않은 것](#아직-정하지-않은-것)에 둔다. 임시 record 샘플은 실제 도메인·API의 기준으로 사용하지 않는다.

## 핵심 원칙

- 핵심(`domain`, `application`)은 바깥 기술(웹, DB, 인증, 외부 API)을 모른다.
- 핵심은 필요한 것을 인터페이스(포트)로 정하고, 바깥은 그 인터페이스를 구현(어댑터)한다.
- 의존(import) 방향은 바깥에서 안쪽으로만 향한다: `adapter → application → domain`.
- 서비스는 포트를 부르고, 어댑터는 포트를 구현한다.

## 패키지 구조

도메인으로 먼저 나누고, 도메인 안에서 `domain` / `application` / `adapter`로 나눈다. 도메인 이름은 [문서 규칙](README.md#도메인-폴더)대로 문서 폴더, 백엔드 패키지, 프론트엔드 경로에서 같게 쓴다.

```text
com.pawmento
 ├─ global/
 │   ├─ security/                         로그인 회원 정보(@LoginMember 등)
 │   ├─ error/                            공통 에러 응답, 예외 → HTTP 응답 변환
 │   └─ exception/                        기본 예외 (도메인·애플리케이션 예외가 상속)
 └─ <도메인>/
     ├─ domain/
     │   ├─ <Domain>                      도메인 객체 (데이터 + 규칙)
     │   └─ exception/                    도메인 예외 (규칙 위반)
     ├─ application/
     │   ├─ port/
     │   │   ├─ in/
     │   │   │   ├─ <Action><Domain>UseCase    입력 포트 (인터페이스)
     │   │   │   ├─ <Action><Domain>Command    변경 요청 입력값 (record)
     │   │   │   ├─ <Action><Domain>Query      조회 요청 입력값 (record)
     │   │   │   └─ <Action><Domain>Result     유스케이스 결과 (record)
     │   │   └─ out/
     │   │       └─ <Verb><Domain>Port          출력 포트 (인터페이스)
     │   ├─ service/
     │   │   └─ <Action><Domain>Service        입력 포트 구현
     │   └─ exception/                         애플리케이션 예외 (찾을 수 없음, 충돌 등)
     └─ adapter/
         ├─ in/
         │   └─ web/
         │       ├─ <Domain>Controller
         │       ├─ <Action><Domain>Request    요청 DTO (record)
         │       └─ <Domain>Response           응답 DTO (record)
         └─ out/
             └─ persistence/
                 ├─ <Domain>PersistenceAdapter  출력 포트 구현
                 ├─ <Domain>JpaEntity           @Entity
                 └─ <Domain>JpaRepository       Spring Data JPA
```

- 여러 도메인이 함께 쓰는 코드는 `com.pawmento.global` 아래에 역할별로 둔다(`security`, `error`, `exception`). 특정 도메인에만 쓰는 코드는 `global`에 두지 않는다.
- 객체 스토리지, 외부 API처럼 DB가 아닌 출구는 `adapter/out/<역할>`로 폴더를 더한다. 예: `adapter/out/storage`. 폴더 이름은 기술(`jpa`, `s3`)이 아니라 역할로 짓는다.

## 의존 규칙

각 패키지가 import할 수 있는 것과 없는 것이다. 리뷰할 때 import 목록부터 확인한다.

| 패키지 | import 가능 | import 금지 |
| --- | --- | --- |
| `domain` | 자바 표준 라이브러리, `lombok.Getter`, `global.exception` | `application`, `adapter`, `global`(`global.exception` 제외), Spring, `jakarta.persistence` |
| `application.exception` | 자바 표준 라이브러리, `global.exception` | `domain`, `application.port`, `application.service`, `adapter`, `global`(`global.exception` 제외), Spring |
| `application.port` | `domain`, 같은 도메인의 `port` | `adapter`, `global`, Spring, `jakarta.persistence` |
| `application.service` | `domain`, `application.port`, `application.exception`, `global.exception`, `org.springframework.stereotype`, `org.springframework.transaction`, `lombok` | `adapter`, `global.security`, Spring Security, `jakarta.persistence`, 웹 DTO |
| `adapter.in.web` | `application.port.in`, 같은 웹 어댑터(`exception` 포함, #33 제안), `global`, Spring Web, `jakarta.validation`, `lombok` | `domain`, `application.service`, `adapter.out` |
| `adapter.out.persistence` | `domain`, `application.port.out`, `application.exception`, Spring Data JPA, `jakarta.persistence`, `lombok` | `application.service`, `application.port.in`, `adapter.in` |

- 컨트롤러는 서비스 클래스가 아니라 유스케이스 인터페이스를 받는다.
- 컨트롤러는 도메인 객체를 다루지 않는다. 유스케이스는 도메인 객체 대신 id나 `Result`를 돌려준다.
- 서비스는 영속성 어댑터나 JpaRepository가 아니라 출력 포트를 받는다.
- 서비스는 Spring Security를 모른다. 로그인한 회원 id는 컨트롤러가 꺼내서 Command·Query에 `memberId`로 담아 넘긴다.
- 예외는 [백엔드 예외 처리](backend-exceptions.md)를 따른다. 도메인 예외와 애플리케이션 예외는 `global.exception`의 기본 예외를 상속하고, `global.error.GlobalExceptionHandler`가 응답으로 바꾼다. 컨트롤러와 서비스는 업무 예외를 잡아 바꾸지 않는다. HTTP 입력 스트림의 I/O 변환과 DispatcherServlet 전 필터의 전역 resolver 위임 허용은 아래 #33 변경 제안이며 기존 규칙과 구분한다. (#60, 고침 #33: 웹 경계 처리 구분)
- 다른 도메인의 기능이 필요하면 그 도메인의 `port/in`을 부른다. 자세한 방식은 [아직 정하지 않은 것](#아직-정하지-않은-것)에서 정한다.

## API 경로

- 모든 API 경로는 `/api/v1/<도메인 복수형>`으로 시작한다. 예: `/api/v1/maps/pins`, `/api/v1/media/uploads`. 반려동물 범위는 해당 도메인의 입력(`petId`)으로 받는다. 지도는 `/maps/pins?petId=`, 미디어는 `/media/uploads?petId=`와 `/media/{assetId}?petId=`를 사용한다. 상위 도메인 접두어를 공유하는 예외를 추가하지 않는다. 개발 서버 nginx가 `/api/`로 시작하는 경로만 백엔드로 넘긴다. (고침 #33: #63의 도메인별 루트와 동일 기준 적용)
- 접두어 `/api/v1`은 `global.web.WebConfig`가 우리 패키지(`com.pawmento`)의 `@RestController`에 붙인다. 컨트롤러 클래스에는 `@RequestMapping("/<복수형>")`만 두고, 메서드에는 `@PostMapping`, `@GetMapping("/{id}")`처럼 나머지 경로만 쓴다. (고침 #22: 컨트롤러마다 쓰던 접두어를 설정 한곳으로 옮김)
- Spring Security 경로(`PublicEndpoints`)는 접두어가 붙기 전의 실제 요청 경로를 보므로 `ApiPaths.V1`을 붙인 전체 경로로 쓴다. (#22)

## 이름 규칙

| 종류 | 형식 | 예 |
| --- | --- | --- |
| 입력 포트 | `<Action><Domain>UseCase` | `CreatePetUseCase` |
| 서비스 | `<Action><Domain>Service` | `CreatePetService` |
| 변경 입력 (생성·수정·삭제) | `<Action><Domain>Command` | `CreatePetCommand` |
| 조회 입력 | `<Action><Domain>Query` | `GetPetQuery` |
| 유스케이스 결과 | `<Action><Domain>Result` | `GetMediaUploadResult` |
| 출력 포트 | `<Verb><Domain>Port` | `SavePetPort`, `LoadPetPort` |
| 요청 DTO | `<Action><Domain>Request` | `CreatePetRequest` |
| 응답 DTO | `<Domain>Response` | `PetResponse` |
| 컨트롤러 | `<Domain>Controller` | `PetController` |
| 영속성 어댑터 | `<Domain>PersistenceAdapter` | `PetPersistenceAdapter` |
| JPA 엔티티 | `<Domain>JpaEntity` | `PetJpaEntity` |
| JPA 리포지토리 | `<Domain>JpaRepository` | `PetJpaRepository` |

- `<Action>`은 사용자 기준 동작이다: `Create`, `Edit`, `Delete`, `Get`, `List`.
- `<Verb>`는 저장소 기준 동작이다: `Save`, `Load`, `Update`, `Delete`.
- 기능 하나에 유스케이스 하나, 서비스 하나를 만든다. 입력 포트 인터페이스는 생략하지 않는다. 컨트롤러와 영속성 어댑터는 도메인에 하나씩 두고 메서드를 늘린다.
- 출력 포트는 동작별로 나눈다. 영속성 어댑터 하나가 여러 출력 포트를 구현한다.

## 값 객체는 `record`로

값만 담아 옮기는 클래스는 자바 `record`로 만든다. 불변이고 생성자·getter를 직접 쓰지 않아도 된다.

| 클래스 | 형태 | 이유 |
| --- | --- | --- |
| Command, Query, Result, Request, Response | `record` | 값만 담아 옮긴다 |
| 식별자·변경 가능한 생명주기가 있는 도메인 상태 객체 | `class` | `private` 생성자와 생성·복원 메서드로 상태/불변 조건을 강제한다 |
| 도메인 불변 값·정책 값·작업 전달값 (**이번 PR의 허용 제안**) | `record` 가능 | 식별자·상태 변경 없이 값을 표현하며 필요한 값 검증은 정규 생성자에서 한다. 아래 제안 범위를 따른다 |
| JpaEntity | `class` | JPA가 기본 생성자와 값을 채울 수 있는 구조를 요구한다 |

- `record`의 값은 `get` 없이 이름으로 꺼낸다: `command.petId()`.

## 롬복 사용 범위

롬복은 컴파일할 때 코드를 만들어 주는 도구라 실행 시점에는 의존이 남지 않는다. 그래서 `domain`에서도 쓸 수 있다. 다만 캡슐화를 깨는 어노테이션은 쓰지 않는다.

| 대상 | 쓰는 것 | 쓰지 않는 것 |
| --- | --- | --- |
| 서비스, 컨트롤러, 어댑터 | `@RequiredArgsConstructor` (`private final` 필드로 생성자 주입) | 직접 쓴 주입용 생성자 |
| 도메인 상태 객체 (`class`) | `@Getter` | `@Setter`, `@Data`, `@AllArgsConstructor`, `@NoArgsConstructor`, 공개 `@Builder` |
| JpaEntity | `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)` | `@Setter`, `@Data` |
| Command·Query·Result·Request·Response | 쓰지 않는다 (`record`로 충분하다) | 롬복 전부 |

- 도메인 객체의 `private` 생성자와 `static` 생성 메서드는 규칙 검사가 들어가므로 직접 쓴다. 롬복으로 대신하지 않는다.
- `@Data`는 setter를 함께 만들어 값을 아무 데서나 바꿀 수 있게 되므로 어디에도 쓰지 않는다.
- JpaEntity의 기본 생성자는 JPA가 쓰도록 남기되, 접근 범위를 좁혀 다른 코드가 함부로 만들지 못하게 한다.
- `build.gradle`에 `compileOnly 'org.projectlombok:lombok'`, `annotationProcessor 'org.projectlombok:lombok'`이 필요하다. 테스트에서 쓰면 `testCompileOnly`, `testAnnotationProcessor`도 더한다.

## 검증 기준

같은 검사를 여러 곳에 흩어 쓰지 않도록 역할을 나눈다.

| 검증 | 위치 | 예 |
| --- | --- | --- |
| 형식 (필수, 길이, 이메일 형식) | Request의 Bean Validation (`@Valid`) | `@NotBlank`, `@Size`, `@Email` |
| 업무 규칙 | 도메인 객체 | 하루 기록 개수 제한, 작성자만 수정 |

- 형식 검증은 Request에서 한다. 서비스에 다시 쓰지 않는다.
- 도메인 객체가 절대 깨지면 안 되는 조건(예: 내용이 비면 안 됨)은 Request에서 막더라도 도메인 생성 메서드에도 남긴다. 다른 경로로 들어온 값도 막는 마지막 안전장치다.
- Bean Validation은 `spring-boot-starter-validation` 의존성이 필요하다.

## 클래스별 작성 규칙

### 도메인 객체 (`domain`)

- 데이터와 그 데이터가 지켜야 할 규칙을 함께 가진다.
- Spring·JPA 어노테이션을 붙이지 않는다. 롬복은 `@Getter`만 쓴다. 식별자·변경 가능한 생명주기를 가진 도메인 객체는 순수 자바 `class`로 쓴다. 불변 값·정책·작업 전달값의 record 허용은 이번 PR의 변경 제안이며 아래 적용 범위를 따른다.
- 상태를 가진 도메인 class의 생성자는 `private`으로 막고, 규칙을 검사하는 `static` 생성·복원 메서드로만 만든다. 이번 PR이 제안하는 불변 record의 값 검증은 정규 생성자에서 할 수 있다. 규칙을 건너뛰고 객체를 만드는 길을 남기지 않는다.
- 상태를 바꾸는 동작은 메서드로 둔다(`edit()`, `changeMode()`). 바꿀 때도 규칙을 검사한다.
- 영속성 어댑터가 값을 꺼낼 수 있게 `@Getter`를 붙인다. setter는 두지 않는다.
- 새로 만든 객체의 `id`는 `null`이다. DB에 저장한 뒤 생긴다.
- 규칙 위반은 `IllegalArgumentException`이 아니라 `domain/exception`의 도메인 예외로 던진다. ([백엔드 예외 처리](backend-exceptions.md))

### 입력 포트, Command·Query·Result (`application/port/in`)

- 유스케이스는 메서드 하나짜리 인터페이스다.
- 입력은 Command(변경) 또는 Query(조회) 하나로 받는다. 값이 하나뿐이어도 감싼다. 웹 DTO(Request)를 받지 않는다.
- 로그인한 회원이 필요한 기능은 Command·Query에 `memberId`를 둔다.
- 반환값은 새로 만든 id(`Long`)나 `Result`다. 도메인 객체를 그대로 돌려주지 않는다.

### 서비스 (`application/service`)

- 입력 포트를 구현한다. `@Service`를 붙이고, 데이터를 바꾸는 메서드에는 `@Transactional`을 붙인다.
- 기능의 순서만 짠다: 도메인 객체를 만들거나 꺼내고, 도메인 메서드를 부르고, 출력 포트로 저장한다.
- 업무 규칙 검사는 도메인 객체에 맡긴다. 형식 검사는 Request가 맡는다. 서비스에 다시 쓰지 않는다.
- 조회 결과는 도메인 객체에서 필요한 값을 꺼내 `Result`로 만들어 돌려준다.
- 출력 포트는 `private final` 필드로 두고 `@RequiredArgsConstructor`로 생성자 주입을 받는다.

### 출력 포트 (`application/port/out`)

- 핵심이 바깥에 부탁할 일을 인터페이스로 정한다.
- 매개변수와 반환값에는 도메인 객체나 자바 기본 타입만 쓴다. `JpaEntity`를 쓰지 않는다.

### 컨트롤러와 웹 DTO (`adapter/in/web`)

- 컨트롤러는 유스케이스 인터페이스를 `private final` 필드로 두고 `@RequiredArgsConstructor`로 받는다.
- 하는 일은 세 가지다: Request를 Command·Query로 옮겨 담기, 유스케이스 호출, 결과(id나 `Result`)를 Response로 옮겨 담기.
- 비즈니스 규칙을 두지 않는다. 도메인 객체를 import하지 않는다.
- Request는 `record`로 만들고 Bean Validation 어노테이션을 붙인다. 컨트롤러 매개변수에 `@Valid`를 붙인다.
- 로그인 정보는 `global.security`의 방식(예: `@LoginMember`)으로 받아 Command·Query에 담는다.

### 영속성 어댑터와 JPA (`adapter/out/persistence`)

- 영속성 어댑터는 출력 포트를 구현한다. `@Component`, `@RequiredArgsConstructor`를 붙이고 JpaRepository를 `private final` 필드로 받는다.
- 하는 일은 변환과 위임뿐이다: 도메인 객체 → JpaEntity → JpaRepository, 결과 → 도메인 객체나 id.
- 비즈니스 규칙을 두지 않는다. DB를 바꾸면 통째로 바꿀 수 있어야 한다.
- JpaEntity는 DB 테이블 모양만 가진다. `@Entity`, `@Table(name = "<복수형>")`, `@Id`, `@GeneratedValue(strategy = GenerationType.IDENTITY)`를 쓴다.
- JpaEntity에는 `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 붙이고, 도메인 객체를 받아 만드는 `static from()`을 둔다. DB에서 꺼낸 값을 도메인 객체로 바꾸는 메서드는 조회 기능을 만들 때 정한다.

## 요청 흐름

하나의 API 요청이 지나가는 순서다. 실제 경로 예시는 `/api/v1/media/uploads`이며 새 기능도 같은 의존 방향으로 흐르게 만든다.

1. 서버를 켤 때 Spring이 `JpaRepository → PersistenceAdapter → Service → Controller` 순서로 객체를 만들어 생성자에 넣는다.
2. 컨트롤러: JSON → Request(`@Valid`로 형식 검증) → Command, 유스케이스 호출
3. 서비스: Command에서 값을 꺼내 도메인 생성 메서드 호출
4. 도메인: 업무 규칙 검사, 통과하면 객체 생성
5. 서비스: 출력 포트 호출
6. 영속성 어댑터: 도메인 → JpaEntity, JpaRepository 저장
7. 결과(id나 Result)가 `return`으로 같은 길을 거꾸로 올라오고, 컨트롤러가 Response로 담아 응답

## 새 기능 추가 순서

계획 문서가 승인된 `Feat` 이슈 기준이다. 안쪽부터 만든다. 바깥 클래스가 안쪽 클래스를 쓰기 때문이다.

1. `domain`: 도메인 객체가 없으면 만들고, 이번 기능에 필요한 규칙과 메서드를 더한다.
2. `port/in`: 유스케이스 인터페이스와 Command·Query, 필요하면 Result를 만든다.
3. `port/out`: 필요한 출력 포트를 만든다. 이미 있으면 재사용한다.
4. `service`: 유스케이스를 구현한다.
5. `adapter/out`: 출력 포트를 구현한다. 처음이면 JpaEntity, JpaRepository도 만든다.
6. `adapter/in/web`: Request, Response, 컨트롤러 메서드를 만든다.
7. 시나리오의 테스트를 쓰고 [검증 기준](testing.md)대로 확인한다.

## 리뷰 체크리스트

- [ ] `domain` 파일에 Spring·JPA 어노테이션이나 `application`·`adapter`·`global`(`global.exception` 제외) import가 없다
- [ ] `application` 파일에 `adapter`·JPA·Spring Security·웹 DTO import가 없다
- [ ] 컨트롤러가 서비스 클래스가 아니라 유스케이스를 받고, 도메인 객체를 import하지 않는다
- [ ] 서비스가 어댑터나 JpaRepository가 아니라 출력 포트를 받는다
- [ ] 형식 검증은 Request, 업무 규칙은 도메인에 있고 컨트롤러·어댑터·서비스에 중복이 없다
- [ ] Request가 서비스까지 넘어가지 않고, 도메인 객체가 응답으로 나가지 않는다
- [ ] Command·Query·Result·Request·Response가 `record`다
- [ ] 컨트롤러 경로에 `/api`를 쓰지 않는다 (`WebConfig`가 `/api/v1`을 붙인다)
- [ ] 의존 객체가 `private final` 필드이고 `@RequiredArgsConstructor`로 주입된다
- [ ] `@Data`, `@Setter`가 없고, 도메인에는 롬복 `@Getter` 말고 다른 롬복이 없다
- [ ] 이름이 [이름 규칙](#이름-규칙)과 맞는다
- [ ] 새 예외가 `global.exception`의 기본 예외를 상속하고, 도메인별 `@RestControllerAdvice`나 서비스의 예외 변환 `try/catch`가 없다 ([백엔드 예외 처리](backend-exceptions.md))

## 함정

- 에디터 자동 import로 `domain`에 `application` 클래스가 들어가는 일이 있다. 쓰지 않는 import라도 지운다.

## 정한 것

- 입력 포트 인터페이스(`UseCase`)를 모든 기능에 둔다. 컨트롤러는 서비스 클래스를 직접 받지 않는다.
- 유스케이스 입력은 Command(변경)·Query(조회)로 받고, 결과는 id나 `Result`로 돌려준다. 도메인 객체를 돌려주지 않는다.
- 여러 도메인이 함께 쓰는 코드는 `com.pawmento.global`에 두고, 하위에 `security`, `error`를 둔다.
- API 경로는 `/api/v1/<복수형>`이다. 컨트롤러에는 `/<복수형>`만 쓴다.
- 로그인한 회원 id는 Command·Query의 `memberId`로 넘긴다. 서비스는 Spring Security를 모른다.
- 형식 검증은 Request의 Bean Validation, 업무 규칙은 도메인이 맡는다.
- Command·Query·Result·Request·Response는 자바 `record`로 만든다.
- 롬복을 쓴다. 범위는 [롬복 사용 범위](#롬복-사용-범위)를 따른다.
- 예외는 공통 기본 예외를 상속하고 `GlobalExceptionHandler` 한 곳에서 응답으로 바꾼다. 자세한 것은 [백엔드 예외 처리](backend-exceptions.md)를 따른다. (#60)

## 아직 정하지 않은 것

- 도메인 객체와 JpaEntity의 분리 기준을 확장 기능에서도 유지할지
- 다른 도메인 기능을 부르는 방식
- 테스트를 계층별로 어디에 어떻게 둘지

## 이번 PR에서 제안하는 공통 규칙 변경 (#33, PR #64)

이 절의 **불변 record 허용·동일 조회 결과 재사용·웹 경계의 제한된 처리 허용**은 기존 확정 규칙을 그대로 적용했다는 설명이 아니다. 이번 PR에서 채택 여부와 공통 적용 범위를 검토하는 변경 제안이다. 아래 표·본문의 관련 표기는 제안이 채택되는 경우의 기준을 일관되게 표현한다. 공통 기본 예외 3종 확장도 [예외 처리 문서](backend-exceptions.md)의 별도 제안이며 팀의 기존 합의와 구분한다.

- 웹 전용 형식·파트·Origin·헤더·요청 크기 오류는 `adapter/in/web/exception`에서 공통 기본 예외를 상속한다. 업무 오류를 웹 예외로 다시 포장하지 않는다. 필터는 MVC 이전 실패를 전역 resolver로 넘길 수 있고, HTTP 입력 스트림 보조 어댑터는 자신의 I/O 실패만 공통 저장소 예외 하위 타입으로 바꿀 수 있다. 컨트롤러에 도메인/애플리케이션 예외 처리 catch를 추가하는 예외는 아니다.
- 식별자와 변경 가능한 생명주기가 있는 도메인(자산·작업)은 `class`로 두고 정책을 가진다. 불변 값·작업 전달값·정책 값은 `record`를 사용할 수 있다. 미디어의 `VerifiedMedia`, `StoredUpload`, `MediaWork`, `MediaPolicy`가 해당한다. 복원 경로도 필수 값·상태 간 관계 등 불변 조건을 검사한다.
- 포트 입력은 동작별 Command/Query 하나로 받는다. 결과는 Action+Domain+Result로 이름 짓고, 생성·수신·조회가 같은 자원의 동일 조회 스냅샷을 반환할 때는 `GetMediaUploadResult`를 재사용한다. 다른 동작의 데이터·규칙을 합치는 허용은 아니다. 미리보기는 `GetMediaPreviewResult`다.
- 미디어의 여러 JPA 엔티티/리포지토리는 한 `MediaPersistenceAdapter`가 여러 출력 포트를 구현해 조율한다. 작업의 임대·재시도·상태 정책은 `MediaJob`과 `MediaPolicy`에, 엔티티는 DB 형태·변환에 둔다.
