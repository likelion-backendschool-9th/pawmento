# report

신고(글, 이후 댓글)의 접수·운영 조회·처리와 신고 당시 원본(스냅샷) 보존에서 정한 것, 데이터 구조, 아직 정하지 않은 것을 모은다. 관리자 화면·API의 계약은 [관리자 신고 관리 계획](../admin/report-management.md)에 있다.

## 이슈와 문서

| 이슈 | 내용 | 문서 |
| --- | --- | --- |
| [#112](https://github.com/likelion-backendschool-9th/pawmento/issues/112) | 게시글 신고 접수와 운영 목록 | [post-report.md](post-report.md) |
| [#92](https://github.com/likelion-backendschool-9th/pawmento/issues/92) | 관리자 신고 목록·상세·처리 | [report-management.md](../admin/report-management.md) |

## 정한 것

- 신고는 별도 도메인 `report`(`com.pawmento.report`)가 맡는다. 접수·운영 목록·상세·처리·스냅샷 보존이 모두 여기 있다. 대상의 조회와 숨김은 `report`가 자기 출력 포트로 선언하고, `adapter/out/<대상 도메인>` 어댑터가 대상 도메인의 `port/in`을 불러 구현한다. 신고는 글과 사용자(회원 / 관리자)도 생명주기(글 삭제 / 신고 보존·1년 정리)도 다르고, 댓글 신고가 로드맵 MVP에 있어 대상이 늘기 때문이다. 처음에는 community 안에 두었다. ([#92 제안](https://github.com/likelion-backendschool-9th/pawmento/issues/92#issuecomment-7181863762)과 [관리자 담당 답변](https://github.com/likelion-backendschool-9th/pawmento/issues/92#issuecomment-7182002811), 고침 #112: community 소유에서 분리)
- 신고 대상은 `target_type`+`target_id`로 가리킨다. 지금 받는 종류는 `POST`뿐이고, 댓글이 생기면 `COMMENT`를 더한다. 대상 테이블을 FK로 묶지 않고, 접수할 때 대상 도메인이 존재를 확인한다(없으면 404). 대상이 늘어도 컬럼과 계약이 그대로이고, `reports`가 다른 도메인 테이블에 묶이지 않는다. (#92, #112)
- 사용자 접수 경로는 `POST /api/v1/reports`이고 본문에 대상을 담는다(`{"targetType":"POST","targetId":7,...}`). 관리자 경로는 `/api/v1/admin/reports`이고 컨트롤러도 `report`에 둔다. 구조 문서의 "상위 도메인 접두어를 공유하지 않는다"와 "관리자 API 구현은 관리 대상 도메인 안에 둔다"를 따른다. (#92, #112)
- 운영 응답의 대상 필드는 `targetType`·`targetId`·`targetState`·`targetVersion`이다. `targetState`는 `VISIBLE`·`HIDDEN`·`DELETED`·`MISSING`이고, `MISSING`은 대상 조회 결과에 그 대상이 없을 때다. 목록은 신고 테이블만으로 페이지와 커서를 정하고, 대상 상태는 그 페이지의 대상 id들로 대상 도메인을 한 번에 조회해 붙인다. 대상 도메인 테이블과 SQL로 조인하지 않는다. (#92, #112)
- 관리자가 신고를 RESOLVED로 처리할 때 대상 숨김은 `report`가 대상 도메인의 숨김 `port/in`을 같은 트랜잭션에서 불러 함께 성공하거나 함께 롤백한다. family가 pet의 `LockPetOwnerUseCase`를 자기 트랜잭션에서 부르는 것과 같다. (#92)
- 신고가 쌓여도 대상을 자동으로 숨기지 않는다. 관리자가 신고를 RESOLVED로 처리할 때만 숨긴다. 숨긴 글의 공개·작성자 화면 동작은 [community README](../community/README.md#정한-것)에 있다. ([관리자 계획 Q3·Q5](../admin/report-management.md#q3q5--처리기각과-게시글-숨김), #92)
- 일반 사용자 화면과 API에서는 신고 여부와 건수를 신고한 본인 말고는 알리지 않는다. 다른 회원, 비로그인, 작성자 모두에게 보여 주지 않는다. 관리자 목록·상세는 관리자 계획의 계약대로 조회한다. 기각되기 전의 신고가 작성자에 대한 인상을 만들지 않게 하기 위해서다. 작성자는 관리자가 숨김 처리했을 때만 알게 된다. (#112)

## 데이터 구조 요약

`reports` 테이블

| 컬럼 | 설명 |
| --- | --- |
| `id` | 신고 id. 자동 증가. 운영 목록의 정렬과 커서 기준 |
| `target_type` | 대상 종류(`POST`) |
| `target_id` | 대상 id. FK 없음 |
| `reporter_id` | 신고한 회원 id. FK 없음(탈퇴 하드 삭제를 막지 않는다) |
| `status` | `PENDING`·`RESOLVED`·`DISMISSED` |
| `reason_type` | `SPAM`·`ABUSE`·`HARMFUL`·`MISINFO`·`PRIVACY`·`OTHER` |
| `reason_detail` | `OTHER`일 때만 1~200자. 처리 1년 뒤 정리 작업이 지운다 |
| `snapshot_title` | 신고 당시 제목. 제목이 없는 대상은 null. 정리 작업이 지운다 |
| `snapshot_body` | 신고 당시 본문. 정리 작업이 지운다 |
| `snapshot_category_code` | 신고 당시 글 카테고리. 글이 아니면 null |
| `snapshot_target_version` | 신고 당시 대상 버전. 기각 뒤 재신고 판정에 써서 정리하지 않는다 |
| `snapshot_cleared_at` | 스냅샷을 정리한 시각. 정리 전에는 null |
| `created_at` | 접수 시각. 스냅샷 캡처 시각과 같다 |
| `version` | 처리 경합 확인용 버전(`@Version`) |

처리자·처리 시각·메모 컬럼은 #92의 처리 단계에서 더한다. 인덱스는 `ux_reports_pending`(대상·신고자, PENDING만 고유), `ix_reports_status_id`, `ix_reports_target_reporter`다.

## 아직 정하지 않은 것

- **공백 기준(`trim()`) 함수의 위치**: 글, 신고 설명, 처리 메모가 모두 `Post`의 JavaScript `trim()` 기준을 쓴다. `global`에 순수 문자열 유틸(`global.text`)을 두고 `domain`에서 import할 수 있게 [구조 문서](../backend-architecture.md)의 의존 규칙에 더하는 방향에 관리자 담당이 동의했고, 도메인의 의존 규칙을 바꾸는 일이라 팀 합의 뒤 적용한다. 그전까지 `report` 도메인에 같은 함수를 따로 두고, 합의되면 한곳으로 합친다. (#112)
- **댓글 신고**: 댓글 기능이 생길 때 `COMMENT` 대상의 스냅샷(제목 null, 부가 정보)과 숨김 포트를 정한다.
