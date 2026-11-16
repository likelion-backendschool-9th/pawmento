# 백엔드 구조 (헥사고날 아키텍처)

백엔드 코드를 어떤 패키지에 어떤 클래스로 나누는지 정한 규칙이다. 사람과 AI 모두 새 기능을 만들 때 이 문서대로 만든다. 예시는 이슈 #16 `record` 도메인의 "기록 작성하기"다.

> 이 문서는 #16 샘플과 그 리뷰를 기준으로 쓴 초안이다. 팀이 정한 것은 [정한 것](#정한-것)에, 남은 것은 [아직 정하지 않은 것](#아직-정하지-않은-것)에 둔다.

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
 │   └─ error/                            공통 에러 응답, 예외 → HTTP 응답 변환
 └─ <도메인>/
     ├─ domain/
     │   └─ <Domain>                      도메인 객체 (데이터 + 규칙)
     ├─ application/
     │   ├─ port/
     │   │   ├─ in/
     │   │   │   ├─ <Action><Domain>UseCase    입력 포트 (인터페이스)
     │   │   │   ├─ <Action><Domain>Command    변경 요청 입력값 (record)
     │   │   │   ├─ <Action><Domain>Query      조회 요청 입력값 (record)
     │   │   │   └─ <Action><Domain>Result     유스케이스 결과 (record)
     │   │   └─ out/
     │   │       └─ <Verb><Domain>Port          출력 포트 (인터페이스)
     │   └─ service/
     │       └─ <Action><Domain>Service        입력 포트 구현
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

- 여러 도메인이 함께 쓰는 코드는 `com.pawmento.global` 아래에 역할별로 둔다(`security`, `error`). 특정 도메인에만 쓰는 코드는 `global`에 두지 않는다.
- 객체 스토리지, 외부 API처럼 DB가 아닌 출구는 `adapter/out/<역할>`로 폴더를 더한다. 예: `adapter/out/storage`. 폴더 이름은 기술(`jpa`, `s3`)이 아니라 역할로 짓는다.

## 의존 규칙

각 패키지가 import할 수 있는 것과 없는 것이다. 리뷰할 때 import 목록부터 확인한다.

| 패키지 | import 가능 | import 금지 |
| --- | --- | --- |
| `domain` | 자바 표준 라이브러리, `lombok.Getter` | `application`, `adapter`, `global`, Spring, `jakarta.persistence` |
| `application.port` | `domain`, 같은 도메인의 `port` | `adapter`, `global`, Spring, `jakarta.persistence` |
| `application.service` | `domain`, `application.port`, `global.error`, `org.springframework.stereotype`, `org.springframework.transaction`, `lombok` | `adapter`, `global.security`, Spring Security, `jakarta.persistence`, 웹 DTO |
| `adapter.in.web` | `application.port.in`, `global`, Spring Web, `jakarta.validation`, `lombok` | `domain`, `application.service`, `adapter.out` |
| `adapter.out.persistence` | `domain`, `application.port.out`, Spring Data JPA, `jakarta.persistence`, `lombok` | `application.service`, `application.port.in`, `adapter.in` |

