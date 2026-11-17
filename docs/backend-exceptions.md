# 백엔드 예외 처리

백엔드에서 예외를 어디에 두고, 어떻게 HTTP 응답으로 바꾸는지 정한 규칙이다. 패키지와 의존 규칙 전체는 [백엔드 구조](backend-architecture.md)를 따른다. (#60)

## 핵심 원칙

- 도메인과 애플리케이션은 HTTP를 모른다. 예외에는 무엇이 잘못됐는지만 담고, 상태 코드는 담지 않는다.
- 도메인 예외와 애플리케이션 예외는 `global.exception`의 **기본 예외**를 상속한다.
- `global.error.GlobalExceptionHandler` 한 곳에서 기본 예외를 상태 코드와 공통 에러 형식(`code`, `message`)으로 바꾼다.
- `GlobalExceptionHandler`는 개별 도메인 예외를 import하지 않는다. import하면 `global`과 도메인이 서로를 참조한다.

## 흐름

```text
[domain]            Pet.edit()                       → InvalidPetNameException (RuleViolationException 상속)
[application]       EditPetService                   → PetNotFoundException (ResourceNotFoundException 상속)
[adapter.out]       PetPersistenceAdapter            → 기술 예외를 잡아 PetConcurrentModificationException (StateConflictException 상속)
                        ↓ 서비스와 컨트롤러는 잡지 않고 그대로 올린다
[global.error]      GlobalExceptionHandler           → 400·403·404·409 + ErrorResponse
```

## 기본 예외

`global.exception` 패키지에 둔다. 이 패키지에는 예외 클래스만 두고, HTTP 상태나 `ErrorCode`를 넣지 않는다.

| 기본 예외 | 뜻 | 응답 | 상속하는 예 |
| --- | --- | --- | --- |
| `RuleViolationException` | 입력값만 보고 판단할 수 있는 규칙 위반 | 400 `VALIDATION` | `InvalidPetNameException`, `InvalidCoordinatesException` |
| `PermissionDeniedException` | 요청한 사람에게 권한이 없음 | 403 `FORBIDDEN` | `NotPetOwnerException`, `NotPostAuthorException` |
| `ResourceNotFoundException` | 대상을 찾을 수 없음 | 404 `NOT_FOUND` | `PetNotFoundException`, `MemberNotFoundException` |
| `StateConflictException` | 입력은 맞지만 지금 저장된 상태 때문에 할 수 없음 | 409 `CONFLICT` | `DuplicateEmailException`, `PetHasRecordsException`, `PetConcurrentModificationException` |
| `ContentLimitExceededException` | 전송하는 내용이 크기 제한을 넘음 | 413 `VALIDATION` | `MediaSizeLimitExceededException`, `MediaContentLimitExceededException` |
| `UnsupportedFormatException` | 지원하지 않는 내용 형식 | 415 `VALIDATION` | `UnsupportedMediaFormatException` |
| `StorageUnavailableException` | 파일·메타데이터 저장소의 일시 장애 | 503 `STORAGE_UNAVAILABLE` | `MediaStorageUnavailableException`, `MediaPersistenceUnavailableException` |

- 400과 409는 "같은 입력을 언제 보내도 실패하는가"로 나눈다. 언제 보내도 실패하면 `RuleViolationException`(이름이 21자), 저장된 상태가 바뀌면 성공할 수 있으면 `StateConflictException`(이미 쓰는 이메일, 연결된 기록이 있어 삭제 불가, 다른 요청이 먼저 수정함)이다.
- 이름은 HTTP 용어(`BadRequest`, `Forbidden`)가 아니라 뜻으로 짓는다. 도메인이 응답 형태를 알지 않게 하기 위해서다.
- `PermissionDeniedException`은 Spring Security의 `AccessDeniedException`과 이름이 겹치지 않게 지었다.
- 이번 PR에서는 처음 정한 네 종류에 내용 크기·지원 형식·저장소 장애 세 종류를 추가하는 것을 제안한다. 미디어의 기존 413·415·503 계약을 유지하기 위해 작성 요청자가 승인한 확장 제안이다. 팀 전체의 기존 확정 규칙이라는 뜻은 아니며, 이번 PR의 문서·코드 리뷰와 머지로 채택 여부를 결정한다. (고침 #33: #62 공통 예외 규칙 적용 시 기존 미디어 응답 유지)
- `ContentLimitExceededException`, `UnsupportedFormatException`은 `RuleViolationException`을 상속한다. 전역 핸들러는 더 구체적인 예외를 골라 일반 400과 구분한다. `StorageUnavailableException`은 요청 규칙 위반과 구분해 `RuntimeException`을 직접 상속한다.
- 이후 공통 예외를 더할 때도 이 문서를 고치는 PR에서 먼저 합의한다. 상태별·도메인별 예외를 공통 패키지에 계속 더하지 않는다.
- 기본 예외는 `abstract`로 두며 최상위는 `RuntimeException`을 상속한다. 메시지는 하위 예외의 생성자가 정하고, 기술 실패의 원인은 cause로 보존한다. HTTP 상태·응답 코드는 예외에 넣지 않는다.

```java
// global/exception/RuleViolationException.java
public abstract class RuleViolationException extends RuntimeException {
    protected RuleViolationException(String message) {
        super(message);
    }
}
```

## 도메인 예외와 애플리케이션 예외

| 종류 | 위치 | 기준 | 예 |
| --- | --- | --- | --- |
| 도메인 예외 | `<도메인>/domain/exception` | 도메인 객체 하나로 판단할 수 있는 규칙 위반 | 이름 길이, 미래 생일, 작성자가 아님 |
| 애플리케이션 예외 | `<도메인>/application/exception` | 저장소나 유스케이스 흐름이 있어야 알 수 있는 실패 | 찾을 수 없음, 이메일 중복, 동시 수정, 연결된 기록이 있어 삭제 불가 |

- "찾을 수 없음"은 도메인 객체가 스스로 판단할 수 없으므로 애플리케이션 예외다.
- 예외 이름은 `<무엇이 잘못됐는지>Exception`으로 짓는다: `InvalidPetNameException`, `NotPostAuthorException`.
- 사용자에게 보여 줄 문구는 예외 생성자에 둔다. 같은 문구를 서비스나 핸들러에 다시 쓰지 않는다.

```java
// pet/domain/exception/InvalidPetNameException.java
public class InvalidPetNameException extends RuleViolationException {
    public InvalidPetNameException() {
        super("이름은 1~20자로 입력해 주세요.");
    }
}

// pet/domain/Pet.java
public void edit(Long requesterId, String name, ...) {
    if (!ownerId.equals(requesterId)) {
        throw new NotPetOwnerException();
    }
    if (name == null || name.isBlank() || name.length() > 20) {
        throw new InvalidPetNameException();
    }
    ...
}
```

## 계층별 규칙

| 계층 | 하는 일 | 하지 않는 일 |
| --- | --- | --- |
| `domain` | 규칙 위반을 도메인 예외로 던진다 | `IllegalArgumentException` 등 자바 표준 예외로 규칙 위반을 알리기 |
| `application.service` | 유스케이스 실패를 애플리케이션 예외로 던진다. 도메인 예외는 잡지 않고 그대로 올린다 | 도메인 예외를 잡아 다른 예외로 바꾸기 |
| `adapter.out` (`persistence`와 외부 API 등 모든 출구) | JPA·외부 API 같은 기술 예외를 잡아 애플리케이션 예외로 바꾸거나, 포트 계약대로 값(예: `Optional.empty()`)으로 돌려준다 | 기술 예외를 서비스까지 올리기 |
| `adapter.in.web` | 업무 예외를 포장하지 않는다. 웹 입력 오류는 같은 어댑터의 `exception`에 두고 공통 기본 예외를 상속한다. MVC 전 필터는 resolver에 위임하고 입력 스트림 보조 객체는 자신의 I/O만 변환할 수 있다 | 도메인별 `@RestControllerAdvice` 만들기 (로그인 실패 401과 커밋 시점 낙관적 락은 [아직 정하지 않은 것](#아직-정하지-않은-것) 참고) |
| `global.error` | 기본 예외와 공통 요청 오류(`@Valid` 실패, JSON 형식 오류)를 응답으로 바꾼다 | 개별 도메인 예외 import, `IllegalArgumentException`을 400으로 한꺼번에 바꾸기 |

- `IllegalArgumentException`을 400으로 한꺼번에 바꾸지 않는 이유: 라이브러리나 프로그래밍 실수에서 나온 예외까지 사용자 입력 오류로 보이게 된다. 이런 예외는 500으로 남겨 로그에서 찾을 수 있게 한다.
- 우리가 만든 예외 중 기본 예외를 상속하지 않은 것은 500으로 응답한다. Spring MVC 표준 예외(경로 변수 타입 오류, 없는 경로 등)는 Spring이 400·404 같은 상태로 바꾸지만, 본문은 `ErrorResponse`가 아니라 Spring Boot 기본 형식이다.

## 로그

- 규칙 위반·권한·미존재·충돌·내용 크기·형식 오류(4xx)는 정상 요청 실패이므로 `ERROR`로 남기지 않는다. 남기더라도 `DEBUG`나 `INFO`로 남긴다.
- 저장소 장애(503)는 원인을 보존해 `ERROR`로 남기고 응답에는 하위 예외가 정한 안내만 쓴다. cause 메시지·경로·SQL을 응답에 복사하지 않는다.
- 500은 원인을 찾을 수 있게 스택 트레이스와 함께 `ERROR`로 남긴다.
- 500 응답 본문에는 예외 메시지나 클래스 이름 같은 내부 정보를 넣지 않는다.
- 외부 API 키, 토큰, 비밀번호 같은 값은 로그에 남기지 않는다.

## 다른 선택지와 고르지 않은 이유

#60에서 비교한 선택지 중 고르지 않은 것이다.

| 선택지 | 고르지 않은 이유 |
| --- | --- |
| 웹 어댑터가 도메인 예외를 import하고, 도메인별 핸들러가 HTTP로 바꿈 | 도메인마다 핸들러를 두고 예외를 하나씩 등록해야 한다 |
| 지금 규칙 유지: 서비스가 도메인 예외를 잡아 애플리케이션 예외로 바꿈 | 서비스마다 `try/catch`가 늘고, 규칙을 피하려고 업무 규칙을 서비스로 옮기는 일이 생긴다(pet의 소유자 확인) |
| `GlobalExceptionHandler`가 각 도메인 예외를 직접 import | `global`과 도메인이 서로를 참조하고, 도메인을 더할 때마다 `global`을 고쳐야 한다 |

고른 방식(공통 기본 예외)의 위험은 공통 패키지가 커지고 도메인이 HTTP 의미를 알게 되는 것이다. 이름을 뜻으로 짓고, 기본 예외는 합의된 의미만 두고, 더할 때 먼저 합의해서 줄인다.

## 아직 정하지 않은 것

- 로그인 실패(`InvalidCredentialsException`, 401)를 기본 예외로 다룰지. `global.security`의 응답 흐름(`SecurityErrorResponder`)과 함께 정한다.
- Spring MVC 표준 예외(경로 변수 타입 오류, 필수 파라미터 누락, 없는 경로 등)도 `ErrorResponse` 형식으로 맞출지.
- 트랜잭션 커밋 시점에 나는 낙관적 락 예외(`ObjectOptimisticLockingFailureException`)를 어디서 바꿀지. 영속성 어댑터의 `save()` 밖에서 나면 어댑터가 잡을 수 없다.

## 미디어 전환 범위 (#33)

미디어는 `domain/exception`, `application/exception`으로 예외를 나누고 기존 `MediaViolation`, 포트에 있던 `MediaUploadException`·저장/검증 예외, `MediaUploadExceptionHandler`를 제거한다. 서비스는 도메인 예외를 포장하지 않는다. 파일 수신 실패 시 상태 정리를 위한 catch는 유지하고 정리 뒤 같은 예외를 올린다. 작업자는 검증 실패 사유와 재시도를 DB 작업에 기록한다. 검증 실패 사유 코드는 애플리케이션 예외의 `reason`으로 별도 보관한다.

파일·DB 어댑터는 기술 실패를 애플리케이션 예외로 바꾸며, 낙관적 잠금은 충돌로 바꾼다. 임의의 프로그래밍 오류를 입력 오류나 저장소 장애로 일괄 포장하지 않는다. 트랜잭션 커밋 시점 낙관적 잠금 처리 위치는 여전히 위 미결 사항이다.

기존 미디어 핸들러의 multipart 용량·파싱 오류 매핑은 전역 핸들러로 옮겨 413/400과 공통 본문을 유지한다. 필수 쿼리·경로 변수 타입 등 다른 MVC 오류의 통일 여부는 이번 전환에서 결정하지 않는다. 기존 회원·반려동물의 전용 핸들러 전환도 각 담당의 후속 작업이다.

### HTTP 입력 경계 허용 제안 (#33, PR #64)

아래 웹 경계 처리는 기존의 “웹은 예외를 잡지 않는다” 규칙에 대한 이번 PR의 제한된 허용 제안이다. 채택 여부와 다른 도메인 적용 범위를 공통 구조 문서와 함께 검토한다. 웹 전용 예외는 `media/adapter/in/web/exception`에 두며 application에 HTTP 파트·Origin·헤더 규칙을 넣지 않는다. 필터 단계의 catch는 전역 응답 처리 위임이고, 입력 스트림 보조 객체의 catch는 웹 I/O 실패 변환이다. 이 범위는 도메인/애플리케이션 업무 예외를 웹에서 다시 매핑하는 것을 허용하지 않는다. 저장소 내부 키·variant 같은 서버 계약 오류는 일시 저장소 장애로 포장하지 않으며 작업은 원본을 보존한 FAILED로 남긴다. 기술 장애는 원인 throwable과 작업 문맥을 ERROR로 기록하고 재시도한다.
