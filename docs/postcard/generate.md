# 정원 우체통 AI 엽서 생성

관련 이슈: [#103](https://github.com/likelion-backendschool-9th/pawmento/issues/103)

## 한눈에
- 끝나면: 정원 모드 반려동물의 소유자가 다이어리 일기 하나를 골라 동의하고 AI 수채화 엽서 생성을 요청한다. 만드는 중·완성·실패 상태를 보고, 만든 엽서를 목록·상세·이미지로 보고 지운다. 엽서가 있는 반려동물은 지울 수 없다.
- 이번 범위: 서버 API까지. 기억 뽑기(무작위 일기 고르기)와 화면 연결은 다음 프론트엔드 이슈에서, 사진만 있는 일기로 만들기·참조 사진·질문 답변·다시 시도·관리자 판매 연계는 각각 후속 이슈에서 한다([범위 밖](#범위-밖)).
- 결정 필요: 없음
- 맞출 것: 반려동물 삭제를 엽서가 막도록 pet의 `HasPetRecordsPort` 어댑터를 고친다(5단계). PR에서 pet 담당에게 리뷰를 요청한다.
- 선행 조건: 6단계 전에 팀 통장 카드로 Gemini API 결제(유료 등급)를 연결한다
- 규모: 6단계

## 1. 생성 요청 (#103-R1)
소유자가 고른 일기와 외부 전송 동의로 엽서 생성을 요청하면, 요청 시점의 일기 내용을 복사한 엽서가 `PROCESSING` 상태로 저장된다. 응답은 생성이 끝나기를 기다리지 않는다.

#103-S1 정상 [테스트]
- Given 회원 A의 반려동물 콩이(petId 10)가 `GARDEN` 모드이고 일기 7번이 콩이의 것이다
- When  A가 `POST /api/v1/postcards?petId=10`에 `{"diaryId":7,"consent":true}`를 보낸다
- Then  202와 `{"postcardId": 1, "status": "PROCESSING"}`을 받는다
- Then  `GET /api/v1/postcards/1?petId=10`이 `status: "PROCESSING"`, `source: {"kind": "DIARY", "id": 7}`, `imageUrl: null`을 돌려준다

#103-S2 실패: 만들 수 없는 요청 [테스트]
- Given 콩이가 `ACTIVE` 모드다
- When  A가 `{"diaryId":7,"consent":true}`로 요청한다
- Then  409와 `code: "CONFLICT"`를 받는다
- Given 일기 8번이 다른 반려동물의 것이다
- When  A가 `{"diaryId":8,"consent":true}`로 요청한다
- Then  404와 `code: "NOT_FOUND"`를 받는다
- Given 반려동물 20이 회원 B의 것이다
- When  A가 `POST /api/v1/postcards?petId=20`을 보낸다
- Then  403과 `code: "FORBIDDEN"`을 받는다 (pet·diary와 같은 소유 확인)
- When  A가 `consent`를 빼거나 `false`로 보낸다
- Then  400과 `code: "VALIDATION"`을 받는다
- Given 콩이의 일기 9번이 글 없이 사진만 있다
- When  A가 `{"diaryId":9,"consent":true}`로 요청한다
- Then  409와 `message: "글이 있는 일기로 엽서를 만들 수 있어요."`를 받는다
- Then  다섯 경우 모두 엽서 행과 사용 횟수가 늘지 않고, 생성기가 불리지 않는다

#103-S3 실패: 하루 제한 [테스트]
- Given A가 오늘(Asia/Seoul) 엽서 5장을 요청했다
- When  A가 6번째를 요청한다
- Then  409와 `message: "엽서는 하루 5장까지 만들 수 있어요."`를 받고 엽서 행이 늘지 않는다
- Given A가 오늘 5장을 요청하고 그중 완성된 1장을 지웠다
- When  A가 다시 요청한다
- Then  같은 409를 받는다 (지워도 횟수는 돌아오지 않는다)
- Given A가 오늘 4장을 요청했다
- When  A의 요청 2건이 동시에 들어온다
- Then  1건만 202이고 나머지 1건은 409이며, 엽서 행은 5개다
- Given 오늘 전체 생성 횟수(실패·삭제 포함)가 99다
- When  서로 다른 회원 C·D의 요청이 동시에 들어온다
- Then  1건만 202이고 나머지 1건은 409와 `message: "오늘은 엽서를 더 만들 수 없어요. 내일 다시 시도해 주세요."`를 받는다
- Given 설정에서 회원당 제한을 6으로 바꿨다
- When  A가 6번째를 요청한다
- Then  202를 받는다

## 2. 생성 처리 (#103-R2)
서버가 `PROCESSING` 엽서를 꺼내 요청 때 복사해 둔 일기 내용으로 AI 이미지 서비스에서 그림을 받고, 이미지를 저장한 뒤 `COMPLETED`로 바꾼다. 실패하면 `FAILED`로 바꾸고 회원당 횟수를 돌려준다.

#103-S4 정상 [테스트]
- Given 엽서 1이 `PROCESSING`이고, 요청 뒤 원본 일기 7번이 삭제됐다
- Given 가짜 생성기가 PNG 한 장을 돌려준다
- When  생성 작업이 한 번 돈다
- Then  생성기가 요청 때의 일기 내용을 받는다
- Then  `GET /api/v1/postcards/1?petId=10`이 `status: "COMPLETED"`, `title`, 일기 내용 앞 200자인 `text`, `imageUrl: "/api/v1/postcards/1/image?petId=10"`, `completedAt`을 돌려준다
- Then  `GET /api/v1/postcards/1/image?petId=10`이 200과 `image/png`, 생성기가 준 바이트를 돌려준다

#103-S5 실패 [테스트]
- Given 엽서 1이 A의 오늘 5번째 요청이고, 가짜 생성기가 시간 초과 예외를 던진다
- When  생성 작업이 한 번 돈다
- Then  엽서 1이 `status: "FAILED"`, `errorMessage: "엽서를 만들지 못했어요. 다시 요청해 주세요."`, `imageUrl: null`이 된다
- Then  A의 오늘 회원당 횟수는 5에서 4로 줄고, 오늘 전체 생성 횟수는 실패 전과 같다
- When  A가 같은 일기로 다시 요청한다
- Then  202를 받고, A의 회원당 횟수는 5, 전체 생성 횟수는 실패 전보다 1 많다
- Then  응답과 로그 메시지에 API 키·외부 응답 본문이 없고, 저장 폴더에 엽서 1의 이미지 파일이 없다

#103-S6 만드는 중 삭제 [테스트]
- Given 엽서 1이 `PROCESSING`이고, 가짜 생성기가 그림을 돌려주기 전에 A가 `DELETE /api/v1/postcards/1?petId=10`을 불러 204를 받았다
- When  생성 작업이 그림을 받아 끝난다
- Then  엽서 1은 목록·상세에 다시 나타나지 않는다
- Then  저장 폴더에 엽서 1의 이미지 파일이 남지 않는다
- Then  A의 회원당 횟수와 전체 생성 횟수는 돌아오지 않는다

#103-S7 실제 AI 서비스 [직접]
- Given 개발 서버에 유료 등급 Gemini API 키를 넣었다
- When  "바닷가에서 노을을 보며 산책했다" 일기로 엽서를 요청한다
- Then  1분 안에 `COMPLETED`가 되고, 이미지가 1K 크기의 수채화풍 바닷가 장면이다

## 3. 목록·상세·이미지·삭제 (#103-R3)
소유자는 반려동물별 엽서를 최신순으로 나눠 보고, 열어 보고, 지운다. 다른 반려동물의 엽서는 없는 것처럼 다룬다.

#103-S8 정상 [테스트]
- Given 콩이의 엽서 25장(완성·실패·만드는 중 섞임)이 있다
- When  A가 `GET /api/v1/postcards?petId=10&size=20`을 부른다
- Then  최근 요청 순으로 20장과 각 `status`, `nextCursor`를 받는다
- When  A가 `cursor=<nextCursor>`로 다시 부른다
- Then  나머지 5장과 `nextCursor: null`을 받는다
- When  A가 `PROCESSING` 또는 `FAILED` 엽서의 이미지를 부른다
- Then  404를 받는다
- When  A가 `COMPLETED` 엽서 1을 지운다
- Then  204를 받고, 목록·상세·이미지에서 엽서 1이 사라지고, 저장 폴더의 이미지 파일도 지워진다

#103-S9 권한 [테스트]
- Given 엽서 1이 회원 A의 콩이(petId 10) 엽서다
- When  회원 B가 `petId=10`으로 엽서 1의 상세·이미지·삭제를 부른다
- Then  셋 다 403을 받는다
- When  B가 자기 반려동물 `petId=20`으로 엽서 1을 부른다
- Then  셋 다 404를 받는다
- Then  엽서 1은 그대로다
- When  쿠키 없이 같은 경로와 목록·생성 요청을 부른다
- Then  모두 401을 받는다

## 4. 반려동물 삭제와 엽서 (#103-R4)
엽서가 하나라도 남아 있는 반려동물은 지울 수 없다.

#103-S10 삭제 막기 [테스트]
- Given 콩이의 일기는 모두 지워졌고 엽서 1장이 남아 있다
- When  A가 콩이를 삭제한다
- Then  기존 반려동물 삭제와 같은 409를 받고 콩이와 엽서가 그대로다
- When  A가 엽서를 지우고 다시 콩이를 삭제한다
- Then  삭제된다

#103-S11 삭제와 생성이 겹칠 때 [테스트]
- Given 콩이에게 일기 7번만 있고 엽서는 없다
- Given A의 생성 요청이 일기 7번 내용을 복사한 뒤, 엽서를 저장하기 직전에 멈춰 있다
- Given 별도 트랜잭션에서 일기 7번을 지우고 커밋했다 (이제 콩이는 일기·엽서가 없어 삭제 확인을 통과한다)
- When  생성 요청이 엽서를 저장하고 먼저 커밋한 뒤, 콩이 삭제가 진행된다
- Then  콩이 삭제는 외래 키 `fk_postcard_pet` 위반으로 S10과 같은 409를 받고, 콩이와 엽서가 그대로다
- When  같은 준비에서 콩이 삭제가 먼저 커밋된 뒤, 멈춰 있던 생성 요청이 엽서를 저장한다
- Then  생성 요청은 외래 키 위반으로 404와 `code: "NOT_FOUND"`를 받는다
- Then  엽서 행과 회원당·전체 사용 횟수가 늘지 않는다
- 테스트는 생성 요청의 저장 직전에 대기 지점을 두고, 두 트랜잭션의 커밋 순서를 순서별로 고정한다

## API

경로는 `/api/v1` 접두어를 뺐다. [공통 API 경로 규칙](../backend-architecture.md#api-경로)에 따라 모든 경로가 필수 쿼리 `petId`를 받고, 먼저 반려동물 소유를 확인한다.

`diaryId`는 다이어리 응답의 `diaryId`, `/diaries/{diaryId}`와 같은 값이다([이름 정리](../diary/naming-cleanup.md)). 응답의 `source`는 원본이 일기인지 질문 답변인지 `kind`로 구분한다. 프론트 엽서 모델의 `sourceEntries[].kind`와 같은 이름·값을 쓴다. 이번에는 `DIARY`만 만들고, 질문 답변(`QUESTION_ANSWER`)은 후속 이슈에서 요청 필드 `questionAnswerId`와 함께 더한다.

| 요청 | 입력 | 응답 |
| --- | --- | --- |
| `POST /postcards?petId=` | `diaryId`, `consent`(true 필수) | 202: `postcardId`, `status` |
| `GET /postcards?petId=` | `size`(기본 20, 1~50), `cursor` | 200: `items`(요약), `nextCursor` |
| `GET /postcards/{postcardId}?petId=` | 없음 | 200: 상세 |
| `GET /postcards/{postcardId}/image?petId=` | 없음 | 200 `image/png`. `COMPLETED`가 아니면 404 |
| `DELETE /postcards/{postcardId}?petId=` | 없음 | 204. 상태와 상관없이 지운다 |

상세와 목록 요약의 필드는 같다. 상태에 따라 값이 비는 필드가 있다.

| 필드 | `PROCESSING` | `COMPLETED` | `FAILED` |
| --- | --- | --- | --- |
| `postcardId`, `petId`, `source`(`kind`, `id`), `status`, `title`, `text`, `createdAt` | 있음 | 있음 | 있음 |
| `imageUrl` | `null` | 이미지 경로 | `null` |
| `completedAt` | `null` | 완성 시각 | `null` |
| `errorMessage` | `null` | `null` | 사용자용 안내 |

프론트엔드는 `PROCESSING`인 동안 상세를 다시 불러 상태를 확인한다. `imageUrl`이 `null`이면 이미지를 부르지 않는다.

## 데이터 구조

| 테이블 | 컬럼 | 의미 |
| --- | --- | --- |
| `postcard` | `id`, `pet_id`(FK `fk_postcard_pet`, `ON DELETE RESTRICT`), `owner_member_id` | 엽서와 소유자 |
| | `source_kind`, `source_id` | 원본 종류(`DIARY`, 후속 `QUESTION_ANSWER`)와 ID. FK를 걸지 않아 원본을 지워도 남는다 |
| | `source_content` | 요청 때 복사한 일기 내용(3,000자 이하). 그림 생성 입력 |
| | `title`, `text` | 제목, 일기 내용 앞 200자 |
| | `status`, `error_message`, `image_key` | 상태, 사용자용 실패 안내, 저장한 이미지 파일 이름 |
| | `requested_on` | 요청한 날(Asia/Seoul). 실패 때 그날 횟수를 돌려줄 때 쓴다 |
| | `created_at`, `processing_started_at`, `completed_at` | 요청·작업 시작·완성 시각 |
| `postcard_daily_usage` | `usage_date`, `member_id`, `count` | 회원당 하루 횟수. 실패하면 1 줄인다 |
| `postcard_daily_total` | `usage_date`, `count` | 전체 하루 생성 횟수. 줄이지 않는다 |

- 요청 하나는 한 트랜잭션에서 `postcard_daily_total` → `postcard_daily_usage` 순서로 그날 행을 잠그고(없으면 만들고), 두 제한을 확인한 뒤 횟수를 늘리고 엽서를 저장한다. 순서를 고정해 교착을 막는다.
- 횟수는 엽서 행이 아니라 이 두 테이블로 센다. 엽서를 지워도 횟수는 그대로다.
- 생성 요청이 어느 단계에서 실패하든(소유·모드 확인, 제한, 엽서 저장의 외래 키 위반) 같은 트랜잭션이라 늘린 횟수도 함께 되돌아간다.

## 범위 밖
- 기억 뽑기, 프론트엔드 HTTP 어댑터 연결, 생성 동의 안내 화면, 양면 다운로드: 다음 프론트엔드 이슈
- 일기에 첨부한 사진으로 그리는 것(사진만 있는 일기 포함): 후속 이슈. 첨부 기능(PR #107)은 이미 있어 선행 기능을 기다리지 않는다. 사진을 Google에 보내는 동의 문구, 다이어리 포트로 첨부 사진 읽기, Gemini에 사진 넣기를 더한다
- 반려동물 프로필 사진으로 그림을 닮게 하는 것: 프로필 사진·media 용도 정책이 정해진 뒤 후속 이슈
- 질문 답변(#31)을 고를 기록에 넣는 것: 후속 이슈
- 다시 시도와 실패 안내: 사용자 흐름 기준으로 후속 이슈에서 설계한다(서버 자동 재시도, 실패 안내 문구, 실패분 제한 처리). 이번에는 실패한 엽서의 회원당 횟수를 돌려줘 새로 요청할 수 있게만 한다
- 엽서 판매·결제, 관리자 판매 관리: 관리자 담당 이슈. 관리자 기능이 엽서 데이터를 어떻게 읽을지도 그 후속 이슈에서 정한다
- 실제 우편·메일 발송, 가족과 엽서 공유
- AI로 엽서 문구 생성: 이번에는 일기 내용을 문구로 쓴다
- 보관함 즐겨찾기(데모의 `saved` 토글)

## 작업 순서
1. `postcard` 도메인, 저장소, 생성 요청 API를 가장 얇게 연결한다 — S1
   - 파일: `postcard/domain/Postcard.java`, `PostcardStatus.java`, `postcard/application/service/RequestPostcardService.java`, `postcard/adapter/out/persistence/*`, `postcard/adapter/in/web/PostcardController.java`, `RequestPostcardRequest.java`(`consent` 검증), 일기 조회 출력 포트와 diary 어댑터(`GetDiaryEntryUseCase` 사용), `HexagonalArchitectureTest` 도메인 목록에 `postcard` 추가, `docs/README.md` 도메인 목록
   - 확인: `./gradlew test --tests 'com.pawmento.postcard.*' --tests '*HexagonalArchitectureTest'` (원격 remote-db)
2. 모드·소유 확인과 하루 제한 — S2, S3
   - 파일: `RequestPostcardService.java`, 반려동물 조회 출력 포트(`GetPetUseCase` 사용), `PostcardUsagePort.java`와 잠금 저장소 어댑터, `PostcardLimitProperties.java`(`pawmento.postcard.daily-limit-per-member`, `daily-limit-total`), `application.yml`
   - 확인: 같은 명령
3. 생성 작업, 이미지 저장, 만드는 중 삭제 — S4, S5, S6
   - 파일: `ProcessPostcardService.java`, `postcard/adapter/in/scheduling/PostcardWorkScheduler.java`, `GeneratePostcardImagePort.java`, 가짜 생성기, `postcard/adapter/out/storage/VolumePostcardImageAdapter.java`, `PostcardStorageProperties.java`(`pawmento.postcard.image-root`)
   - 확인: 같은 명령
4. 목록·상세·이미지·삭제와 권한 — S8, S9
   - 파일: `ListPostcardsService.java`, `GetPostcardService.java`, `GetPostcardImageService.java`, `DeletePostcardService.java`, `PostcardController.java`
   - 확인: 같은 명령
5. 반려동물 삭제를 엽서가 막게 연결 — S10, S11
   - 파일: `postcard/application/port/in/HasPostcardsUseCase.java`, pet의 `adapter/out/records/DiaryPetRecordsAdapter.java`(일기 또는 엽서가 있으면 true, 이름 변경 검토), pet의 `PetPersistenceAdapter.deleteById`(외래 키 `fk_postcard_pet` 위반도 `PetHasRecordsException`으로 바꿈), postcard 저장 어댑터(`fk_postcard_pet` 위반을 반려동물 없음 404로 바꿈)
   - 확인: `./gradlew test --tests 'com.pawmento.postcard.*' --tests 'com.pawmento.pet.*'`
6. Gemini 이미지 어댑터와 설정 — S7
   - 파일: `postcard/adapter/out/ai/GeminiPostcardImageAdapter.java`, `GeminiImageProperties.java`, `application.yml`, `.env.prod.example`(`GEMINI_API_KEY`), `docs/deploy.md`(키 설정)
   - 확인: 개발 서버에서 S7

## 정한 것
- 이슈 #103의 첫 범위에서 기억 뽑기, 참조 사진, 질문 답변, 관리자 판매 연계를 뺐다. 이번에는 고른 일기의 글만으로 그림을 만든다. 질문 답변과 참조 사진은 선행 기능(#31, 프로필 사진 정책)이 없고, 기억 뽑기는 화면 기능이다.
- AI 이미지는 외부 API인 Google Gemini 3.1 Flash Image로 만든다. 화풍 지정에 강하고, 후속 참조 사진 이슈에서 같은 서비스로 사진을 넣을 수 있다. 서버에서 오픈 모델을 직접 돌리는 방식은 여러 팀이 같이 쓰는 개발 서버에 부담이 커서 쓰지 않고, 미리 그린 장면 그림을 고르는 방식은 매번 새 그림이 아니어서 쓰지 않는다.
- AI 이미지 서비스 비용은 팀 통장에서 내고, 결제·키 발급은 이슈 담당자가 한다. 결제 계정에 예산 알림을 건다.
- Gemini는 유료 등급 키만 쓴다. 무료 등급은 보낸 내용을 서비스 개선에 쓸 수 있어, 사용자의 일기를 그 조건으로 보내지 않는다.
- 일기 내용이 외부 서비스로 보내지므로 생성 요청마다 `consent: true`를 받는다. 안내 문구와 화면은 프론트엔드 이슈에서 만든다.
- 도메인 이름은 `postcard`로 한다. 프론트 데모의 `Postcard` 타입과 같고, `card`는 UI 카드·결제 카드와 헷갈린다.
- 하루 제한은 회원당 5장, 팀 전체 100장이다. 회원당 5장은 프론트 데모 값을 따랐고, 전체 상한은 키 유출이나 버그로 요청이 몰릴 때 비용을 막는다. 결제 예산 알림은 요청을 막지 않으므로 서버에서도 막는다. 두 값은 `application.yml` 설정으로 두어 결제 상황에 따라 코드 변경 없이 바꾼다.
- 횟수는 엽서 행이 아니라 따로 둔 사용 횟수로 센다. 엽서를 지워도 횟수는 돌아오지 않는다. 동시 요청은 그날 횟수 행을 잠가 한 건씩 처리한다.
- 실패한 엽서는 회원당 횟수를 돌려준다. 서버나 Gemini 탓인 실패로 사용자가 제한을 잃지 않게 한다. 전체 횟수는 돌려주지 않는다. Gemini를 실제로 불렀고, 시간 초과는 Gemini 쪽에서 이미 그림을 만들어 과금됐을 수 있다.
- 다시 시도 API는 이번에 만들지 않는다. 사용자 흐름(자동 재시도, 실패 안내)을 보고 후속 이슈에서 설계한다.
- 기억 뽑기는 프론트엔드가 다이어리 목록 API(`GET /diaries?petId=`, PR #100)로 한다. `size=1`로 `totalElements`를 받은 뒤 0부터 시작하는 무작위 `page` 하나를 불러 보여 주고, 고른 일기의 ID를 `diaryId`로 보내 생성을 요청한다. `GET /postcards/draw`처럼 동사를 경로에 넣지 않고, 미리 보며 다시 뽑아도 하루 제한과 비용을 쓰지 않는다. 서버는 받은 `diaryId`가 그 아이의 일기인지와 모드를 다시 확인한다.
- 원본 참조는 일기와 질문 답변을 구분한다. 요청은 `diaryId`, 응답은 `source: {kind, id}`, 저장은 `source_kind`·`source_id`다. 프론트 모델(`sourceEntries: [{kind, id}]`)과 이름을 맞춰 붙일 때 변환을 줄인다. 서버 엽서는 일기 하나로 만들므로 배열이 아닌 객체 하나다. 일기·질문 답변 분리와 `diaryId` 통일(#115)을 따른다. 이번에는 `DIARY`만 받는다.
- 글 없이 사진·음성만 있는 일기(#32 첨부, PR #107)로는 엽서를 만들지 않는다. 이번에는 글만으로 그림을 만들기 때문이다. 프론트엔드는 뽑은 일기에 글이 없으면 다시 뽑는다. 첨부 사진으로 그리는 후속 이슈에서 이 제한을 푼다. 그때 요청·응답 형식은 바꾸지 않고, `source_content`를 비울 수 있게 하고 S2의 사진만 있는 일기 시나리오를 `(고침 …)`으로 바꾼다.
- 요청 때 일기 내용을 엽서에 복사한다. 생성 전에 일기가 고쳐지거나 지워져도 요청한 내용으로 만든다. 일기와 엽서의 생명주기는 묶지 않는다.
- 만드는 중인 엽서도 지울 수 있다. 작업이 끝났을 때 엽서가 없으면 받은 그림을 저장하지 않고, 이미 저장했으면 지운다. 횟수는 돌려주지 않는다.
- 엽서가 남아 있는 반려동물은 지울 수 없다. 기존 "기록이 있으면 삭제를 막는다"(`HasPetRecordsPort`, 409)에 엽서를 더한다. 일기를 모두 지워도 엽서가 남을 수 있어서다.
- 삭제 확인과 생성 요청이 겹치면 DB 외래 키가 마지막으로 막는다. 엽서가 먼저 저장되면 반려동물 삭제가 409, 반려동물이 먼저 지워지면 생성 요청이 404다. 다이어리가 같은 경합을 `fk_diary_entries_pet`으로 처리하는 방식을 따른다.
- 남의 반려동물은 pet·diary와 같이 403, 다른 반려동물에 속한 엽서는 404로 응답한다.

## 가정
- 접근은 반려동물 소유자만 허용한다. 다이어리와 같이 가족 역할은 아직 열지 않는다.
- `ACTIVE` 모드와 하루 제한은 모드를 바꾸거나 다음 날이 되면 해결되므로 409로 응답한다. 전체 상한도 새 503 공통 예외를 만들지 않고 409로 둔다([예외 처리 문서](../backend-exceptions.md)).
- 생성은 비동기다. 작업은 media처럼 스케줄러가 `PROCESSING` 엽서를 하나씩 꺼내 처리하며, 서버는 한 대로 본다.
- 외부 호출은 60초가 지나면 실패로 처리하고, 이번에는 자동 재시도를 하지 않는다.
- `PROCESSING`에서 10분 넘게 멈춘 엽서는 다음 작업 때 `FAILED`로 바꾸고 회원당 횟수를 돌려준다.
- 이미지는 1K(1024×1024) PNG로 받는다. 비용이 해상도에 따라 달라서 크기를 고정한다.
- 이미지는 설정 `pawmento.postcard.image-root`(기본은 media 볼륨 아래 `postcards/`)에 저장한다. media 정리 작업은 `tmp/` 아래만 지워 겹치지 않는다.
- 엽서 제목은 "`<이름>`와 함께한 작은 순간"으로 고정하고, 문구는 일기 내용 앞 200자다.
- 목록 커서는 커뮤니티 글 목록(#21)과 같은 방식(마지막 엽서 ID)으로 한다.

## 참고
- 프론트 데모의 엽서 흐름: `frontend/src/life/application/useCases.ts`의 `requestPostcard`, 상태 `PROCESSING | COMPLETED | FAILED`, 하루 5장, 생성 동의 확인, 실패 후 같은 엽서로 다시 시도(이번 이슈에서는 다시 시도를 뺐다) ([앱 데모](../app-demo/full-demo.md) #12-R8, #12-S9)
- 회의 결정: 우체통은 정원 기능이고, 이번 범위는 AI 이미지 생성까지([1차 회의록](../meetings/2026-11-16.md))
- 다이어리는 일기와 질문 답변을 나눈다. 질문 답변 ID를 일기 ID로 쓰지 않는다([다이어리](../diary/README.md#도메인-경계)). 엽서도 원본 종류를 `source_kind`로 나눠 두 ID가 섞이지 않게 한다
- 남의 반려동물 접근은 `Pet.requireOwnedBy`가 `PetOwnershipViolationException`(403)을 던진다. 다이어리도 `GetPetUseCase`로 같은 검사를 한다.
- 반려동물 삭제 차단은 pet의 `HasPetRecordsPort`를 diary 어댑터(`DiaryPetRecordsAdapter`)가 구현한다. 삭제 서비스는 이 포트로 확인한 뒤 지우고, 그사이 생긴 기록은 `PetPersistenceAdapter.deleteById`가 외래 키 이름(`fk_diary_entries_pet`)과 SQLSTATE `23001`·`23503`으로 골라 409로 바꾼다. 다른 외래 키 위반은 그대로 던진다.
- media 용도는 지금 `DIARY_ATTACHMENT`, `QUESTION_ANSWER_ATTACHMENT` 두 가지이며, AI 생성물은 별도 정책을 정한 뒤 확장한다고 적혀 있다([미디어](../media/README.md))
- Gemini 이미지 모델은 장당 고정 가격이 아니라 출력 토큰으로 과금하고, 해상도가 클수록 비싸다. 1K 기준 약 $0.067(2026-11 기준, [Gemini API 가격](https://ai.google.dev/gemini-api/docs/pricing))
- 다른 도메인은 애플리케이션 포트로만 쓰고 JPA 엔티티를 직접 참조하지 않는다([백엔드 구조](../backend-architecture.md))
