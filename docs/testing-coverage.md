# 검증 범위 대응표

## 위치 저장 경로와 지도 리뷰 반영 (#53)

#34 후속 설계에서 같은 앱·같은 DB의 위치 쓰기를 다이어리로 통일한다. 아래는 **계획된 확인 위치**의 변경이다. 이 PR에서 구현 테스트를 삭제·이동하거나 기능 통과를 주장하지 않는다. 기존 시나리오 번호는 유지한다.

| 기존 기준 | 새 확인 위치 | 남는 확인 범위 |
| --- | --- | --- |
| #19-S1·S2·S4: 별도 위치 PUT/DELETE로 저장·좌표 검증·삭제 | diary body 테스트 #32-S16·S17 및 map 입력 포트 테스트 #44-S1·S4·S5·S6 | 단일 body의 권한·검증·원자성·지도 반영. 별도 위치 PUT/DELETE는 만들지 않아 해당 HTTP 경로의 정상 저장 테스트는 없음 |
| #19-S7의 쓰기 거부·작성자 확인 역조회 | diary 권한 #32-S2·S12, #44-S8 | map 외부 쓰기/역조회 포트가 없어 해당 호출 테스트는 없음. map 핀 조회의 미인증·타 작성자 미노출은 #19-S7에서 유지 |
| #44-S7·#32-S18: 지도 실패 시 일기 보존·비동기 재시도 | 동일 DB 트랜잭션 롤백 검사, 외부 주소 실패 검사 | 지도용 작업자·syncStatus·재시도 API 검사는 제거. DB 실패는 전체 롤백, 주소 실패만 좌표 저장 허용으로 정책 변경 |
| #33-S13·S14 및 #44-S1·S3·S8: GPS 후보 처리 | 업로드 사전 동의·후보 검증, #33-S19·#44-S11·S12 추가 | 미동의 후보 미생성·업로드 전 GPS 제거·서버 동의 검사. 동의 UI/전처리는 직접 검사 포함 |
| #44-S6·#32-S17: 삭제 표시 행으로 늦은 쓰기 방지 | 부모 버전 검사·삭제 경합 롤백·실제 map 행 삭제, APPLIED/DUPLICATE/STALE 검증 | tombstone/deleted 필터·좌표 NOT NULL 해제 검사는 없음. 기존 DB NOT NULL을 유지하고 NONE 최초 저장에 행을 만들지 않는 경계 검사로 대체 |
| #44-S7·#32-S18: 외부 주소 조회 위치 미확정 | 저장 트랜잭션 전 주소 조회·apply 외부 호출 금지 검사 | 주소 실패는 비어 있는 주소로 진행, 조회 중 동시 수정은 저장 시 재검증·충돌 |
| media 물리 파일 삭제·검증 작업 | 기존 #33 검증·참조·삭제 재시도 검사 유지 | DB 밖 파일 I/O는 동기 지도 반영과 별개이며 삭제 작업/재시도를 제거하지 않음 |

구현 PR에서는 위 자동/직접 검사를 실제 테스트 클래스·메서드와 매핑하고, 아직 자동화하지 못한 범위를 확인 방법에 적는다. 설계 문서: [지도 1단계](map/memory-location.md), [위치 연결](map/photo-location.md), [다이어리](diary/README.md), [미디어](media/README.md).

## 산책 좌표 한도와 마지막 활동 시각 (#73)

