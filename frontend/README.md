# PawMento 프론트엔드

Next.js App Router 기반 P1~P3 프론트엔드 데모. `docs/Frontend/`의 헥사고날 청사진을 따르며 일상·정원 공통 상단 탐색·사진 전시형 홈과 모바일 하단 탐색을 제공한다.

## 실행

Node.js 24와 npm을 권장한다. 테스트는 Node의 TypeScript 실행 기능을 사용한다.

```sh
cd frontend
npm ci
npm run dev
```

개발 서버가 출력하는 Local 주소를 연다. 3000번 포트가 사용 중이면 다음 포트를 선택한다. 개발 서버의 호스트가 `0.0.0.0`이면 `localhost` 주소를 사용한다. Next.js는 허용되지 않은 개발 origin의 클라이언트 리소스를 차단한다.

## 화면과 데모 동작

| 영역 | 경로 | 동작 |
| --- | --- | --- |
| 진입·인증 | `/`, `/sign-up`, `/sign-in`, `/reset-password` | 가입·로그인·Google/카카오 데모·로컬 비밀번호 변경 |
| 대시보드 | `/home` | 질문·답변 작성 상태, 최근 기록·달력·주요 메뉴 |
| 반려동물 | `/pets`, `/pets/new`, `/pets/:petId` | 등록·선택·수정·빈 프로필 삭제 |
| 질문 일기 | `/pets/:petId/diary/new`, `/pets/:petId/diary/:recordId` | 질문 ID·질문 본문·답변 함께 저장, 수정·삭제 |
| 자유 기록 | `/pets/:petId/records/new`, `/pets/:petId/records/:recordId` | 날짜·본문·대표 사진·추가 미디어 저장, 수정·삭제 |
| 달력·타임라인 | `/pets/:petId/calendar`, `/pets/:petId/timeline` | 자유 기록만 일기/달력으로 조회, 이전 기록 더 보기 |
| 질문 이력 | `/pets/:petId/questions` | 작성 당시 질문과 답변을 함께 조회 |
| 미디어 | `/pets/:petId/media` | 사진·영상·음성 필터와 원본 기록 이동 |
| 모드·정원 | `/pets/:petId/settings/mode`, `/pets/:petId/garden` | 반려동물 관리 카드에서 해당 아이만 모드 변경, 과거 오늘·무작위 기억 |
| 우체통 | `/pets/:petId/garden/mailbox`, `/new`, `/:postcardId` | 정원 전용, 기록 선택·데모 생성·진행/실패/재시도·보관·HTML 다운로드 |
| 커뮤니티 | `/community`, `/community/new`, `/community/:postId` | 반려 생활 기본, 카테고리별 검색·정렬, 글·댓글 CRUD, 좋아요·북마크·신고 |
| 가족 | `/pets/:petId/family`, `/family/invite/:inviteId` | 초대 링크·수락·만료·취소, 편집자/열람자 역할·공유 해제 |
| 마이·활동 | `/me`, `/me/activity` | 닉네임·로그아웃·데모 탈퇴, 내 글·댓글·북마크 |
| 운영 | `/admin/reports`, `/admin/questions`, `/admin/notices`, `/admin/members` | 신고 처리, 질문 풀·공지·데모 회원 상태 |
| 설치·연결 | `/settings`, `/offline` | 설치 지원 시 프롬프트, 온라인/오프라인 상태, 오프라인 안내 |

일기·자유 기록·커뮤니티 상세 뒤의 `/edit` 경로에서 수정한다. 질문형 일기는 질문 스냅샷을 고정하며 관리자 질문 수정이나 답변 수정으로 과거 질문이 바뀌지 않는다.

## 데모 데이터 범위

실제 서버 인증·OAuth·업로드·AI·메일 전송과 연결되지 않는다. 가입한 계정과 비밀번호는 현재 탭의 메모리에서만 유지되며 새로고침하면 소멸한다. 실제 서비스 비밀번호를 사용하지 않는다. 가입 없는 데모와 소셜 데모는 고정 ID를 사용하므로 해당 데모로 다시 시작하면 브라우저에 남아 있는 기록을 불러올 수 있다.

