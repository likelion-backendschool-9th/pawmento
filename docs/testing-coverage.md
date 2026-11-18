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

후속 검증 위치: 좌표 쌓기 API PR의 시나리오 테스트(`#73-S14`, `#73-S6`)에서 실제 DB와 요청으로 아래를 확인한다.

- 누적 한도를 넘긴 요청 뒤에 `walk_points` 개수와 `walks.last_activity_at`이 요청 전과 같다(새 좌표와 활동 시각이 함께 롤백된다).
- 5,000개를 채운 뒤 같은 요청을 다시 보내면 204이고 좌표가 늘지 않는다.
- 한도 직전의 산책에 좌표를 쌓는 요청이 동시에 여러 개 와도 합계가 5,000개를 넘지 않는다.

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

