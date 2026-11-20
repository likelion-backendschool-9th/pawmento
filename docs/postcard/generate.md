# 정원 우체통 AI 엽서 생성

관련 이슈: [#103](https://github.com/likelion-backendschool-9th/pawmento/issues/103)

## 한눈에
- 끝나면: 베타 기능으로, 정원 모드 반려동물의 소유자가 다이어리 일기 하나를 골라 동의하고 AI 수채화 엽서 생성을 요청한다. 일기의 글과 첫 사진, 반려동물의 종·품종으로 그린다. 재료가 부족하면 설명 한 줄을 더해 만든다. 만드는 중·완성·실패 상태를 보고, 만든 엽서를 목록·상세·이미지로 보고 지운다. 엽서가 있는 반려동물은 지울 수 없다.
- 이번 범위: 서버 API까지. 기억 뽑기(무작위 일기 고르기)와 화면 연결은 다음 프론트엔드 이슈에서, 질문 답변·산책 원본, 프로필 사진·사진 추가 업로드, 다시 시도, 관리자 판매 연계는 각각 후속 이슈에서 한다. 요청 형식은 원본 종류를 받게 해 두어 후속 원본이 계약을 바꾸지 않는다([범위 밖](#범위-밖)).
- 결정 필요: 없음
- 맞출 것: 반려동물 삭제를 엽서가 막도록 pet의 `HasPetRecordsPort` 어댑터를 고친다(6단계). PR에서 pet 담당에게 리뷰를 요청한다.
- 선행 조건: 7단계 전에 Gemini API 결제(유료 등급)를 연결한다
- 규모: 7단계

## 1. 생성 요청 (#103-R1)
소유자가 고른 원본(이번에는 일기), 선택한 추가 설명, 외부 전송 동의로 엽서 생성을 요청하면, 요청 시점의 일기 글과 첫 사진, 추가 설명, 반려동물의 종·품종을 복사한 엽서가 `PROCESSING` 상태로 저장된다. 응답은 생성이 끝나기를 기다리지 않는다.

#103-S1 정상 [테스트]
- Given 회원 A의 반려동물 콩이(petId 10)가 `GARDEN` 모드이고 일기 7번이 콩이의 것이다
- When  A가 `POST /api/v1/postcards?petId=10`에 `{"source":{"kind":"DIARY","id":7},"consent":true}`를 보낸다
- Then  202와 `{"postcardId": 1, "status": "PROCESSING"}`을 받는다
- Then  `GET /api/v1/postcards/1?petId=10`이 `status: "PROCESSING"`, `source: {"kind": "DIARY", "id": 7}`, `imageUrl: null`을 돌려준다

#103-S2 실패: 만들 수 없는 요청 [테스트]
- Given 콩이가 `ACTIVE` 모드다
- When  A가 일기 7번으로 요청한다
- Then  409와 `code: "CONFLICT"`를 받는다
- Given 일기 8번이 다른 반려동물의 것이다
- When  A가 `source: {"kind":"DIARY","id":8}`로 요청한다
- Then  404와 `code: "NOT_FOUND"`를 받는다
- Given 반려동물 20이 회원 B의 것이다
- When  A가 `POST /api/v1/postcards?petId=20`을 보낸다
- Then  403과 `code: "FORBIDDEN"`을 받는다 (pet·diary와 같은 소유 확인)
- When  A가 `consent`를 빼거나 `false`로 보낸다. 또는 `source`를 빼거나, 이번에 받지 않는 `kind`(`QUESTION_ANSWER`, `WALK`)를 보내거나, `note`를 200자 넘게 보낸다
- Then  400과 `code: "VALIDATION"`을 받는다
- Given 콩이의 일기 9번이 글 없이 음성만 있다. 일기 10번은 글 없이 열 수 없는 사진(`available: false`)만 있다
- When  A가 `note` 없이 일기 9번과 10번으로 각각 요청한다
- Then  둘 다 409와 `message: "그림으로 만들 내용이 부족해요. 다른 기록을 고르거나 설명을 더해 주세요."`를 받는다
- Then  모든 경우에 엽서 행, 사용 횟수, 복사한 사진 파일이 늘지 않고, 생성기가 불리지 않는다

#103-S3 실패: 하루 제한 [테스트]
- Given A가 오늘(Asia/Seoul) 엽서 3장을 요청했다
- When  A가 4번째를 요청한다
- Then  409와 `message: "엽서는 베타 기간에 하루 3장까지 만들 수 있어요."`를 받고 엽서 행이 늘지 않는다
- Given A가 오늘 3장을 요청하고 그중 완성된 1장을 지웠다
- When  A가 다시 요청한다
- Then  같은 409를 받는다 (지워도 횟수는 돌아오지 않는다)
- Given A가 오늘 2장을 요청했다
- When  A의 요청 2건이 동시에 들어온다
- Then  1건만 202이고 나머지 1건은 409이며, 엽서 행은 3개다
- Given 오늘 전체 생성 횟수(실패·삭제 포함)가 99다
- When  서로 다른 회원 C·D의 요청이 동시에 들어온다
- Then  1건만 202이고 나머지 1건은 409와 `message: "오늘은 엽서를 더 만들 수 없어요. 내일 다시 시도해 주세요."`를 받는다
- Given 설정에서 회원당 제한을 4로 바꿨다
- When  A가 4번째를 요청한다
- Then  202를 받는다

#103-S12 사진이 있는 일기 [테스트]
- Given 콩이의 일기 11번에 글과 사진 두 장(첨부 순서 21, 22)이 있고, 일기 12번은 글 없이 사진 23만 있다
- When  A가 일기 11번과 12번으로 각각 엽서를 요청한다
- Then  둘 다 202를 받고, 엽서마다 첫 사진(21, 23)의 썸네일이 복사된다
- When  두 일기를 지운 뒤 생성 작업이 돈다
- Then  가짜 생성기가 11번은 글과 사진 21을, 12번은 사진 23만 받는다
- Then  두 엽서가 `COMPLETED`가 되고, 12번 엽서의 `text`는 `""`다. 복사한 사진 파일은 남지 않는다

#103-S15 추가 설명으로 보완 [테스트]
- Given 콩이는 종 `DOG`, 품종 `말티즈`다. 일기 9번은 글 없이 음성만 있고, 일기 7번은 글만 있다
- When  A가 일기 9번에 `note: "공원 벤치 옆에서 낙엽을 밟으며 놀았다"`를 붙여 요청한다
- Then  202를 받고, 상세의 `note`가 보낸 값이며 `text`도 그 설명이다
- When  A가 일기 7번에 `note`를 붙여 요청한다
- Then  202를 받고, `text`는 일기 글 앞 200자다
- When  생성 작업이 돈다
- Then  가짜 생성기가 9번 요청은 설명과 `DOG`·`말티즈`를, 7번 요청은 일기 글과 설명과 `DOG`·`말티즈`를 받는다
- When  요청 뒤 콩이의 품종을 `푸들`로 고친 다음 생성 작업이 돈다
- Then  생성기는 요청 때의 `말티즈`를 받는다

#103-S16 베타 안내와 남은 횟수 [테스트]
- Given A가 오늘 엽서 1장을 요청했고, 그중 실패한 것은 없다
- When  A가 `GET /api/v1/postcards/usage?petId=10`을 부른다
- Then  200과 `{"beta": true, "dailyLimit": 3, "usedToday": 1, "remainingToday": 2}`를 받는다
- When  설정에서 회원당 제한을 4로 바꾸고 다시 부른다
- Then  `dailyLimit: 4`, `remainingToday: 3`을 받는다

## 2. 생성 처리 (#103-R2)
서버가 아직 시작하지 않은 `PROCESSING` 엽서를 하나씩 선점해 요청 때 복사해 둔 글과 사진으로 AI 이미지 서비스에서 그림을 받고, 이미지를 저장한 뒤 `COMPLETED`로 바꾼다. 실패하면 `FAILED`로 바꾸고 회원당 횟수를 돌려준다. 외부 호출을 시작한 엽서는 서버가 재시작돼도 다시 호출하지 않는다.

#103-S4 정상 [테스트]
- Given 엽서 1이 `PROCESSING`이고, 요청 뒤 원본 일기 7번이 삭제됐다
- Given 가짜 생성기가 PNG 한 장을 돌려준다
- When  생성 작업이 한 번 돈다
- Then  생성기가 요청 때의 일기 내용과 반려동물 종·품종을 받는다
- Then  `GET /api/v1/postcards/1?petId=10`이 `status: "COMPLETED"`, `title`, 일기 내용 앞 200자인 `text`, `imageUrl: "/api/v1/postcards/1/image?petId=10"`, `completedAt`을 돌려준다
- Then  `GET /api/v1/postcards/1/image?petId=10`이 200과 `image/png`, 생성기가 준 바이트를 돌려준다

#103-S5 실패 [테스트]
- Given 엽서 1이 A의 오늘 3번째 요청이고, 가짜 생성기가 시간 초과 예외를 던진다
- When  생성 작업이 한 번 돈다
- Then  엽서 1이 `status: "FAILED"`, `errorMessage: "엽서를 만들지 못했어요. 다시 요청해 주세요."`, `imageUrl: null`이 된다
- Then  A의 오늘 회원당 횟수는 3에서 2로 줄고, 오늘 전체 생성 횟수는 실패 전과 같다
- When  A가 같은 일기로 다시 요청한다
- Then  202를 받고, A의 회원당 횟수는 3, 전체 생성 횟수는 실패 전보다 1 많다
- Then  응답과 로그 메시지에 API 키·외부 응답 본문이 없고, 저장 폴더에 엽서 1의 이미지 파일이 없다

#103-S6 만드는 중 삭제 [테스트]
- Given 엽서 1이 `PROCESSING`이고, 가짜 생성기가 그림을 돌려주기 전에 A가 `DELETE /api/v1/postcards/1?petId=10`을 불러 204를 받았다
- When  생성 작업이 그림을 받아 끝난다
- Then  엽서 1은 목록·상세에 다시 나타나지 않는다
- Then  저장 폴더에 엽서 1의 이미지 파일이 남지 않는다
- Then  A의 회원당 횟수와 전체 생성 횟수는 돌아오지 않는다

#103-S13 외부 호출 중 서버 종료 [테스트]
- Given 엽서 1이 선점돼 `processing_started_at`이 기록됐고, 가짜 생성기가 요청을 받은 직후 작업이 중단됐다(서버 종료를 흉내 낸다). 엽서 2는 아직 선점되지 않았다
- When  재시작한 것처럼 새 작업 실행기로 생성 작업이 돈다
- Then  생성기는 엽서 2로만 불리고, 엽서 1로 다시 불리지 않는다
- When  엽서 1의 시작 시각에서 10분이 지난 뒤 만료 처리가 두 번 겹쳐 돈다
- Then  엽서 1이 `FAILED`가 되고, A의 회원당 횟수는 한 번만 줄어든다
- When  그 뒤 엽서 1의 늦은 결과가 완성 반영을 시도한다
- Then  엽서 1은 `FAILED` 그대로이고, 늦게 받은 이미지 파일은 지워진다

#103-S7 실제 AI 서비스 [직접]
- Given 개발 서버에 유료 등급 Gemini API 키를 넣었다
- When  "바닷가에서 노을을 보며 산책했다" 글만 있는 일기, 같은 글과 반려동물 사진이 있는 일기, 사진만 있는 일기, 음성만 있는 일기에 설명 한 줄을 더한 요청으로 엽서를 하나씩 만든다
- Then  넷 다 1분 안에 `COMPLETED`가 되고, 이미지가 1K 가로(3:2) 수채화풍이다. 사진이 있으면 털색·무늬가 사진 속 아이와 닮았고, 사진이 없으면 프로필의 종·품종으로 그렸다
- Then  같은 네 요청을 설정 모델 `gemini-nano-banana-2.1`과 `gemini-3.1-flash-image`로 각각 만들어 품질, 응답 시간, 결제 콘솔의 실제 금액을 비교하고 기본 모델을 확정한다

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

## 5. 이미지 파일 정리 (#103-R5)
엽서 DB와 맞지 않는 이미지·복사 사진 파일은 정리 작업이 지운다. 생성 중단이나 파일 삭제 실패로 정상 경로가 파일을 지우지 못해도 결국 남지 않는다. AI 생성 재시도와는 별개다.

#103-S14 남은 파일 정리 [테스트]
- Given 저장 폴더에 아래 파일이 있다
  - 엽서 1: `COMPLETED`이고 `image_key`가 가리키는 이미지 (정상)
  - 엽서 2: 이미지를 저장한 직후 `COMPLETED` 반영 전에 작업이 중단돼, 엽서는 만료로 `FAILED`이고 이미지만 남았다
  - 엽서 3: 엽서를 지워 204를 받았지만 파일 삭제가 실패해 이미지가 남았다
  - 엽서 4: 생성 요청 트랜잭션이 사진을 복사한 뒤 롤백돼, 엽서 행 없이 복사 사진만 남았다
  - 엽서 5: 방금 저장해 유예 시간(1시간)이 지나지 않은 이미지
- When  정리 작업이 돈다
- Then  엽서 2·3·4의 파일이 지워지고, 엽서 1·5의 파일은 그대로다
- Then  정리 작업은 `pawmento.postcard.image-root` 밖의 파일을 건드리지 않는다

## API

경로는 `/api/v1` 접두어를 뺐다. [공통 API 경로 규칙](../backend-architecture.md#api-경로)에 따라 모든 경로가 필수 쿼리 `petId`를 받고, 먼저 반려동물 소유를 확인한다.

요청과 응답의 `source`는 `{kind, id}`로 같은 모양이다. `kind`는 원본 종류이고 프론트 엽서 모델의 `sourceEntries[].kind`와 같은 이름·값을 쓴다. 이번에는 `DIARY`만 받고, `id`는 다이어리 응답의 `diaryId`, `/diaries/{diaryId}`와 같은 값이다([이름 정리](../diary/naming-cleanup.md)). 질문 답변(`QUESTION_ANSWER`)과 산책(`WALK`)은 후속 이슈에서 `kind` 값만 더한다.

`note`는 사용자가 더하는 장면 설명이다(선택, 200자 이하). 원본에 글도 쓸 수 있는 사진도 없으면 필요하다.

| 요청 | 입력 | 응답 |
| --- | --- | --- |
| `POST /postcards?petId=` | `source`(`kind`, `id`), `note`(선택), `consent`(true 필수) | 202: `postcardId`, `status` |
| `GET /postcards/usage?petId=` | 없음 | 200: `beta`, `dailyLimit`, `usedToday`, `remainingToday` |
| `GET /postcards?petId=` | `size`(기본 20, 1~50), `cursor` | 200: `items`(요약), `nextCursor` |
| `GET /postcards/{postcardId}?petId=` | 없음 | 200: 상세 |
| `GET /postcards/{postcardId}/image?petId=` | 없음 | 200 `image/png`. `COMPLETED`가 아니면 404 |
| `DELETE /postcards/{postcardId}?petId=` | 없음 | 204. 상태와 상관없이 지운다 |

상세와 목록 요약의 필드는 같다. 상태에 따라 값이 비는 필드가 있다.

| 필드 | `PROCESSING` | `COMPLETED` | `FAILED` |
| --- | --- | --- | --- |
| `postcardId`, `petId`, `source`(`kind`, `id`), `status`, `title`, `text`, `createdAt` | 있음 | 있음 | 있음 |
| `note` | 보낸 값 또는 `null` | 보낸 값 또는 `null` | 보낸 값 또는 `null` |
| `imageUrl` | `null` | 이미지 경로 | `null` |
| `completedAt` | `null` | 완성 시각 | `null` |
| `errorMessage` | `null` | `null` | 사용자용 안내 |

프론트엔드는 우체통 화면에 `usage`로 베타 표시와 오늘 남은 횟수를 보여 준다. 횟수를 화면에 고정하지 않아 설정을 바꿔도 맞는다. `PROCESSING`인 동안 상세를 다시 불러 상태를 확인한다. `imageUrl`이 `null`이면 이미지를 부르지 않는다.

## 데이터 구조

| 테이블 | 컬럼 | 의미 |
| --- | --- | --- |
| `postcard` | `id`, `pet_id`(FK `fk_postcard_pet`, `ON DELETE RESTRICT`), `owner_member_id` | 엽서와 소유자 |
| | `source_kind`, `source_id` | 원본 종류(`DIARY`, 후속 `QUESTION_ANSWER`·`WALK`)와 ID. FK를 걸지 않아 원본을 지워도 남는다 |
| | `source_content` | 요청 때 복사한 일기 글(3,000자 이하, 글이 없으면 `""`). 그림 생성 입력 |
| | `source_note` | 요청의 추가 설명(200자 이하). 없으면 `null` |
| | `source_pet_species`, `source_pet_breed` | 요청 때 복사한 반려동물 종과 품종(품종은 `null` 가능). 사진이 없을 때 아이 모습을 맞춘다 |
| | `source_photo_key` | 요청 때 복사한 첫 사진 썸네일 파일 이름. 사진이 없으면 `null`. 생성이 끝나면 파일을 지운다 |
| | `title`, `text` | 제목, 일기 글 앞 200자(글이 없으면 `note`, 둘 다 없으면 `""`) |
| | `status`, `error_message`, `image_key` | 상태, 사용자용 실패 안내, 저장한 이미지 파일 이름 |
| | `requested_on` | 요청한 날(Asia/Seoul). 실패 때 그날 횟수를 돌려줄 때 쓴다 |
| | `created_at`, `processing_started_at`, `completed_at` | 요청·작업 시작·완성 시각 |
| `postcard_daily_usage` | `usage_date`, `member_id`, `count` | 회원당 하루 횟수. 실패하면 1 줄인다 |
| `postcard_daily_total` | `usage_date`, `count` | 전체 하루 생성 횟수. 줄이지 않는다 |

- 요청 하나는 한 트랜잭션에서 `postcard_daily_total` → `postcard_daily_usage` 순서로 그날 행을 잠그고(없으면 만들고), 두 제한을 확인한 뒤 횟수를 늘리고 엽서를 저장한다. 순서를 고정해 교착을 막는다.
- 횟수는 엽서 행이 아니라 이 두 테이블로 센다. 엽서를 지워도 횟수는 그대로다.
- 생성 요청이 어느 단계에서 실패하든(소유·모드 확인, 제한, 엽서 저장의 외래 키 위반) 같은 트랜잭션이라 늘린 횟수도 함께 되돌아간다. 이미 복사한 사진 파일은 S14의 정리 작업이 지운다.
- 파일은 `image-root` 아래 `images/{postcardId}.png`(완성 이미지)와 `sources/{postcardId}.jpg`(복사 사진)에 둔다. 이름에 엽서 ID가 들어가 정리 작업이 DB와 대조할 수 있다. 엽서 ID는 시퀀스라 롤백돼도 다시 쓰이지 않는다.
- 파일은 같은 폴더의 임시 이름(`*.part`)으로 쓴 뒤 이름을 바꿔, 쓰다 만 파일이 보이지 않게 한다.

## 범위 밖
- 기억 뽑기, 프론트엔드 HTTP 어댑터 연결, 생성 동의 안내 화면(글과 사진이 Google로 보내진다는 안내), 양면 다운로드: 다음 프론트엔드 이슈(#129)
- 사진 두 장 이상으로 그리기: 이번에는 첫 사진 한 장만 쓴다
- 반려동물 프로필 사진으로 그림을 닮게 하는 것: 반려동물 등록에 프로필 사진을 더하는 작업 뒤 후속 이슈(#127). 사진이 없는 원본에서 프로필 사진을 외모 1순위로 쓴다
- 엽서 요청 때 사진을 새로 올리거나 다른 일기 사진을 고르는 것: 프로필 사진과 같이 media 용도 정책을 정한 뒤 후속 이슈(#127)
- 질문 답변 원본(`QUESTION_ANSWER`): 서버 질문 답변 API(#31) 뒤 후속 이슈(#125)
- 산책 원본(`WALK`): 후속 이슈(#126). 산책에는 시각과 경로 좌표만 있어 `note`를 필수로 받고, 위치가 외부로 나가지 않게 좌표는 보내지 않는다. map에 산책 하나를 조회하는 입력 포트가 필요하다
- 다시 시도와 실패 안내: 사용자 흐름 기준으로 후속 이슈(#128)에서 설계한다(서버 자동 재시도, 실패 안내 문구, 실패분 제한 처리). 이번에는 실패한 엽서의 회원당 횟수를 돌려줘 새로 요청할 수 있게만 한다
- 엽서 판매·결제, 관리자 판매 관리: 관리자 담당 이슈. 관리자 기능이 엽서 데이터를 어떻게 읽을지도 그 후속 이슈에서 정한다
- 실제 우편·메일 발송, 가족과 엽서 공유
- AI로 엽서 문구 생성: 이번에는 일기 내용을 문구로 쓴다
- 보관함 즐겨찾기(데모의 `saved` 토글)

## 작업 순서
1. `postcard` 도메인, 저장소, 생성 요청 API를 가장 얇게 연결한다 — S1
   - 파일: `postcard/domain/Postcard.java`, `PostcardStatus.java`, `postcard/application/service/RequestPostcardService.java`, `postcard/adapter/out/persistence/*`, `postcard/adapter/in/web/PostcardController.java`, `RequestPostcardRequest.java`(`source`·`note`·`consent` 검증), 일기 조회 출력 포트와 diary 어댑터(`GetDiaryEntryUseCase` 사용), `HexagonalArchitectureTest` 도메인 목록에 `postcard` 추가, `docs/README.md` 도메인 목록
   - 확인: `./gradlew test --tests 'com.pawmento.postcard.*' --tests '*HexagonalArchitectureTest'` (원격 remote-db)
2. 모드·소유 확인, 하루 제한과 사용량 조회, 사진·설명·종 복사 — S2, S3, S16, S12·S15(요청 부분)
   - 파일: `RequestPostcardService.java`, `GetPostcardUsageService.java`, 반려동물 조회 출력 포트(`GetPetUseCase` 사용, 모드·종·품종), `PostcardUsagePort.java`와 잠금 저장소 어댑터, `PostcardLimitProperties.java`(`pawmento.postcard.daily-limit-per-member`(기본 3), `daily-limit-total`, `beta`), 일기 사진 출력 포트와 diary 어댑터(`GetDiaryFileUseCase`, 썸네일), `postcard/adapter/out/storage/VolumePostcardFileAdapter.java`, `PostcardStorageProperties.java`(`pawmento.postcard.image-root`), `application.yml`
   - 확인: 같은 명령
3. 생성 작업 선점, 이미지 저장, 만료, 만드는 중 삭제 — S4, S5, S6, S12·S15(생성 부분), S13
   - 파일: `ProcessPostcardService.java`, `postcard/adapter/in/scheduling/PostcardWorkScheduler.java`, `GeneratePostcardImagePort.java`, 가짜 생성기, `VolumePostcardFileAdapter.java`
   - 확인: 같은 명령
4. 목록·상세·이미지·삭제와 권한 — S8, S9
   - 파일: `ListPostcardsService.java`, `GetPostcardService.java`, `GetPostcardImageService.java`, `DeletePostcardService.java`, `PostcardController.java`
   - 확인: 같은 명령
5. 남은 파일 정리 작업 — S14
   - 파일: `CleanupPostcardFilesService.java`, `PostcardWorkScheduler.java`(정리 주기), `VolumePostcardFileAdapter.java`(목록·삭제), `PostcardStorageProperties.java`(유예 시간)
   - 확인: 같은 명령
6. 반려동물 삭제를 엽서가 막게 연결 — S10, S11
   - 파일: `postcard/application/port/in/HasPostcardsUseCase.java`, pet의 `adapter/out/records/DiaryPetRecordsAdapter.java`(일기 또는 엽서가 있으면 true, 이름 변경 검토), pet의 `PetPersistenceAdapter.deleteById`(외래 키 `fk_postcard_pet` 위반도 `PetHasRecordsException`으로 바꿈), postcard 저장 어댑터(`fk_postcard_pet` 위반을 반려동물 없음 404로 바꿈)
   - 확인: `./gradlew test --tests 'com.pawmento.postcard.*' --tests 'com.pawmento.pet.*'`
7. Gemini 이미지 어댑터와 설정 — S7
   - 파일: `postcard/adapter/out/ai/GeminiPostcardImageAdapter.java`, `GeminiImageProperties.java`(`pawmento.postcard.gemini.model`), `application.yml`, `.env.prod.example`(`GEMINI_API_KEY`), `docs/deploy.md`(키 설정)
   - 확인: 개발 서버에서 S7

## 정한 것
- 이슈 #103의 첫 범위에서 기억 뽑기, 프로필 사진, 질문 답변·산책 원본, 관리자 판매 연계를 뺐다. 이번에는 고른 일기의 글과 첫 사진, 추가 설명, 종·품종으로 그림을 만든다. 질문 답변과 프로필 사진은 선행 기능(#31, 반려동물 프로필 사진)이 없고, 산책은 글·사진이 없어 설명 입력 흐름과 함께 따로 설계한다. 기억 뽑기는 화면 기능이다.
- AI 이미지는 외부 API인 Google Gemini 이미지 모델로 만든다. 같은 프롬프트와 사진으로 OpenAI GPT Image와 웹에서 비교해 Gemini 결과가 나았다(2026-11-20, 담당자 확인). 사진을 넣어 화풍을 바꾸는 편집을 지원하고, 유료 등급은 보낸 내용을 학습에 쓰지 않는다. 서버에서 오픈 모델을 직접 돌리는 방식은 여러 팀이 같이 쓰는 개발 서버에 부담이 커서 쓰지 않고, 미리 그린 장면 그림을 고르는 방식은 매번 새 그림이 아니어서 쓰지 않는다.
- 모델은 설정 `pawmento.postcard.gemini.model`로 둔다. 기본값은 `gemini-nano-banana-2.1`(1K 약 $0.0336)이다. `gemini-3.1-flash-image`(약 $0.067)보다 싸고, Google 발표로는 화풍 변환이 좋아졌다. 독립 비교 자료가 적어 S7에서 두 모델을 비교해 확정하고, 바꿔도 코드는 고치지 않는다. 종료 예정인 `gemini-2.5-flash-image`(2027-03-15)는 쓰지 않는다.
- 결제·키 발급은 이슈 담당자가 한다. 결제 계정에 예산 알림을 건다.
- Gemini는 유료 등급 키만 쓴다. 무료 등급은 보낸 내용을 서비스 개선에 쓸 수 있어, 사용자의 일기 글과 사진을 그 조건으로 보내지 않는다.
- 일기 글, 첫 사진, 추가 설명, 반려동물 종·품종이 외부 서비스로 보내지므로 생성 요청마다 `consent: true`를 받는다. 안내 문구와 화면은 프론트엔드 이슈에서 만든다.
- 도메인 이름은 `postcard`로 한다. 프론트 데모의 `Postcard` 타입과 같고, `card`는 UI 카드·결제 카드와 헷갈린다.
- 엽서는 베타 기능으로 연다. 생성 품질과 실제 비용을 확인하는 기간이라 사용자에게 베타임과 하루 제한을 알린다. 베타 여부는 설정 `pawmento.postcard.beta`로 두고 `usage` 응답에 싣는다. 정식 전환 때 화면 문구와 제한을 함께 바꾼다.
- 하루 제한은 베타 기간에 회원당 3장, 팀 전체 100장이다. 회원당 제한은 프론트 데모(5장)보다 줄여 베타 기간 비용을 아낀다. 전체 상한은 키 유출이나 버그로 요청이 몰릴 때 비용을 막는다. 결제 예산 알림은 요청을 막지 않으므로 서버에서도 막는다. 두 값은 `application.yml` 설정으로 두어 결제 상황에 따라 코드 변경 없이 바꾼다.
- 횟수는 엽서 행이 아니라 따로 둔 사용 횟수로 센다. 엽서를 지워도 횟수는 돌아오지 않는다. 동시 요청은 그날 횟수 행을 잠가 한 건씩 처리한다.
- 실패한 엽서는 회원당 횟수를 돌려준다. 서버나 Gemini 탓인 실패로 사용자가 제한을 잃지 않게 한다. 전체 횟수는 돌려주지 않는다. Gemini를 실제로 불렀고, 시간 초과는 Gemini 쪽에서 이미 그림을 만들어 과금됐을 수 있다.
- 다시 시도 API는 이번에 만들지 않는다. 사용자 흐름(자동 재시도, 실패 안내)을 보고 후속 이슈에서 설계한다.
- 기억 뽑기는 프론트엔드가 다이어리 목록 API(`GET /diaries?petId=`, PR #100)로 한다. `size=1`로 `totalElements`를 받은 뒤 0부터 시작하는 무작위 `page` 하나를 불러 보여 주고, 고른 일기를 `source: {kind: "DIARY", id}`로 보내 생성을 요청한다. `GET /postcards/draw`처럼 동사를 경로에 넣지 않고, 미리 보며 다시 뽑아도 하루 제한과 비용을 쓰지 않는다. 서버는 받은 일기가 그 아이의 것인지와 모드를 다시 확인한다.
- 원본은 일기·질문 답변·산책 중 하나를 고를 수 있게 한다. 요청과 응답 모두 `source: {kind, id}`, 저장은 `source_kind`·`source_id`다. 후속 원본은 `kind` 값만 더하므로 API 계약이 바뀌지 않는다. 프론트 모델(`sourceEntries: [{kind, id}]`)과 이름을 맞춰 붙일 때 변환을 줄인다. 엽서는 원본 하나로 만들므로 배열이 아닌 객체 하나다. 일기·질문 답변 분리와 `diaryId` 통일(#115)을 따른다. 이번에는 `DIARY`만 받는다.
- 일기 첨부(#32, PR #107)의 첫 사진을 그림 입력에 넣는다. 사진이 있으면 아이 모습을 닮게 그릴 수 있고, 사진만 있는 일기로도 만들 수 있다. 첨부 순서에서 열 수 있는(`available`) 첫 사진 한 장만 쓰고, 음성은 쓰지 않는다.
- 재료가 부족하면 사용자가 보완한다. 일기 글, 쓸 수 있는 사진, `note` 중 하나도 없으면 409이고, 프론트엔드는 다시 고르기와 설명 더하기 중 하나를 고르게 한다. 일기 상세에서 미리 알 수 있어 대부분 요청 전에 화면에서 거른다. 설명은 짧은 글이나 음성 일기에도 붙일 수 있고, 후속 산책 원본도 같은 필드를 쓴다.
- 아이 모습은 사진이 있으면 사진으로, 없으면 반려동물 프로필의 종·품종으로 맞춘다. 프로필에는 외모 필드(털색 등)가 없고, 반려동물 등록에 프로필 사진을 더할 예정이라 외모 필드를 새로 만들지 않는다. 프로필 사진이 생기면 후속 이슈에서 외모 1순위로 쓴다.
- 요청 때 일기 글, 첫 사진의 썸네일, 추가 설명, 종·품종을 엽서에 복사한다. 생성 전에 일기가 고쳐지거나 지워져 첨부 파일이 정리돼도 요청한 내용으로 만든다. 일기와 엽서의 생명주기는 묶지 않는다.
- 만드는 중인 엽서도 지울 수 있다. 작업이 끝났을 때 엽서가 없으면 받은 그림을 저장하지 않고, 이미 저장했으면 지운다. 횟수는 돌려주지 않는다.
- 작업은 `processing_started_at`이 비어 있는 `PROCESSING` 엽서만 조건부 갱신(`... WHERE processing_started_at IS NULL`)으로 선점한 뒤 외부를 호출한다. 시작 시각이 있는 엽서는 재시작 뒤에도 다시 호출하지 않는다. Gemini가 요청을 받은 뒤 서버가 꺼지면 이미 과금됐을 수 있어서, 다시 부르면 중복 과금이 된다. (PR #118 리뷰 반영)
- 상태 전환은 모두 `status = 'PROCESSING'` 조건부 갱신이다. 완성·실패·만료 중 하나만 성공하고, 만료와 실패의 회원당 횟수 환불은 그 전환과 같은 트랜잭션에서 한 번만 한다. 늦게 온 결과가 전환에 실패하면 받은 이미지를 지운다.
- 파일과 DB는 한 트랜잭션으로 묶을 수 없어 순서를 정하고, 어긋난 것은 정리 작업이 지운다. 완성은 파일 저장 → DB 반영 순서이고, 삭제는 DB 커밋 → 파일 삭제 순서다. 정리 작업은 `images/`에서 `COMPLETED`이고 `image_key`가 같은 엽서의 파일만, `sources/`에서 `PROCESSING` 엽서의 파일만 남긴다. media 정리 작업은 이 폴더를 보지 않으므로 엽서가 따로 정리한다. (PR #118 리뷰 반영)
- 엽서가 남아 있는 반려동물은 지울 수 없다. 기존 "기록이 있으면 삭제를 막는다"(`HasPetRecordsPort`, 409)에 엽서를 더한다. 일기를 모두 지워도 엽서가 남을 수 있어서다.
- 삭제 확인과 생성 요청이 겹치면 DB 외래 키가 마지막으로 막는다. 엽서가 먼저 저장되면 반려동물 삭제가 409, 반려동물이 먼저 지워지면 생성 요청이 404다. 다이어리가 같은 경합을 `fk_diary_entries_pet`으로 처리하는 방식을 따른다.
- 남의 반려동물은 pet·diary와 같이 403, 다른 반려동물에 속한 엽서는 404로 응답한다.

## 가정
- 접근은 반려동물 소유자만 허용한다. 다이어리와 같이 가족 역할은 아직 열지 않는다.
- `ACTIVE` 모드와 하루 제한은 모드를 바꾸거나 다음 날이 되면 해결되므로 409로 응답한다. 전체 상한도 새 503 공통 예외를 만들지 않고 409로 둔다([예외 처리 문서](../backend-exceptions.md)).
- 생성은 비동기다. 작업은 media처럼 스케줄러가 선점한 엽서를 하나씩 처리하며, 서버는 한 대로 본다. 선점이 조건부 갱신이라 서버가 늘어도 같은 엽서를 두 번 부르지 않는다.
- 외부 호출은 60초가 지나면 실패로 처리하고, 이번에는 자동 재시도를 하지 않는다.
- 선점한 지 10분이 넘은 `PROCESSING` 엽서는 다음 작업 때 `FAILED`로 바꾸고 회원당 횟수를 돌려준다. 60초 제한보다 충분히 길어 진행 중인 호출을 만료시키지 않는다.
- 이미지는 1K 가로(3:2) PNG로 받는다. 비용이 해상도에 따라 달라서 크기를 고정한다.
- 사진은 원본 대신 media 썸네일(최대 512px JPEG, 1MiB 이하, EXIF 제거)을 보낸다. 원본은 최대 15MiB라 base64로 바꾸면 Gemini 인라인 요청 한도(20MB)에 걸릴 수 있다. 털색·무늬를 알아보기에 부족하면 S7에서 크기를 다시 정한다.
- 이미지와 복사 사진은 설정 `pawmento.postcard.image-root`(기본은 media 볼륨 아래 `postcards/`)에 저장한다. media 정리 작업은 `tmp/` 아래만 지워 겹치지 않는다.
- 파일 정리 작업은 1시간마다 돌고, 만든 지 1시간이 지난 파일만 본다. 저장 직후 DB 반영을 기다리는 파일을 지우지 않기 위해서다.
- 엽서 제목은 "`<이름>`와 함께한 작은 순간"으로 고정하고, 문구는 일기 글 앞 200자다. 글이 없으면 추가 설명을 문구로 쓴다.
- 목록 커서는 커뮤니티 글 목록(#21)과 같은 방식(마지막 엽서 ID)으로 한다.

## 참고
- 프론트 데모의 엽서 흐름: `frontend/src/life/application/useCases.ts`의 `requestPostcard`, 상태 `PROCESSING | COMPLETED | FAILED`, 하루 5장, 생성 동의 확인, 실패 후 같은 엽서로 다시 시도(이번 이슈에서는 다시 시도를 뺐다) ([앱 데모](../app-demo/full-demo.md) #12-R8, #12-S9)
- 회의 결정: 우체통은 정원 기능이고, 이번 범위는 AI 이미지 생성까지([1차 회의록](../meetings/2026-11-16.md))
- 다이어리는 일기와 질문 답변을 나눈다. 질문 답변 ID를 일기 ID로 쓰지 않는다([다이어리](../diary/README.md#도메인-경계)). 엽서도 원본 종류를 `source_kind`로 나눠 두 ID가 섞이지 않게 한다
- 남의 반려동물 접근은 `Pet.requireOwnedBy`가 `PetOwnershipViolationException`(403)을 던진다. 다이어리도 `GetPetUseCase`로 같은 검사를 한다.
- 반려동물 삭제 차단은 pet의 `HasPetRecordsPort`를 diary 어댑터(`DiaryPetRecordsAdapter`)가 구현한다. 삭제 서비스는 이 포트로 확인한 뒤 지우고, 그사이 생긴 기록은 `PetPersistenceAdapter.deleteById`가 외래 키 이름(`fk_diary_entries_pet`)과 SQLSTATE `23001`·`23503`으로 골라 409로 바꾼다. 다른 외래 키 위반은 그대로 던진다.
- media 용도는 지금 `DIARY_ATTACHMENT`, `QUESTION_ANSWER_ATTACHMENT` 두 가지이며, AI 생성물은 별도 정책을 정한 뒤 확장한다고 적혀 있다([미디어](../media/README.md))
- Gemini 이미지 모델은 장당 고정 가격이 아니라 출력 토큰으로 과금하고, 해상도가 클수록 비싸다. 1K 기준 `gemini-nano-banana-2.1` 약 $0.0336, `gemini-3.1-flash-image` 약 $0.067이다. 이미지 모델은 무료 등급에서 쓸 수 없다(2026-11 기준, [Gemini API 가격](https://ai.google.dev/gemini-api/docs/pricing))
- 일기 첨부는 `GetDiaryEntryUseCase` 결과의 `attachments`(순서, `type`, `available`)로 알고, 파일은 `GetDiaryFileUseCase`(`thumbnail: true`)가 소유·첨부 관계를 확인한 뒤 돌려준다. media는 이미지 저장 때 메타데이터를 지우고 512px 썸네일을 만든다([일기 첨부](../diary/attachments.md), [미디어](../media/README.md))
- 반려동물 등록은 `name`(필수), `species`(`DOG`·`CAT`·`OTHER`, 필수), `breed`(선택, 자유 입력), `birthday`(선택)를 받는다. 외모 정보와 프로필 사진은 없다
- 산책(`Walk`)에는 시작·끝 시각과 경로 좌표만 있고, map 입력 포트는 시작·좌표 추가·끝·날짜별 목록뿐이다. 서버에 질문 답변 도메인은 아직 없다(#31 진행 중)
- 다른 도메인은 애플리케이션 포트로만 쓰고 JPA 엔티티를 직접 참조하지 않는다([백엔드 구조](../backend-architecture.md))
