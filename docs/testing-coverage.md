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

사전 조회로 열린 JPA 문맥은 부모·자산 잠금 후 refresh하며, 실제 저장 시 소유권·버전·상태를 다시 검사한다. 최초 연결 단계에서는 기존 실행 테스트와 경합 검사를 유지했다. 이후 미사용 포트 제거에 따른 검사 변경은 아래 대응표에 기록한다. 이전 준비 단계에서 'map 호출 미검사'로 남긴 범위 중 MANUAL/NONE은 위 테스트로 연결했다.


## 지도 입력 포트 경계와 미사용 저장 포트 정리 (#19, #44, #72)

| 기존 검사 | 현재 검사 | 자동 확인에서 빠지는 범위·이유 |
| --- | --- | --- |
| DiaryLocationPersistenceAdapterTest #19-S1/S4/S5, #44-S13의 save/load/delete | 같은 테스트의 lock/write/read/remove 및 loadInArea | 기존 포트 3개와 save/load/delete 구현을 제거했다. 왕복·수정·물리 삭제·핀 필터·출처 기본값·JPA 문맥 검사는 운영 포트로 유지 |
| #19-S1 uniquePerRecord와 ConstraintTest의 중복 생성→409 변환 | uniquePerRecord의 실제 DB 유일 제약, DiaryLocationScenarioTest.atomicApply #44-S6 | 이제 최초 삽입을 직렬화하여 APPLIED/DUPLICATE로 수렴한다. 제거한 예외의 제약 이름 대소문자 비교·409 변환은 검사하지 않음. 다른 무결성 오류 전달은 MapPersistenceFailureTest #72-S10으로 유지 |
| #19-S1 updateMissing, #19-S4 deletedLocationIsNotServedFromCache | #44-S6 mutationAndPinQueriesShareJpaContext, lockRefreshesPreviouslyReadEntity 및 경합 시나리오 | 제거된 행 ID 기반 save의 유령 ID 거절은 검사하지 않음. 운영 포트는 diaryId로 반영하며 삭제 뒤 read/핀 조회에 캐시가 남지 않는지 검사 |
| #19-S7 cannotOverwriteWithDifferentOwner | DiaryLocationTest.decidesIncomingRevision #44-S6, DiaryLocationScenarioTest.atomicApply/authorAndVersion #44-S6/S8 | 구 save의 행 ID·다른 diaryId 대조는 제거. 현재 계약의 pet/작성자 불일치 판정과 실제 포트 경합·HTTP 작성자 권한을 검사 |
| MapPinScenarioTest #19의 SaveDiaryLocationPort fixture | DiaryLocationStorePort lock/write fixture | 핀 목록 HTTP 검증 범위는 유지 |
| 내부 ID의 400 예외 검사 | GetDiaryLocationServiceTest #44-S6 | 내부 ID·관측값 계약 위반과 손상 관측값 무검증 보존을 검사. 사용자 MANUAL JSON 오류 400 검사는 기존 diary 파서·시나리오에 유지 |

DiaryLocationContractErrorTest #44-S6은 실제 전역 MVC advice에서 내부 ID·좌표 오류가 400으로 처리되지 않고 ServletException으로 컨테이너에 전파되는지 검사한다. 실제 서버의 HTTP 500 응답 본문은 이 검사 범위가 아니다.

DiaryLocationState.value()는 사용하지 않아 제거했다. 관측 상태는 원본 그대로 유지하며 새 좌표 입력만 지도 서비스에서 도메인 값으로 변환한다. HexagonalArchitectureTest.mapBoundaryUsesInputContracts #44-S6으로 기존 구조 검사를 이동했다. 기존 단일 DiaryMapAdapter 파일·DiaryLocation 이름 필터 대신 diary/adapter 전체 및 map/application/port/in 전체를 재귀 조회한다. 기존 import 검사와 도메인 타입 노출 금지를 유지하고 완전 수식 map.domain 참조도 검사한다. 이름이 다른 새 전달 타입·어댑터도 포함되며 기존 자동 확인에서 빠지는 범위는 없다. 소스 문자열 기반의 기존 저장소 검사 방식을 재사용하며 바이트코드 의존성 분석을 대체하지는 않는다. DiaryLocationPortValuesTest #44-S6은 LocationSource·입력 계약·diary 관측값의 출처 이름 집합 일치와 모든 현행 출처의 지도 경계 왕복을 검사한다. 이 정리로 AUTO/GPS·가족 공유·프론트 검증이 추가되는 것은 아니다.

## 질문·다이어리 구조 정리 (#31, #32)

