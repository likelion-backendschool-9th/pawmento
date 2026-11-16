# 백엔드 구조 (헥사고날 아키텍처)

백엔드 코드를 어떤 패키지에 어떤 클래스로 나누는지 정한 규칙이다. 사람과 AI 모두 새 기능을 만들 때 이 문서대로 만든다. 예시는 이슈 #16 `record` 도메인의 "기록 작성하기"다.

> 이 문서는 #16 샘플을 기준으로 쓴 초안이다. 팀이 정한 것은 [정한 것](#정한-것)에, 남은 것은 [아직 정하지 않은 것](#아직-정하지-않은-것)에 둔다.

## 핵심 원칙

- 핵심(`domain`, `application`)은 바깥 기술(웹, DB, 외부 API)을 모른다.
- 핵심은 필요한 것을 인터페이스(포트)로 정하고, 바깥은 그 인터페이스를 구현(어댑터)한다.
- 의존(import) 방향은 바깥에서 안쪽으로만 향한다: `adapter → application → domain`.
- 서비스는 포트를 부르고, 어댑터는 포트를 구현한다.

## 패키지 구조

도메인으로 먼저 나누고, 도메인 안에서 `domain` / `application` / `adapter`로 나눈다. 도메인 이름은 [문서 규칙](README.md#도메인-폴더)대로 문서 폴더, 백엔드 패키지, 프론트엔드 경로에서 같게 쓴다.

```text
com.pawmento.<도메인>
 ├─ domain/
 │   └─ <Domain>                          도메인 객체 (데이터 + 규칙)
 ├─ application/
 │   ├─ port/
 │   │   ├─ in/
 │   │   │   ├─ <Action><Domain>UseCase    입력 포트 (인터페이스)
 │   │   │   ├─ <Action><Domain>Command    변경 요청 입력값
 │   │   │   └─ <Action><Domain>Query      조회 요청 입력값
 │   │   └─ out/
 │   │       └─ <Verb><Domain>Port          출력 포트 (인터페이스)
 │   └─ service/
 │       └─ <Action><Domain>Service        입력 포트 구현
 └─ adapter/
     ├─ in/
     │   └─ web/
     │       ├─ <Domain>Controller
     │       ├─ <Action><Domain>Request    요청 DTO
     │       └─ <Domain>Response           응답 DTO
     └─ out/
         └─ persistence/
             ├─ <Domain>PersistenceAdapter  출력 포트 구현
             ├─ <Domain>JpaEntity           @Entity
             └─ <Domain>JpaRepository       Spring Data JPA
```

- 객체 스토리지, 외부 API처럼 DB가 아닌 출구는 `adapter/out/<역할>`로 폴더를 더한다. 예: `adapter/out/storage`. 폴더 이름은 기술(`jpa`, `s3`)이 아니라 역할로 짓는다.
- 여러 도메인이 함께 쓰는 코드(공통 예외, 설정, 유틸)는 `com.pawmento.common`에 둔다. 특정 도메인에만 쓰는 코드는 `common`에 두지 않는다.

## 의존 규칙

각 패키지가 import할 수 있는 것과 없는 것이다. 리뷰할 때 import 목록부터 확인한다.

| 패키지 | import 가능 | import 금지 |
| --- | --- | --- |
| `domain` | 자바 표준 라이브러리 | `application`, `adapter`, Spring, `jakarta.persistence` |
| `application.port` | `domain`, 같은 도메인의 `port` | `adapter`, Spring, `jakarta.persistence` |
| `application.service` | `domain`, `application.port`, `org.springframework.stereotype`, `org.springframework.transaction` | `adapter`, `jakarta.persistence`, 웹 DTO |
| `adapter.in.web` | `application.port.in`, Spring Web | `application.service`, `domain`, `adapter.out` |
| `adapter.out.persistence` | `domain`, `application.port.out`, Spring Data JPA, `jakarta.persistence` | `application.service`, `application.port.in`, `adapter.in` |

- 컨트롤러는 서비스 클래스가 아니라 유스케이스 인터페이스를 받는다.
- 서비스는 영속성 어댑터나 JpaRepository가 아니라 출력 포트를 받는다.
- 다른 도메인의 기능이 필요하면 그 도메인의 `port/in`을 부른다. 다른 도메인의 `adapter`, `domain` 내부를 직접 쓰는 방식은 [아직 정하지 않은 것](#아직-정하지-않은-것)에서 정한다.

## 이름 규칙

| 종류 | 형식 | 예 |
| --- | --- | --- |
| 입력 포트 | `<Action><Domain>UseCase` | `CreateRecordUseCase` |
| 서비스 | `<Action><Domain>Service` | `CreateRecordService` |
| 변경 입력 (생성·수정·삭제) | `<Action><Domain>Command` | `CreateRecordCommand` |
| 조회 입력 | `<Action><Domain>Query` | `GetRecordQuery` |
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

## 클래스별 작성 규칙

### 도메인 객체 (`domain`)

- 데이터와 그 데이터가 지켜야 할 규칙을 함께 가진다.
- 어노테이션을 붙이지 않는다. 순수 자바로 쓴다.
- 생성자는 `private`으로 막고, 규칙을 검사하는 `static` 생성 메서드로만 만든다. 규칙을 건너뛰고 객체를 만드는 길을 남기지 않는다.
- 상태를 바꾸는 동작은 메서드로 둔다(`edit()`, `changeMode()`). 바꿀 때도 규칙을 검사한다.
- 영속성 어댑터가 값을 꺼낼 수 있게 getter를 둔다. setter는 두지 않는다.
- 새로 만든 객체의 `id`는 `null`이다. DB에 저장한 뒤 생긴다.

```java
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

    // getter
}
```

### 입력 포트와 Command (`application/port/in`)

- 유스케이스는 메서드 하나짜리 인터페이스다.
- 입력은 Command(변경) 또는 Query(조회) 객체 하나로 받는다. 값이 하나뿐이어도 Command·Query로 감싼다. 웹 DTO(Request)를 받지 않는다.
- Command·Query는 클래스로 만든다. 필드는 `private final`, 모든 값을 받는 생성자와 getter만 둔다.
- 반환값은 새로 만든 id(`Long`)나 결과 객체다. 도메인 객체를 그대로 돌려줄지는 [아직 정하지 않은 것](#아직-정하지-않은-것)에서 정한다.

```java
public interface CreateRecordUseCase {
    Long create(CreateRecordCommand command);
}
```

### 서비스 (`application/service`)

- 입력 포트를 구현한다. `@Service`를 붙이고, 데이터를 바꾸는 메서드에는 `@Transactional`을 붙인다.
- 기능의 순서만 짠다: 도메인 객체를 만들거나 꺼내고, 도메인 메서드를 부르고, 출력 포트로 저장한다.
- 규칙 검사는 도메인 객체에 맡긴다. 같은 검사를 서비스에 다시 쓰지 않는다.
- 출력 포트는 생성자로 받고 필드는 `private final`로 둔다.

```java
@Service
public class CreateRecordService implements CreateRecordUseCase {
    private final SaveRecordPort saveRecordPort;

    public CreateRecordService(SaveRecordPort saveRecordPort) {
        this.saveRecordPort = saveRecordPort;
    }

    @Override
    @Transactional
    public Long create(CreateRecordCommand command) {
        Record record = Record.create(command.getPetId(), command.getContent());
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

- 컨트롤러는 유스케이스 인터페이스를 생성자로 받는다.
- 하는 일은 세 가지다: Request를 Command로 옮겨 담기, 유스케이스 호출, 결과를 Response로 옮겨 담기.
- 비즈니스 규칙을 두지 않는다.
- Request는 기본 생성자와 getter를 둔다(JSON → 객체). Response는 값을 받는 생성자와 getter를 둔다(객체 → JSON).
- 도메인 객체를 응답으로 그대로 내보내지 않는다.

```java
@RestController
public class RecordController {
    private final CreateRecordUseCase createRecordUseCase;

    public RecordController(CreateRecordUseCase createRecordUseCase) {
        this.createRecordUseCase = createRecordUseCase;
    }

    @PostMapping("/records")
    public RecordResponse create(@RequestBody CreateRecordRequest request) {
        CreateRecordCommand command = new CreateRecordCommand(request.getPetId(), request.getContent());
        Long id = createRecordUseCase.create(command);
        return new RecordResponse(id);
    }
}
```

### 영속성 어댑터와 JPA (`adapter/out/persistence`)

- 영속성 어댑터는 출력 포트를 구현한다. `@Component`를 붙이고 JpaRepository를 생성자로 받는다.
- 하는 일은 변환과 위임뿐이다: 도메인 객체 → JpaEntity → JpaRepository, 결과 → 도메인 객체나 id.
- 비즈니스 규칙을 두지 않는다. DB를 바꾸면 통째로 바꿀 수 있어야 한다.
- JpaEntity는 DB 테이블 모양만 가진다. `@Entity`, `@Table(name = "<복수형>")`, `@Id`, `@GeneratedValue(strategy = GenerationType.IDENTITY)`를 쓴다.
- JpaEntity에는 JPA가 쓰는 기본 생성자와, 도메인 객체를 받아 만드는 `static from()`을 둔다. DB에서 꺼낸 값을 도메인 객체로 바꾸는 메서드는 조회 기능을 만들 때 정한다.

```java
@Component
public class RecordPersistenceAdapter implements SaveRecordPort {
    private final RecordJpaRepository recordJpaRepository;

    public RecordPersistenceAdapter(RecordJpaRepository recordJpaRepository) {
        this.recordJpaRepository = recordJpaRepository;
    }

    @Override
    public Long save(Record record) {
        RecordJpaEntity saved = recordJpaRepository.save(RecordJpaEntity.from(record));
        return saved.getId();
    }
}
```

## 요청 흐름

`POST /records` 하나가 지나가는 순서다. 새 기능도 같은 순서로 흐르게 만든다.

1. 서버를 켤 때 Spring이 `JpaRepository → PersistenceAdapter → Service → Controller` 순서로 객체를 만들어 생성자에 넣는다.
2. 컨트롤러: JSON → Request → Command, 유스케이스 호출
3. 서비스: Command에서 값을 꺼내 도메인 생성 메서드 호출
4. 도메인: 규칙 검사, 통과하면 객체 생성
5. 서비스: 출력 포트 호출
6. 영속성 어댑터: 도메인 → JpaEntity, JpaRepository 저장
7. 결과(id)가 `return`으로 같은 길을 거꾸로 올라오고, 컨트롤러가 Response로 담아 응답

## 새 기능 추가 순서

계획 문서가 승인된 `Feat` 이슈 기준이다. 안쪽부터 만든다. 바깥 클래스가 안쪽 클래스를 쓰기 때문이다.

1. `domain`: 도메인 객체가 없으면 만들고, 이번 기능에 필요한 규칙과 메서드를 더한다.
2. `port/in`: 유스케이스 인터페이스와 Command 또는 Query를 만든다.
3. `port/out`: 필요한 출력 포트를 만든다. 이미 있으면 재사용한다.
4. `service`: 유스케이스를 구현한다.
5. `adapter/out`: 출력 포트를 구현한다. 처음이면 JpaEntity, JpaRepository도 만든다.
6. `adapter/in/web`: Request, Response, 컨트롤러 메서드를 만든다.
7. 시나리오의 테스트를 쓰고 [검증 기준](testing.md)대로 확인한다.

## 리뷰 체크리스트

- [ ] `domain` 파일에 어노테이션이나 `application`·`adapter`·Spring·JPA import가 없다
- [ ] `application` 파일에 `adapter`·JPA·웹 DTO import가 없다
- [ ] 컨트롤러가 서비스 클래스가 아니라 유스케이스를 받는다
- [ ] 서비스가 어댑터나 JpaRepository가 아니라 출력 포트를 받는다
- [ ] 비즈니스 규칙이 도메인에 있고 컨트롤러·어댑터에 없다
- [ ] Request가 서비스까지 넘어가지 않고, 도메인 객체가 응답으로 나가지 않는다
- [ ] 의존 객체를 생성자로 받고 필드가 `private final`이다
- [ ] 이름이 [이름 규칙](#이름-규칙)과 맞는다

## 함정

- 자바에는 `java.lang.Record`가 있고 `java.lang`은 자동으로 import된다. 도메인 클래스 이름이 `Record`이면, import를 빠뜨려도 컴파일 에러 없이 자바 내장 `Record`로 컴파일된다. `Record`를 쓰는 파일은 `import com.pawmento.record.domain.Record;`를 확인한다.
- 에디터 자동 import로 `domain`에 `application` 클래스가 들어가는 일이 있다. 쓰지 않는 import라도 지운다.

## 정한 것

- 입력 포트 인터페이스(`UseCase`)를 모든 기능에 둔다. 컨트롤러는 서비스 클래스를 직접 받지 않는다.
- 유스케이스 입력은 Command(변경)·Query(조회) 객체로 받는다.
- 여러 도메인이 함께 쓰는 코드는 `com.pawmento.common`에 둔다.

## 아직 정하지 않은 것

- 도메인 객체와 JpaEntity를 계속 분리할지 (#16은 분리)
- 도메인 규칙 위반을 HTTP 400으로 바꾸는 방식 (예외 클래스, `@RestControllerAdvice` 위치). 지금은 500으로 나간다
- 유스케이스가 도메인 객체를 그대로 돌려줄지, 결과 객체를 따로 둘지
- 도메인 클래스 이름 `Record` 유지 여부 (대안: `PetRecord`)
- 다른 도메인 기능을 부르는 방식
- 테스트를 계층별로 어디에 어떻게 둘지