- 컨트롤러는 서비스 클래스가 아니라 유스케이스 인터페이스를 받는다.
- 컨트롤러는 도메인 객체를 다루지 않는다. 유스케이스는 도메인 객체 대신 id나 `Result`를 돌려준다.
- 서비스는 영속성 어댑터나 JpaRepository가 아니라 출력 포트를 받는다.
- 서비스는 Spring Security를 모른다. 로그인한 회원 id는 컨트롤러가 꺼내서 Command·Query에 `memberId`로 담아 넘긴다.
- 다른 도메인의 기능이 필요하면 그 도메인의 `port/in`을 부른다. 자세한 방식은 [아직 정하지 않은 것](#아직-정하지-않은-것)에서 정한다.

## API 경로

- 모든 API 경로는 `/api/<도메인 복수형>`으로 시작한다. 예: `/api/records`. 개발 서버 nginx가 `/api/`로 시작하는 경로만 백엔드로 넘긴다.
- 컨트롤러 클래스에 `@RequestMapping("/api/<복수형>")`을 두고, 메서드에는 `@PostMapping`, `@GetMapping("/{id}")`처럼 나머지 경로만 쓴다.

## 이름 규칙

| 종류 | 형식 | 예 |
| --- | --- | --- |
| 입력 포트 | `<Action><Domain>UseCase` | `CreateRecordUseCase` |
| 서비스 | `<Action><Domain>Service` | `CreateRecordService` |
| 변경 입력 (생성·수정·삭제) | `<Action><Domain>Command` | `CreateRecordCommand` |
| 조회 입력 | `<Action><Domain>Query` | `GetRecordQuery` |
| 유스케이스 결과 | `<Action><Domain>Result` | `GetRecordResult` |
| 출력 포트 | `<Verb><Domain>Port` | `SaveRecordPort`, `LoadRecordPort` |
| 요청 DTO | `<Action><Domain>Request` | `CreateRecordRequest` |
| 응답 DTO | `<Domain>Response` | `RecordResponse` |
| 컨트롤러 | `<Domain>Controller` | `RecordController` |
| 영속성 어댑터 | `<Domain>PersistenceAdapter` | `RecordPersistenceAdapter` |
| JPA 엔티티 | `<Domain>JpaEntity` | `RecordJpaEntity` |
| JPA 리포지토리 | `<Domain>JpaRepository` | `RecordJpaRepository` |

- `<Action>`은 사용자 기준 동작이다: `Create`, `Edit`, `Delete`, `Get`, `List`.
- `<Verb>`는 저장소 기준 동작이다: `Save`, `Load`, `Update`, `Delete`.
- 기능 하나에 유스케이스 하나, 서비스 하나를 만든다. 입력 포트 인터페이스는 생략하지 않는다. 컨트롤러와 영속성 어댑터는 도메인에 하나씩 두고 메서드를 늘린다.
- 출력 포트는 동작별로 나눈다. 영속성 어댑터 하나가 여러 출력 포트를 구현한다.

## 값 객체는 `record`로

값만 담아 옮기는 클래스는 자바 `record`로 만든다. 불변이고 생성자·getter를 직접 쓰지 않아도 된다.

| 클래스 | 형태 | 이유 |
| --- | --- | --- |
| Command, Query, Result, Request, Response | `record` | 값만 담아 옮긴다 |
| 도메인 객체 | `class` | `private` 생성자와 생성 메서드로 규칙을 강제해야 한다 |
| JpaEntity | `class` | JPA가 기본 생성자와 값을 채울 수 있는 구조를 요구한다 |

- `record`의 값은 `get` 없이 이름으로 꺼낸다: `command.petId()`.

## 롬복 사용 범위

롬복은 컴파일할 때 코드를 만들어 주는 도구라 실행 시점에는 의존이 남지 않는다. 그래서 `domain`에서도 쓸 수 있다. 다만 캡슐화를 깨는 어노테이션은 쓰지 않는다.

| 대상 | 쓰는 것 | 쓰지 않는 것 |
| --- | --- | --- |
| 서비스, 컨트롤러, 어댑터 | `@RequiredArgsConstructor` (`private final` 필드로 생성자 주입) | 직접 쓴 주입용 생성자 |
| 도메인 객체 | `@Getter` | `@Setter`, `@Data`, `@AllArgsConstructor`, `@NoArgsConstructor`, 공개 `@Builder` |
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
- Spring·JPA 어노테이션을 붙이지 않는다. 롬복은 `@Getter`만 쓴다. 순수 자바 `class`로 쓴다.
- 생성자는 `private`으로 막고, 규칙을 검사하는 `static` 생성 메서드로만 만든다. 규칙을 건너뛰고 객체를 만드는 길을 남기지 않는다.
- 상태를 바꾸는 동작은 메서드로 둔다(`edit()`, `changeMode()`). 바꿀 때도 규칙을 검사한다.
- 영속성 어댑터가 값을 꺼낼 수 있게 `@Getter`를 붙인다. setter는 두지 않는다.
- 새로 만든 객체의 `id`는 `null`이다. DB에 저장한 뒤 생긴다.

```java
@Getter
public class Record {
    private Long id;
    private Long petId;
    private String content;

    private Record(Long petId, String content) {
        this.petId = petId;
        this.content = content;
    }

    public static Record create(Long petId, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("내용이 비었습니다.");
        }
        return new Record(petId, content);
    }
}
```

### 입력 포트, Command·Query·Result (`application/port/in`)

- 유스케이스는 메서드 하나짜리 인터페이스다.
- 입력은 Command(변경) 또는 Query(조회) 하나로 받는다. 값이 하나뿐이어도 감싼다. 웹 DTO(Request)를 받지 않는다.
- 로그인한 회원이 필요한 기능은 Command·Query에 `memberId`를 둔다.
- 반환값은 새로 만든 id(`Long`)나 `Result`다. 도메인 객체를 그대로 돌려주지 않는다.

```java
public interface CreateRecordUseCase {
    Long create(CreateRecordCommand command);
}

public record CreateRecordCommand(Long petId, String content) {
}
```

### 서비스 (`application/service`)

- 입력 포트를 구현한다. `@Service`를 붙이고, 데이터를 바꾸는 메서드에는 `@Transactional`을 붙인다.
- 기능의 순서만 짠다: 도메인 객체를 만들거나 꺼내고, 도메인 메서드를 부르고, 출력 포트로 저장한다.
- 업무 규칙 검사는 도메인 객체에 맡긴다. 형식 검사는 Request가 맡는다. 서비스에 다시 쓰지 않는다.
- 조회 결과는 도메인 객체에서 필요한 값을 꺼내 `Result`로 만들어 돌려준다.
- 출력 포트는 `private final` 필드로 두고 `@RequiredArgsConstructor`로 생성자 주입을 받는다.

```java
@Service
@RequiredArgsConstructor
public class CreateRecordService implements CreateRecordUseCase {
    private final SaveRecordPort saveRecordPort;

    @Override
    @Transactional
    public Long create(CreateRecordCommand command) {
        Record record = Record.create(command.petId(), command.content());
        return saveRecordPort.save(record);
    }
}
```

### 출력 포트 (`application/port/out`)

- 핵심이 바깥에 부탁할 일을 인터페이스로 정한다.
- 매개변수와 반환값에는 도메인 객체나 자바 기본 타입만 쓴다. `JpaEntity`를 쓰지 않는다.

```java
public interface SaveRecordPort {
    Long save(Record record);
}
```

### 컨트롤러와 웹 DTO (`adapter/in/web`)

- 컨트롤러는 유스케이스 인터페이스를 `private final` 필드로 두고 `@RequiredArgsConstructor`로 받는다.
- 하는 일은 세 가지다: Request를 Command·Query로 옮겨 담기, 유스케이스 호출, 결과(id나 `Result`)를 Response로 옮겨 담기.
- 비즈니스 규칙을 두지 않는다. 도메인 객체를 import하지 않는다.
- Request는 `record`로 만들고 Bean Validation 어노테이션을 붙인다. 컨트롤러 매개변수에 `@Valid`를 붙인다.
- 로그인 정보는 `global.security`의 방식(예: `@LoginMember`)으로 받아 Command·Query에 담는다.

```java
@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class RecordController {
    private final CreateRecordUseCase createRecordUseCase;

    @PostMapping
    public RecordResponse create(@Valid @RequestBody CreateRecordRequest request) {
        CreateRecordCommand command = new CreateRecordCommand(request.petId(), request.content());
        Long id = createRecordUseCase.create(command);
        return new RecordResponse(id);
    }
}

public record CreateRecordRequest(
        @NotNull Long petId,
        @NotBlank String content) {
}

public record RecordResponse(Long recordId) {
}
```

### 영속성 어댑터와 JPA (`adapter/out/persistence`)

- 영속성 어댑터는 출력 포트를 구현한다. `@Component`, `@RequiredArgsConstructor`를 붙이고 JpaRepository를 `private final` 필드로 받는다.
- 하는 일은 변환과 위임뿐이다: 도메인 객체 → JpaEntity → JpaRepository, 결과 → 도메인 객체나 id.
- 비즈니스 규칙을 두지 않는다. DB를 바꾸면 통째로 바꿀 수 있어야 한다.
- JpaEntity는 DB 테이블 모양만 가진다. `@Entity`, `@Table(name = "<복수형>")`, `@Id`, `@GeneratedValue(strategy = GenerationType.IDENTITY)`를 쓴다.
- JpaEntity에는 `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 붙이고, 도메인 객체를 받아 만드는 `static from()`을 둔다. DB에서 꺼낸 값을 도메인 객체로 바꾸는 메서드는 조회 기능을 만들 때 정한다.

```java
@Component
@RequiredArgsConstructor
public class RecordPersistenceAdapter implements SaveRecordPort {
    private final RecordJpaRepository recordJpaRepository;

    @Override
    public Long save(Record record) {
        RecordJpaEntity saved = recordJpaRepository.save(RecordJpaEntity.from(record));
        return saved.getId();
    }
}
```

## 요청 흐름

`POST /api/records` 하나가 지나가는 순서다. 새 기능도 같은 순서로 흐르게 만든다.

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

- [ ] `domain` 파일에 Spring·JPA 어노테이션이나 `application`·`adapter`·`global` import가 없다
- [ ] `application` 파일에 `adapter`·JPA·Spring Security·웹 DTO import가 없다
- [ ] 컨트롤러가 서비스 클래스가 아니라 유스케이스를 받고, 도메인 객체를 import하지 않는다
- [ ] 서비스가 어댑터나 JpaRepository가 아니라 출력 포트를 받는다
- [ ] 형식 검증은 Request, 업무 규칙은 도메인에 있고 컨트롤러·어댑터·서비스에 중복이 없다
- [ ] Request가 서비스까지 넘어가지 않고, 도메인 객체가 응답으로 나가지 않는다
- [ ] Command·Query·Result·Request·Response가 `record`다
- [ ] API 경로가 `/api/`로 시작한다
- [ ] 의존 객체가 `private final` 필드이고 `@RequiredArgsConstructor`로 주입된다
- [ ] `@Data`, `@Setter`가 없고, 도메인에는 롬복 `@Getter` 말고 다른 롬복이 없다
- [ ] 이름이 [이름 규칙](#이름-규칙)과 맞는다

## 함정

- 자바에는 `java.lang.Record`가 있고 `java.lang`은 자동으로 import된다. 도메인 클래스 이름이 `Record`이면, import를 빠뜨려도 컴파일 에러 없이 자바 내장 `Record`로 컴파일된다. `Record`를 쓰는 파일은 `import com.pawmento.record.domain.Record;`를 확인한다.
- 에디터 자동 import로 `domain`에 `application` 클래스가 들어가는 일이 있다. 쓰지 않는 import라도 지운다.

## 정한 것

- 입력 포트 인터페이스(`UseCase`)를 모든 기능에 둔다. 컨트롤러는 서비스 클래스를 직접 받지 않는다.
- 유스케이스 입력은 Command(변경)·Query(조회)로 받고, 결과는 id나 `Result`로 돌려준다. 도메인 객체를 돌려주지 않는다.
- 여러 도메인이 함께 쓰는 코드는 `com.pawmento.global`에 두고, 하위에 `security`, `error`를 둔다.
- API 경로는 `/api/<복수형>`으로 시작한다.
- 로그인한 회원 id는 Command·Query의 `memberId`로 넘긴다. 서비스는 Spring Security를 모른다.
- 형식 검증은 Request의 Bean Validation, 업무 규칙은 도메인이 맡는다.
- Command·Query·Result·Request·Response는 자바 `record`로 만든다.
- 롬복을 쓴다. 범위는 [롬복 사용 범위](#롬복-사용-범위)를 따른다.

## 아직 정하지 않은 것

- 도메인 객체와 JpaEntity를 계속 분리할지 (#16은 분리)
- 도메인 클래스 이름 `Record` 유지 여부. 프론트엔드가 이 개념을 `Memory`로 쓰고 있어 `Memory`로 맞추자는 의견이 있다. 바꾸면 패키지(`com.pawmento.memory`), 문서 폴더, 프론트엔드 경로를 함께 바꾼다
- 도메인 예외를 `global.error`의 공통 에러 응답(400)으로 연결하는 방식
- 다른 도메인 기능을 부르는 방식
- 테스트를 계층별로 어디에 어떻게 둘지