산책 도메인과 저장소 PR(#76)의 리뷰로 좌표 개수 검사를 둘로 나눴다. 요청 하나의 크기와 상태는 `Walk.requireAcceptingPoints`가, 산책 전체의 누적 한도는 실제로 새로 쌓인 개수로 `Walk.requireCapacity`가 보고, 마지막 활동 시각은 두 검사를 통과한 뒤에 `Walk.markPointsReceived`로만 바꾼다. 아래는 이 변경으로 검증 위치가 옮겨지거나 아직 자동으로 확인하지 못하는 범위다.

| 기존 기준 | 새 확인 위치 | 남는 확인 범위 |
| --- | --- | --- |
| #73-S14: 누적 5,000개를 넘기는 요청이 거절되어도 마지막 활동 시각이 그대로 (`WalkTest`가 `acceptPoints` 하나로 확인) | `WalkTest`의 `capacityRejectionKeepsActivity`(검사 메서드가 값을 바꾸지 않고 누적 한도 거절 뒤에도 활동 시각이 그대로), `requireMethodsDoNotChangeState` | **자동으로 확인하지 않는 부분:** 한도를 넘긴 요청에서 새로 쌓은 좌표와 활동 시각 저장이 함께 롤백되는지. 서비스가 `requireCapacity`를 쌓은 뒤에 부르고 예외로 트랜잭션을 되돌리는 순서는 도메인·저장소 테스트로 확인할 수 없다 |
| #73-S14: 한 요청 101개 거절 | `WalkTest`의 `batchLimitComesFirst`, `tooLargeBatchKeepsActivity`. 빈 묶음은 `emptyBatchIsRejected` | 요청 형식 검사(`@Size`)와 400 응답은 API를 연결하는 PR에서 확인 |
| #73-S6: 한도를 채운 마지막 요청의 재전송 | `WalkTest`의 `resendAtLimitIsNotRejected`(새로 쌓인 개수 0이면 통과) | 같은 묶음을 실제로 다시 보내 204를 받고 좌표가 늘지 않는 요청 수준 확인은 API를 연결하는 PR |
| #73-S14: 동시 요청에서도 누적 한도를 지킴 | `WalkRowLockTest`(두 트랜잭션으로 행 잠금 확인) | 한도 직전에서 여러 요청이 동시에 와도 합계가 5,000개를 넘지 않는지는 서비스 수준 확인이 필요 |

좌표 쌓기 API PR(#73 3단계)에서 위 "남는 확인 범위"를 `WalkPointsScenarioTest`로 실제 DB와 요청에서 확인했다. (고침 #73: 좌표 쌓기 API 연결)

- 누적 한도를 넘긴 요청 뒤에 `walk_points` 개수와 `walks.last_activity_at`이 요청 전과 같다(새 좌표와 활동 시각이 함께 롤백된다): `walkLimit`.
- 한도 직전(4,990개)에서 재전송을 섞은 묶음은 새로 쌓이는 개수로 세어 5,000개까지 쌓이고, 한도에 닿은 뒤 같은 묶음을 다시 보내면 204이며 좌표가 늘지 않는다: `resendDoesNotCountTowardsLimit`. 같은 시각의 좌표를 다른 값으로 다시 보내도 먼저 쌓인 값이 남는다: `resendKeepsFirstValue`.
- 한도 직전의 산책에 새 좌표 100개씩을 8개 요청이 동시에 보내도 하나만 쌓이고 합계는 5,000개다: `concurrentBatchesNeverExceedLimit`.
- 요청 형식(위도·경도 범위, 값 없음, 시각 형식, 빈 묶음, 101개)과 400 응답, 소유자 검사가 크기·상태 검사보다 먼저인 것(403), 끝나거나 중단된 산책의 409와 활동 시각 유지: `invalidCoordinateRejectsWholeBatch`, `malformedBodiesAreRejected`, `batchLimitPerRequest`, `onlyOwnerAddsPoints`, `notAcceptingWalks`.

자동으로 확인하지 않는 부분: 좌표를 읽는 API(날짜별 조회)는 다음 단계라서 이 테스트는 저장된 좌표를 DB에서 직접 읽는다. 요청 본문 크기의 상한(매우 큰 JSON)은 서버의 기본 설정에 맡기고 검사하지 않는다.

설계 문서: [산책로](map/walk-route.md)

## pet 삭제 거절의 애플리케이션 이동 (#72)

| 기존 검사 | 새 검사 위치 | 유지·제외 범위 |
| --- | --- | --- |
| `PetTest.recordsBlockDeletion`의 #46-S10: 기록이 있으면 도메인에서 삭제 거절·이름 보존 | `DeletePetServiceTest.recordsBlockDeletion`의 #46-S10: 가짜 기록 포트가 true이면 `PetHasRecordsException`·삭제 포트 미호출·원본 보존. false이면 삭제 호출 | 기존 `PetScenarioTest.deletePet`의 HTTP 409·원본 보존 및 정상 204·재조회 404를 유지한다. 실제 diary/question 기록 DB 연결은 검증하지 않는다. |

기록 유무를 조회한 결과에 따른 삭제 거절은 애플리케이션에서 판단한다. 기존 #46-S10 번호를 유지하며 테스트 설명에 `(고침 #72: 기록 조회에 따른 삭제 거절을 애플리케이션에서 판단)`을 표시한다.

## 커뮤니티 글 목록 조건 (#21)

#83 리뷰를 반영해 목록의 `size`·`cursor` 범위 검사를 서비스에서 웹 요청(`ListPostsRequest`의 Bean Validation)으로 옮겼다. 시나리오 번호는 그대로 둔다.

| 기존 기준 | 새 확인 위치 | 남는 확인 범위 |
| --- | --- | --- |
| #21-S13 `ListPostsServiceTest.outOfRangePageIsRejected`: 서비스가 `size` 0·51, `cursor` 0을 `InvalidPostPageException`으로 거절 | `PostScenarioTest.listOutOfRange`: 같은 값을 HTTP로 보내 400 `VALIDATION`과 오류 문구 확인 | 서비스는 범위를 다시 검사하지 않는다. 웹 요청을 거치지 않고 서비스를 부르는 경로는 없다 |
| #21-S4 `ListPostsServiceTest.defaultSizeIsTwenty`: `size`가 없으면 서비스가 20개씩 읽음 | `PostScenarioTest.listWithCursor`: 첫 요청에 `size`를 보내지 않고 20개와 `nextCursor`를 받는지 확인 | 기본값은 `ListPostsRequest`가 채운다. 서비스 단위 테스트는 기본값을 보지 않는다 |

## 커뮤니티 글 동시 수정 (#21)

리뷰를 반영하면서 #21-S14의 확인 위치를 서비스·포트 호출에서 HTTP 요청으로 옮겼다. 시나리오 번호는 그대로 둔다.

| 기존 기준 | 새 확인 위치 | 남는 확인 범위 |
| --- | --- | --- |
| #21-S14 수정 중 삭제: 수정 스레드는 트랜잭션 안에서 `LoadPostPort`로 읽고 `Post.edit` 뒤 `UpdatePostPort.update`를 직접 부르고, 삭제 스레드는 그 사이에 유스케이스로 삭제를 커밋한다. 수정 쪽 예외가 `PostConcurrentModificationException`인지 확인 | `PostConcurrentUpdateTest.editAfterDeleteIsRejected`: PUT이 글을 읽은 직후 다른 스레드가 삭제를 커밋하고, PUT 응답이 409 `CONFLICT`인지와 DB에 원래 제목·본문과 `deleted_at`이 남았는지 확인 | 예외 타입은 직접 보지 않고 HTTP 응답 코드로 확인한다. 끼어드는 삭제는 HTTP가 아니라 유스케이스 호출이다 |
| #21-S14 겹친 수정: 먼저 읽은 글로 `UpdatePostPort.update`를 불러 예외 타입을 확인 | `PostConcurrentUpdateTest.laterEditIsRejected`: PUT이 글을 읽은 직후 다른 스레드가 수정을 커밋하고, PUT 응답이 409 `CONFLICT`인지와 먼저 저장된 값이 남았는지 확인 | 영속성 어댑터를 직접 부르는 검사는 `conflictKeepsCause` 하나로, 409 예외가 저장소의 충돌 예외를 cause로 남기는지만 본다(#84 리뷰). 끼어드는 수정은 HTTP가 아니라 유스케이스 호출이다 |

## 다이어리 첨부 지원 (#32, #105)

| 기존 검사 | 새 확인 위치 | 유지·제외 범위 |
| --- | --- | --- |
| `DiaryWritePolicyTest` #32-S23의 첨부 요청 일괄 거부 및 `DiaryScenarioTest.unsupported`의 attachmentIds 요청 409 | `DiaryAttachmentScenarioTest` #32-S3·S9·S10: 실제 READY 첨부 성공, JSON 누락/null/공백, 중복·소유권·아이·용도·만료·상태 검증 및 저장 실패 전체 롤백 | 첨부는 더 이상 미지원이 아니다. 기존 위치 AUTO/MANUAL 거부와 잘못된 JSON 거부는 유지한다. 가족·지도·프론트 연동은 검증하지 않는다. |
| 텍스트 전용 본문 공백 거부 | 기존 `DiaryEntryTest`의 첨부 없는 공백 거부를 유지하고, `DiaryAttachmentScenarioTest` #32-S3·S8·S9에서 첨부 전용 생성·전환·마지막 첨부 제거 거부 검사 | 본문과 첨부가 모두 없는 일기는 계속 거부한다. PATCH 생략과 명시적 null을 구분한다. |

기존 #32-S21 수정/삭제 경합 검사는 유지한다. 첨부 갱신은 같은 부모 행 잠금 뒤 ID 순서 자산 잠금으로 직렬화하며, 잠금 재조회에서 확인한 버전 충돌도 공통 409로 변환한다. 테스트를 서비스 호출 검증만으로 축소하지 않는다.


## 다이어리·질문 명칭 분리 (#32)

| 기존 검사 | 새 검사 | 변경과 남는 범위 |
| --- | --- | --- |
| frontend workspace.test.ts #12-S3 날짜당 질문 일기 1개·날짜별 질문 포트 | question-workflow.test.ts #12-S3 (고침 #32) | 날짜 해시 배정 대신 미답변 질문 유지·작성자와 배정 질문별 중복 방지·동시 저장·최초 등록시각 유지. 날짜당 1개 제한은 폐기 |
| workspace.test.ts #12-S6 스냅샷·혼합 컬렉션 필터 #12-S4 | question-workflow.test.ts #12-S6 (고침 #32), workspace.test.ts #12-S4 | 별도 저장소와 날짜 조회, 모드 전환 보존. question-workflow.test.ts #12-S4 (고침 #32)에서 같은 날짜의 일기+질문 혼합 fixture로 페이지별 정확 ID·날짜 필터의 답변 제외·조회 후 상태 불변을 복원 |
| life.test.ts #12-S10 가족 편집자가 타인 답변 수정 | life.test.ts #12-S10 및 question-workflow.test.ts #12-S10 (고침 #32) | 작성자별 수정 제한, 가족 공통 진행과 복수 답변. 실제 서버 가족 API 검증은 포함하지 않음 |
| workspace.test.ts #12-S5 손상 v1 읽기 | 기존 검사 유지 + workspace-migration.test.ts #32-S29 | v1 보존·v2 재조회·용량 실패 시 기존 기록 조회·사용자 저장 실패 보존·공간 확보 후 재이관·손상 v2 우회 금지·초기화 후 데이터 부활 방지 |
| 지도 RecordLocation 테스트 | 동일 시나리오의 DiaryLocation 테스트 | 동작 유지. DiaryNamingMigrationTest #32-S28에서 PostgreSQL 기존 행·FK·유니크·시퀀스·재실행·충돌 보존 추가 |

브라우저 저장 데모는 서버 업로드·가족 멤버십·질문 API의 완성을 뜻하지 않는다. HTTP 연동과 복수 탭의 원자적 동시성 검사는 후속 범위다.

#115 재리뷰: DB 이관은 #32-S28, 브라우저 이관은 #32-S29로 등록한다. #32-S1/S2의 기존 CRUD·인증/권한 시나리오를 변경하지 않는다. 질문의 공백 거부·활성 풀 전체 순환은 #12-S3, 완료 배정 기반 이력과 마지막 답변 삭제 후 재답변은 #12-S10 (고침 #32)에서 확인한다.

#115 추가 재리뷰: `workspace-migration.test.ts` #32-S29에 질문 풀 이관이 필요한 저장소(5MiB 한도·512바이트 여유)의 기존 회원 확인→SharedWorkspaceRepository 조회 검사를 추가했다. `life.test.ts` #32-S29에서 무변경 회원 확인은 저장 생략, 이름 변경·신규 회원은 직렬 저장함을 확인한다. 실제 AppProvider의 기존 데모 진입과 열람자/모드별 빈 질문 안내는 브라우저 직접 확인 범위이며 실행 결과는 PR에 기록한다.

## 다이어리 위치 입력·원본 저장 준비 (#32, #44)

- `DiaryScenarioTest` #32-S23은 유효한 미연동 MANUAL/AUTO의 409를 유지한다. 좌표가 없는 MANUAL처럼 형식이 잘못된 요청은 웹 입력 단계 400으로 분리했다. 오류 입력을 성공으로 무시하지 않는 기존 검증은 유지하며 위치 연결 완료로 표시하지 않는다.
- `DiaryLocationRequestValuesTest` #44-S2/S4/S7은 JSON 생략/명시 NONE/MANUAL/AUTO·좌표/장소명·서버 필드 주입 거부와 코어 입력 보존을 검사한다. 실제 지도 입력 포트/주소 조회 호출은 아직 검사하지 않는다.
- `DiaryLocationIntentPersistenceTest` #44-S4/S13은 실제 PostgreSQL에서 위치 원본 저장·본문 수정 보존·버전 충돌·NONE 전환·미이관 NULL과 기존 핀 보존·스키마 반복 적용·double precision 왕복을 검사한다. 지도 행의 초기 백필·명시적 정정·일기 삭제와 핀의 원자성은 후속 map 연결 범위다.
- `DiaryEntryTest`와 `DiaryLocationIntentTest`는 위치 작성자와 값 불변식을 검사한다. 이 준비 작업만으로 #44-S4/S13 전체 또는 #32-S12/S16~S18을 완료 처리하지 않는다.

## 수동 위치의 실제 지도 연결 (#32, #44)

| 기존 확인 | 현재 검사 | 범위·후속 |
| --- | --- | --- |
| #32-S23의 MANUAL/AUTO 미지원 409 | DiaryScenarioTest·DiaryWritePolicyTest | 유효한 MANUAL은 실제 저장으로 전환하고 AUTO만 409 유지. 잘못된 MANUAL JSON은 400 유지 |
| #44-S4/S7/S8 준비 단계의 입력·저장 검사 | DiaryLocationScenarioTest | 실제 API 생성→핀 조회→위치만 PATCH→KEEP→NONE→삭제, 주소 TX 경계·255자·권한 응답·첨부 사전 검사. AUTO/가족/프론트는 제외 |
| #44-S6 revision·부모 경합 | DiaryLocationScenarioTest atomicApply/manualEditDeleteRace/staleReconcile | 동시 최초 삽입 APPLIED/DUPLICATE, STALE, 계약 위반, 관측 경합과 실제 핀 수정/삭제 경합·오래된 요청의 핀 부활 금지 |
| #44-S7 전체 롤백 | DiaryLocationScenarioTest rollbackCreateAndEdit/rollbackDelete/deletionJobRollback | 지도 DB 쓰기/삭제 후 실패를 주입해 부모·첨부·참조·실제 핀·물리 삭제 작업 등록을 원자적으로 취소 |
| #44-S13 미이관 상태 보존 | DiaryLocationIntentPersistenceTest | 원본 값 왕복 외 정상 핀 백필·revision 맞춤·반복 적용·초기 ID 충돌/고아 핀 보존 및 중단 추가 |
| DiaryConcurrencyTest #32-S22 첫 생성/아이 삭제의 바깥 TX latch | 동일 테스트의 SaveDiaryEntryPort spy latch | HTTP를 감싼 바깥 TX 대신 실제 DB 쓰기 내부에서 첫 INSERT 후 커밋 전 대기. 반대 실행 순서·기록/아이 보존 검사 유지 |
| DiaryAttachmentScenarioTest #32-S10 참조 획득/만료 정리의 바깥 TX latch | 동일 테스트의 DiaryMediaPort.change spy latch | 실제 참조 획득 후 DB TX 안에서 대기·만료 정리의 잠금 대기·참조/파일/작업 보존 유지 |

사전 조회로 열린 JPA 문맥은 부모·자산 잠금 후 refresh하며, 실제 저장 시 소유권·버전·상태를 다시 검사한다. 기존 코드의 실행 테스트를 삭제하거나 경합을 순차 호출로 축소하지 않았다. 이전 준비 단계에서 'map 호출 미검사'로 남긴 범위 중 MANUAL/NONE은 위 테스트로 연결했다.