| 기존 검사 | 새 검사 위치 | 유지하는 범위 |
| --- | --- | --- |
| `QuestionAnswerWriteServiceTest.pastAnswerDoesNotAdvance` (#31-S6/S9) | `CreateQuestionAnswerServiceTest.pastAnswerDoesNotAdvance` | 생성 유스케이스 분리에 따른 클래스 이름만 변경한다. 과거 회차에 다른 작성자가 답해도 현재 공통 진행을 다시 바꾸지 않는 단위 검증과 실제 가족 연동 미포함 범위를 유지한다. 삭제하거나 자동 확인에서 제외하는 시나리오는 없다. |

(고침 #31: 답변 유스케이스별 서비스 분리에 따른 기존 테스트 위치 대응)

## 프론트 구조 개편의 경계 검사 (#182)

프론트를 `features/<도메인>`의 `components`·`hooks`·`api`·`types`·`utils`로 나누고 브라우저 저장 업무를 `mocks/handlers`로 옮기면서, 계층 이름(`domain`·`application`·`adapters`)으로 검사하던 `tests/hexagonal-architecture.test.ts`를 `tests/dependency-boundaries.test.ts`로 바꿨다. 시나리오 번호는 그대로 둔다. 설계 문서: [프론트 구조 개편](frontend-structure/README.md)

| 기존 기준 | 새 확인 위치 | 남는 확인 범위 |
| --- | --- | --- |
| #52-S9 코어(`*/domain`·`*/application`·`lib/application`)는 React·Next·HTTP·`adapters`를 가져오지 않고 `window`·`document`·`localStorage`·`fetch`를 쓰지 않는다 | #52-S9 "기능의 api·types·utils와 lib는 React·Next·mocks를 가져오지 않는다", #52-S9 "기능의 types·utils와 기능 폴더 바로 아래 파일은 HTTP 클라이언트를 가져오지 않는다", #52-S9 "기능의 types·utils·바로 아래 파일과 lib/date·lib/application은 브라우저 전역과 fetch를 쓰지 않는다" | `adapters` 금지는 계층 폴더가 없어져 "계층 폴더가 다시 생기지 않는다" 검사로 바꿨다. HTTP 금지에서는 `ApiClient`로 세션을 만드는 `features/member/session.ts`를 뺐다. 브라우저 전역 검사에서는 `features/map/utils/naverMaps.ts`(지도 SDK), `features/postcard/utils/downloadPostcard.ts`(내려받기), `features/map/LocalActiveWalkStore.ts`(#167 확인 뒤 #200에서 제거)를 뺐다. 예외 파일이 없어지거나 더 이상 걸리지 않으면 테스트가 실패한다. `api`는 HTTP 클라이언트를 쓰는 곳이라 HTTP 금지 대상이 아니다. 옛 코어였던 브라우저 저장 업무는 `mocks/handlers`로 옮겨 `localStorage`를 쓰므로 검사하지 않는다 (고침 #182: #202 리뷰로 HTTP 금지와 `utils` 검사를 되살림) |
| #52-S9 마이페이지 화면은 `adapters/outbound`와 `lib/http`를 모른다 | #52-S9 "화면과 훅은 HTTP 클라이언트를 직접 가져오지 않는다"(모든 `features/*/components`·`hooks`와 공통 `components`) | 마이페이지만 보던 검사를 모든 화면으로 넓혔다 |
| #182 회원·반려동물 API는 react·next·mocks를 가져오지 않는다 | #52-S9 "기능의 api·types·utils와 lib는 …"(모든 `features/*/api`) | 없음 |
| (새 검사) | #182 "공통 components는 features와 mocks를 가져오지 않는다", #182 "기능 폴더에 domain·application·adapters 계층 폴더가 다시 생기지 않는다" | `import()` 호출도 경로를 모아 검사한다. 문자열을 조합해 만든 경로는 잡지 못한다 |

브라우저 저장 업무의 테스트(`workspace.test.ts`, `workspace-migration.test.ts`, `life.test.ts`, `postcard-demo.test.ts`, `question-workflow.test.ts`, `demo-questions.test.ts`)는 이번에 지우지 않고 가져오는 경로만 `mocks/handlers`로 바꿨다. 이 테스트는 이제 데모(목업)의 동작을 확인할 뿐 운영 업무 규칙의 근거가 아니다. 반려동물·일기·질문·엽서·가족·커뮤니티·관리 규칙은 각 화면을 서버 API에 연결하는 이슈(#85, #104, #129 등)에서 백엔드 시나리오 테스트로 확인하고, 그때 해당 목업 테스트를 줄인다.

커뮤니티(#104)는 가입·로그인한 회원의 화면을 글 API에 연결했지만 `life.test.ts`의 커뮤니티 테스트(#12-S8 두 개)는 줄이지 않았다. 데모 세션과 관리자 신고 화면(`AdminScreen`)이 아직 브라우저 데모 글을 쓰기 때문이다. 서버 글의 규칙은 #21의 백엔드 시나리오 테스트가 확인한다. 두 테스트는 데모 진입점 결정(#168) 뒤 데모를 정리할 때 함께 줄인다.
