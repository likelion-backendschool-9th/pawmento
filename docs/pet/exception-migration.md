# 반려동물 예외를 공통 기본 예외로 전환

관련 이슈: [#72](https://github.com/likelion-backendschool-9th/pawmento/issues/72)의 pet 항목. 기능·응답 기준: [반려동물 등록·관리](manage.md) #46.

## 한눈에
- 끝나면: pet의 400·403·404·409 응답과 기존 데이터 보호를 유지하면서 공통 기본 예외와 `GlobalExceptionHandler`를 사용한다. 서비스는 도메인 예외를 다시 포장하지 않는다.
- 결정 필요: member/pet 공용 아키텍처 테스트의 최소 변경 범위(Q2). 삭제 불가 예외(Q1)와 pet에 적용할 공통 기본 예외(Q3)는 아래 결정으로 확인됐다.
- 선행 조건: #64로 병합된 기본 4종과 전역 매핑을 사용한다. [#72 담당 답변](https://github.com/likelion-backendschool-9th/pawmento/issues/72#issuecomment-6971607724)과 본문에 따라 pet 전환을 계획한다. 공통 기반 추가 수정을 기다리는 일괄 보류 조건은 해제하되, **Q2의 공용 검사 변경 범위를 담당자와 맞추고 계획 검토를 받은 뒤 구현한다.** pet 작업에서 global 코드를 새로 만들거나 변경하지 않는다.
- 규모: 계획 검토 후 입력 오류, 권한·조회 실패, 삭제·동시성, 핸들러 제거를 각각 작은 백엔드 작업으로 진행한다. 프론트 작업은 없다.

## 질문
- Q2: #64 반영 후에도 `HexagonalArchitectureTest`는 member와 pet을 함께 검사하며 domain의 `global.exception` import를 허용하지 않는다. 테스트 담당·다윗과 **pet 검사에 공통 기본 예외 import만 허용하는 최소 변경**을 합의한다. member의 검사 의미·마이페이지 테스트는 변경하지 않는다. 이 파일이 #52 검증도 겸하므로 별도 합의 없이 수정하지 않는다. 중복 pet 테스트를 추가하는 것만으로 기존 검사의 실패가 해결됐다고 보지 않는다(S6).

## 확인된 결정

[#72 본문과 담당 답변](https://github.com/likelion-backendschool-9th/pawmento/issues/72#issuecomment-6971607724)을 기준으로 정리한다. pet 범위를 넘어선 공통 규칙 변경을 승인한 뜻은 아니다.

- Q1 해결: `HasPetRecordsPort` 조회 결과 연결된 기록이 있으면 애플리케이션에서 `PetHasRecordsException`을 던진다. `StateConflictException`을 상속해 기존 409 `CONFLICT`를 유지한다. 중복 `PetDeletionBlockedException`·예외 재포장과 그 예외만을 위한 `Pet.requireDeletable`은 정리하고, 삭제 차단 검증은 애플리케이션의 가짜 포트 검사로 옮긴다.
- 소유자 판단은 기존 `Pet.requireOwnedBy()`에 유지한다. `PetOwnership`은 조회·도메인 호출만 조율하며 예외 재포장을 제거한다.
- Q3 해결: pet은 #64의 `RuleViolationException`·`PermissionDeniedException`·`ResourceNotFoundException`·`StateConflictException` 네 종류와 기존 전역 매핑을 사용한다. 413·415·503의 타 도메인 공통 사용 여부는 이슈의 별도 미결이며 pet 전환을 위해 채택하거나 재설계하지 않는다.
- `GetPetUseCase` 계약과 미디어 권한 회귀 확인은 유지한다. Q2에 대한 구체 파일 수정 범위는 별도 합의가 필요하며 담당 답변만으로 공용 검사를 임의 변경하지 않는다.

## 공통 기반과 전환 대상

다음은 #72 본문·담당 답변에 맞춘 pet 전환 대상이다. #64에 존재하는 기본 예외 중 아래 네 종류만 사용하며, 413·415·503을 pet 입력·삭제·동시성 오류에 적용하지 않는다.

| 전환 대상 | 위치·부모 | 기존 처리 정리 |
| --- | --- | --- |
| `InvalidPetProfileException` | `pet/domain/exception`, `RuleViolationException` | 등록·수정·모드 서비스의 도메인 예외 재포장 제거 |
| `InvalidPetException` | `pet/application/exception`, `RuleViolationException` | 종·모드 문자열 변환 실패에만 유지, HTTP 설명 주석 제거 |
| `PetOwnershipViolationException` | `pet/domain/exception`, `PermissionDeniedException` | `PetOwnership`의 재포장과 중복 `PetAccessDeniedException` 제거 |
| `PetNotFoundException` | `pet/application/exception`, `ResourceNotFoundException` | 조회 실패의 메시지·404 유지 |
| `PetHasRecordsException` | `pet/application/exception`, `StateConflictException` | 기록 조회 결과로 직접 거절, `PetDeletionBlockedException`·재포장 제거 |
| `PetConcurrentModificationException` | `pet/application/exception`, `StateConflictException` | 어댑터의 기술 예외 변환·flush 유지 |

`ContentLimitExceededException`·`UnsupportedFormatException`은 `RuleViolationException` 하위지만 각각 413·415로 매핑된다. pet의 일반 입력 오류는 이 두 타입을 상속하지 않는다. `StorageUnavailableException`은 503이며 예상하지 못한 pet 예외를 일괄 감싸는 부모로 사용하지 않는다.

## 1. 입력 오류의 응답 유지 (#72-R1)
이름·생일 등 도메인 규칙 실패와 종·모드 문자열 오류는 400 `VALIDATION`으로 응답하고 저장 상태를 바꾸지 않는다.

#72-S1 실패: 입력 오류 [테스트]
- Given 회원 A가 로그인했고 콩이의 이름은 `콩이`, 모드는 `ACTIVE`다
- When 이름 21자 또는 미래 생일로 등록·수정하거나, 종류 `BIRD` 또는 모드 `PARK`를 보낸다
- Then 400과 `code: "VALIDATION"`, 해당 입력 오류 안내를 받는다
- Then 실패한 등록으로 목록이 늘지 않고, 수정·모드 변경 실패로 저장된 이름·모드가 바뀌지 않는다
- 기존 #46-S2·S7을 유지한다. 기존 안내 문구를 바꾸지 않는다. 서비스 단위 검사에서는 `InvalidPetProfileException`이 같은 인스턴스로 전파되는지 확인한다.

## 2. 소유자 판단과 조회 실패 (#72-R2)
소유자 판단은 기존 `Pet.requireOwnedBy`에 유지하고, 서비스 보조 객체는 조회와 호출만 조율한다.

#72-S2 실패: 다른 회원 [테스트]
- Given 회원 A의 콩이가 있고 회원 B가 로그인했다
- When B가 콩이의 상세 조회·수정·모드 변경·삭제를 요청한다
- Then 각각 403과 `code: "FORBIDDEN"`을 받고 저장된 프로필·모드는 그대로다
- 기존 #46-S9를 유지한다. 도메인에서 `PetOwnershipViolationException`을 던지고 `PetOwnership`이 이를 다른 예외로 바꾸지 않는지 확인한다.

#72-S3 실패: 없는 반려동물 [테스트]
- Given 회원 A가 로그인했고 요청할 petId의 반려동물이 없다
- When 해당 petId의 상세 조회·수정·모드 변경·삭제를 요청한다
- Then 각각 404와 `code: "NOT_FOUND"`를 받는다
- 조회 실패가 권한 실패나 500으로 바뀌지 않는다. `GetPetUseCase`의 입력·정상 결과 형식은 유지한다.

## 3. 삭제와 동시 수정의 충돌 유지 (#72-R3)
기록이 있는 반려동물의 삭제와 오래된 버전 저장은 409 `CONFLICT`로 거절한다.

#72-S4 실패·정상: 기록과 삭제 [테스트]
- Given 콩이에는 기록이 있고(`HasPetRecordsPort`가 true), 보리에는 기록이 없다(false)
- When 소유자가 각각 삭제를 요청한다
- Then 콩이는 409와 `code: "CONFLICT"`로 거절되고 그대로 남는다
- Then 보리는 204로 삭제되고 재조회는 404다
- 기존 #46-S10 번호와 요청 검사를 유지한다. 삭제 규칙의 도메인 단위 검사는 `DeletePetService`의 가짜 포트 검사로 옮겨 삭제 차단·삭제 포트 미호출·원본 보존을 확인한다. `docs/testing-coverage.md`에 이전/새 검증 위치와 빠지는 범위를 적고, 옮기는 테스트 설명에는 `(고침 #72: 기록 조회에 따른 삭제 거절을 애플리케이션에서 판단)`을 남긴다. 실제 기록 DB 연동이 검증됐다는 의미는 아니다.

#72-S5 실패: 동시 수정 [테스트]
- Given 같은 버전의 반려동물을 두 요청이 읽었다
- When 먼저 모드 변경이 저장된 후 오래된 프로필 수정이 저장된다
- Then 오래된 요청은 `PetConcurrentModificationException`으로 실패하고 HTTP 응답은 409 `CONFLICT`다
- Then 먼저 저장된 모드가 유지된다
- 기존 `PetConcurrentUpdateTest`의 실제 PostgreSQL 버전 충돌 검사에 HTTP 응답 매핑 검사를 보완한다. 영속성 어댑터의 저장·flush·충돌 변환은 유지한다.

## 4. 예외 의존 방향과 최종 처리 (#72-R4)
pet 예외는 기본 예외를 상속하고, 전환이 끝나면 pet 전용 핸들러 없이 동일 오류 응답을 제공한다.

#72-S6 경계 [테스트]
- Given Q2의 공용 테스트 변경 범위가 합의됐다
- When pet의 domain·application·adapter import와 구체 예외 상속을 검사한다
- Then domain에는 JPA·Spring·application·adapter 의존이 없고, 공통 예외 의존은 `global.exception`만 허용된다
- Then application 예외에 HTTP 상태·ErrorCode·JPA 의존이 없고 서비스는 도메인 예외를 재포장하지 않는다
- 기존 member 검사와 금지 규칙은 유지한다. 포괄적인 `global` 허용이나 검사 삭제로 통과시키지 않는다.

#72-S7 처리 경로 [테스트]
- Given pet 예외 전환이 모두 끝나고 `PetExceptionHandler`가 제거됐다
- When S1~S5의 실패를 실제 웹 요청으로 발생시킨다
- Then 공통 핸들러 경로로 400·403·404·409와 `code`, `message`를 받는다
- Then 개별 pet 예외를 전역 핸들러에 추가 등록하지 않아도 동작한다
- 기본 예외의 공통 매핑 검사는 기반 작업의 테스트를 재사용하고, pet 요청에서 발생한 예외가 그 경로에 연결되는지는 pet 회귀 검사로 확인한다. 도메인/서비스 직접 호출 결과를 HTTP 검사 대신 사용하지 않는다.

## 5. 입력 포트 호출자의 권한 검사 유지 (#72-R5)
pet의 입력·정상 결과 형식을 바꾸지 않고, 미디어가 사용하는 존재·소유 검사와 웹 이전 필터의 오류 응답을 보존한다.

#72-S8 호출자 회귀 [테스트]
- Given media가 pet의 `GetPetUseCase`로 소유를 확인하고 회원 A·B가 서로 다른 반려동물을 갖는다
- When B가 A의 petId로 미디어 업로드 세션을 만들거나 A의 파일 수신을 시도한다
- Then 기존 미디어 #33-S2·S17과 같이 403이며, 파일 수신은 multipart 파싱 전에 거절된다
- When A의 정상 `GetPetQuery`와 존재하지 않는 petId의 Query를 pet 입력 포트에 전달한다
- Then 정상 결과 구조는 유지되고 없는 대상은 `ResourceNotFoundException` 계열로 실패한다
- pet 포트의 정상·실패 계약은 pet 소유 테스트에서 검사한다. 실제 미디어 요청 회귀는 기존 `MediaUploadScenarioTest.access`·`guardBeforeParsing`을 실행해 확인하며 미디어 테스트를 임의 수정하지 않는다. 없는 petId의 미디어 404 HTTP 검사까지 기존 두 테스트가 검증한다고 주장하지 않는다. 추가 미디어 검사가 필요하면 미디어 담당과 범위를 맞춘다.

## 범위 밖
- `member`, 마이페이지(`/me`, `/me/activity`, 내 정보·닉네임·세션·친구 수), 관리자 기능 및 모든 프론트 코드·테스트·리팩터링. 마이페이지는 당분간 다윗 담당이다.
- `global.exception`·`GlobalExceptionHandler`·공통 응답 테스트 수정, 공통 예외 추가/재설계. #64가 제공한 기반을 재사용한다. 미디어의 413·415·503 정책을 pet에 확대하지 않는다.
- 다른 도메인의 예외 전환, pet 포트/Result 이름 정리, 테이블·컬럼·API 경로·정상 응답 변경.
- 가족 권한 확대, 사진 업로드, 실제 diary/question 기록 존재 확인 연동, 태그 정책. memory 샘플은 #64에서 제거됐으므로 추가 삭제 작업을 만들지 않는다. 실제 map의 `RecordLocation`·`record_id` 계약은 유지한다.
- 소유 판단을 서비스로 옮기는 리팩터링. 이미 존재하는 도메인 판단을 유지한다.
- 로그인 401 정책, 공통 MVC 표준 오류 응답 통일, 커밋 시점 낙관적 락 변환 위치 결정. 어댑터 밖에서 발생하는 충돌까지 이 계획으로 해결했다고 주장하지 않는다.
- `Pet.restore` 전체 검증 재설계, 기술 장애의 신규 전역 매핑, 입력 파서·기술 변환의 모든 catch 제거.

## 작업 순서
아래 각 구현 단위는 관련 예외·호출부·회귀 검사를 함께 담아 단독으로 통과시킨다. 문서 승인과 선행 조건 확인 없이 코드를 구현하지 않는다.

1. 계획 문서 검토: Q1·Q3의 확인된 결정을 반영하고 Q2의 공용 검사 수정 범위를 먼저 합의한다. 이후 공통 계약 변경이 실제 제안되면 영향 범위만 재검토한다. 여러 담당이 공유하는 #72의 다른 도메인 계획과 시나리오 번호 중복 여부도 이슈 담당과 맞춘다. 현재 #72-S1~S8은 이 pet 계획의 제안 번호다. map의 산책 예외 추가 댓글은 map 담당 범위이며 이 계획에 섞지 않는다.
2. 입력 오류 전환 — S1·S6: `InvalidPetProfileException`을 `pet/domain/exception`으로 이동해 `RuleViolationException`을 상속하고 Create/Edit/ChangeMode 서비스의 재포장만 제거한다. `InvalidPetException`은 종·모드 파서가 쓰므로 같은 기본 예외를 상속한 application 예외로 유지한다. 파서는 `valueOf`의 예상한 `IllegalArgumentException`만 변환한다. 실패 문구는 예외 생성자/이유별 생성 메서드에 모은다.
   - 파일: 위 예외, 세 서비스, `Pet`의 import, `PetModeParser`·`PetSpeciesParser`의 문구 생성 호출, 관련 pet 테스트. Q2 합의가 필요한 최소 경계 검사를 먼저 준비한다.
   - 확인: backend에서 `./gradlew test --tests '*PetTest' --tests '*PetScenarioTest' --tests '*PetExceptionPropagationTest' --tests '*HexagonalArchitectureTest'` (새 전파 테스트는 구현 PR에서 추가).
3. 권한·미존재 전환 — S2·S3·S6: `PetOwnershipViolationException`을 domain/exception의 `PermissionDeniedException` 하위로 옮긴다. `PetOwnership`은 조회·도메인 호출만 유지하고 중복 `PetAccessDeniedException`은 참조 확인 후 제거한다. `PetNotFoundException`은 application의 `ResourceNotFoundException`을 상속한다. `GetPetUseCase`, Query, Result의 시그니처는 바꾸지 않는다.
   - 파일: 해당 예외·보조 객체·도메인 import·pet 단위/요청 테스트와 포트 계약 검사. media/map 호출자는 오류가 공통 기본 예외로 바뀐다는 영향만 공유하고 해당 코드는 수정하지 않는다.
   - 확인: 2단계 검사로 소유자·타인·없는 대상의 요청 결과와 전파를 확인한다. S8의 기존 미디어 권한·필터 검사도 실행한다.
4. 삭제·동시성 전환 — S4·S5·S6: `DeletePetService`가 `HasPetRecordsPort`의 true 결과에서 `PetHasRecordsException`을 직접 던진다. 중복 도메인 예외·`Pet.requireDeletable`·재포장을 제거한다. `PetHasRecordsException`과 `PetConcurrentModificationException`은 application의 `StateConflictException`을 상속한다. 기술 예외를 바꾸는 어댑터 catch와 flush는 유지한다.
   - 파일: 삭제 예외·서비스·`Pet`의 해당 메서드/기존 단위 검사, 새 `DeletePetServiceTest`·충돌 예외·관련 pet 테스트. 도메인에서 서비스로 옮긴 삭제 거절 검사를 검증 범위 대응표에 최소 기록한다.
   - 확인: `./gradlew test --tests '*PetTest' --tests '*DeletePetServiceTest' --tests '*PetScenarioTest' --tests '*PetExceptionPropagationTest' --tests '*PetConcurrentUpdateTest' --tests '*HexagonalArchitectureTest'`.
5. pet 전용 핸들러 제거 — S1~S8: 전환 누락 예외와 참조가 없음을 확인한 뒤 `PetExceptionHandler`를 제거하고 전역 매핑을 통과하는 회귀 검사를 실행한다. 앞선 단계에서는 아직 전환하지 않은 application 예외의 기존 매핑을 유지한다.
   - 파일: `pet/adapter/in/web/PetExceptionHandler.java`, pet 회귀 테스트. 전역 핸들러를 고치지 않는다.
   - 확인: 4단계 pet 검사와 S8의 미디어 선택 검사, `git diff --check`. 공통 매핑의 핵심이 바뀌지 않았다면 `CommonExceptionResponseTest` 결과를 재사용하고 pet 연결 검사를 대신 생략하지 않는다. 전체 테스트는 CI에 맡기고 같은 코드·환경의 성공 결과는 재사용한다.

### 선택 검사와 실행 환경

- 입력·권한·삭제 PR에서는 바뀐 pet 단위/포트/요청 검사만, 동시성 PR에서는 `PetConcurrentUpdateTest`까지 실행한다. 마지막 핸들러 제거에서 네 상태 코드와 호출자 경로를 다시 확인한다. 새 전파·포트 테스트는 pet 패키지 안에 둔다.
- 미디어 호출자 회귀 명령(backend에서): `./gradlew test --tests '*MediaUploadScenarioTest.access' --tests '*MediaUploadScenarioTest.guardBeforeParsing'`.
- #64 이후 pet 요청 검사도 전체 Spring 컨텍스트에서 media 설정을 함께 올린다. [미디어 실행 환경](../media/runtime.md#실행-환경)의 Linux·PostgreSQL Testcontainers·도구 요구를 먼저 확인한다. macOS에서 환경 실패가 나면 pet 소스 오류나 권한 정책 회귀로 단정하지 않고 지원되는 Linux 컨테이너/CI에서 필요한 검사만 확인한다. 도구 설치·공유 설정 변경을 pet 전환에 묶지 않는다.
- 다른 도메인의 실패를 피하려고 전체 테스트를 끄거나 기존 검증을 삭제하지 않는다. 미디어 테스트를 보완해야 한다면 담당자에게 필요한 검증 계약을 남기고 합의 전 수정하지 않는다.

## 가정
- 정상 API·응답·문구·권한·데이터 보존 정책은 #46 그대로 유지한다. 기본 예외 상속과 내부 전달 경로만 전환한다.
- 삭제 불가의 application 분류와 기본 예외 4종 사용은 #72 담당 답변·본문에 반영된 기준이다. 과거 Q1/Q3 미결 메모를 근거로 중복 예외를 유지하거나 공통 기반 구현을 새로 만들지 않는다.
- 아키텍처 공용 검사와 테스트 번호는 다른 담당과 조율하고, 해당 합의가 pet 범위를 넘어선 코드 수정 허가를 뜻하지 않는다.
- #64의 공통문서에는 불변 record·조회 결과 재사용·웹 경계 허용을 '제안'으로 설명한 문구가 남아 있다. 병합된 코드·문서의 제한 범위를 따르되 이를 pet Result 이름 변경, 도메인 상태 객체의 record 전환, 서비스 재포장 허용의 근거로 확대하지 않는다. 해석이 달라지는 부분은 별도 합의한다.
- 구현 PR은 #72를 관련 이슈로만 연결한다. pet 항목 완료와 PR 번호만 기록하고 전체 이슈를 `closes`로 닫지 않는다.

## 참고
- [공통 예외](../backend-exceptions.md), [헥사고날 구조](../backend-architecture.md), [검사 선택·리뷰 기준](../testing.md).
- 기록 존재 포트의 가짜 응답 검사와 실제 기록 도메인 연결은 다르다. 삭제 거절 테스트가 실제 diary DB 연결을 입증하지 않는다.
- 기본 예외가 먼저 전역 응답으로 매핑돼 있어야 서비스의 재포장과 전용 핸들러를 안전하게 제거할 수 있다. 메시지에 기술 예외·저장 경로를 넣지 않는다.