`pawmento.workspace.v1.<계정 ID>`는 개인별 반려동물·기록, `pawmento.life.v1`은 같은 브라우저에서 공유되는 커뮤니티·초대·엽서·운영 상태다. 가족 초대 수락 시 소유자의 원본 저장 공간을 조회하며 편집자는 기록을 작성·수정하고 열람자는 읽기만 한다. 다른 기기나 브라우저 사이의 실제 공유는 제공하지 않는다. 브라우저 저장소 삭제 시 복구되지 않는다.

가입 없는 ‘보리네’ 데모에는 관리자 데모 권한이 있고 일반 가입·소셜 데모에는 없다. 서버 권한 검증의 대체가 아니다. 데모 엽서는 무작위 기록 하나의 상황과 반려견 참조 사진을 연결해 미리 준비한 수채화 장면을 표시하며 실제 AI 생성으로 표시하지 않는다. 다운로드는 양면·그림·글꼴을 내장한 독립 HTML 파일이다. 생성 한도는 한국 날짜 기준 하루 5장이다.

대표 사진은 JPG/PNG/WebP 1MB까지, 추가 첨부는 사진·MP4/WebM·음성 개별 2MB/최대 5개다. 지원 브라우저에서는 마이크로 최대 30초 녹음할 수 있다. 미디어는 자동 재생하지 않는다. 브라우저 전체 저장 한도를 초과하면 입력을 보존하고 오류를 표시한다.

반려동물 프로필은 연관 기록이 없는 경우에만 삭제한다. 기록이 있는 프로필은 보관 정책 확정 전까지 유지한다. 외부 푸시와 자동 엽서 생성은 만들지 않는다. 우체통은 GARDEN에서만 노출한다.

## 아키텍처

`src/app/(auth)`와 `src/app/(app)`의 파일 기반 페이지를 사용한다. 공통 인증·Shell은 `(app)/layout.tsx`에 있고 각 page는 필요한 inbound 화면만 import한다. 없는 URL은 Next.js의 404, 로딩과 예상하지 못한 오류는 `loading.tsx`·`error.tsx`로 처리한다. 도메인과 유스케이스는 라우팅을 참조하지 않는다.

미저장 편집기는 링크·탭 닫기와 프로그램 방식의 반려동물 전환 전에 같은 확인을 사용한다. 전환을 취소하면 선택 아이와 입력을 유지한다. 날짜 표시는 브라우저 시간대와 관계없이 한국 날짜를 사용한다.

- `src/auth`, `src/workspace`, `src/life`의 `domain`: 순수 모델과 검증
- `application`: 포트·유스케이스. React·브라우저 저장소를 참조하지 않음
- `adapters/outbound`: 데모 인증·저장소·가족 공유·질문 풀·데모 엽서·다운로드
- `adapters/inbound`: 화면·표현 훅·CSS Modules
- `src/app/composition.ts`: 어댑터 주입
- `src/shared/ui`: 로고·입력·미디어·질문/답변 표현 컴포넌트

서버 계약 확정 후 HTTP 어댑터로 교체하고 실제 인증·권한·업로드·공유·AI 처리를 연결한다. 인증 정보를 localStorage에 저장하는 방식으로 확장하지 않는다.

사용자가 제공한 두 PNG는 `Brand.tsx`의 워드마크와 결합한 로고로 사용하며 화면 장식으로 반복하지 않는다. ACTIVE에서는 코기, GARDEN에서는 정원 로고를 사용한다. 원본 픽셀을 재생성하지 않는다. 샘플 사진은 레퍼런스의 사진 영역을 SVG 뷰포트로 표시한다. 출처는 `public/images/CREDITS.txt`, 자체 호스팅 폰트 라이선스는 `public/fonts/OFL.txt`에 있다.

## PWA

설치 가능한 브라우저에서 `/settings`의 설치 버튼을 이용한다. 운영 빌드에서만 서비스워커를 등록하며, 연결이 끊긴 탐색은 정적 `offline.html` 안내를 표시한다. 오프라인에서 서버 데이터 동기화나 계정 복구를 제공하는 것은 아니다.

```sh
npm run build
npm run start
```

## 검사

```sh
npm test
npm run lint
npx tsc --noEmit
npm run build
```

자동 테스트는 이슈 #12의 시나리오 번호를 사용한다. 사용자가 승인한 전체 확장 범위와 시나리오는 `docs/app-demo/full-demo.md`에 있다.
