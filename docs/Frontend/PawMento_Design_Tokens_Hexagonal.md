# PawMento — 디자인 토큰 · UI 가이드

> **문서 버전:** 1.1 (헥사고날 프론트엔드 대응) · **대상:** PC 웹 우선 → 반응형 모바일 → PWA<br>
> **브랜드 컨셉:** 햇살 드는 정원, 기록하는 오늘, 차분한 추억. *따뜻하지만 유치하지 않은* 라이프로그 서비스.<br>
> **중요:** 이 문서의 수치와 색상은 첨부 기능명세에 **기재되어 있지 않은 디자인 제안값**이다. 실제 브랜드 색상과 대비 검증 후 확정한다.

## 1. 비주얼 방향

- **ACTIVE:** 아이보리 바탕, 세이지 그린 CTA, 살구색으로 작은 포인트. 일상·질문·기록에 초점.
- **GARDEN(P2):** 같은 디자인 시스템을 공유하되 부드러운 초록 그라데이션, 정원 이미지와 여유로운 여백으로 회상 경험 강화.
- **COMMUNITY(P2):** 본문 가독성 중심, 장식 최소. 장례·추모 관련 게시물의 이미지를 강제로 노출하거나 강조하지 않음.
- **AI 우체통(P3):** GARDEN 안에서만 쓰는 오브젝트. 따뜻한 크림색 봉투/조용한 반짝임. 강한 빨간 숫자 배지·팝업·자동 재생 없음(기존 UX 선호에 따른 제안).
- **일러스트:** 보리 같은 반려동물의 캐릭터는 빈 상태·온보딩·AI 엽서 시안에서 제한적으로 사용. 콘텐츠의 중심은 사용자가 올린 **실제 사진**.

## 2. 색상 팔레트 (Light Theme 제안)

| 토큰 | HEX | 용도 |
|---|---|---|
| `--color-ivory-50` | `#FFFCF7` | 카드 및 강조 배경 |
| `--color-ivory-100` | `#F8F3E9` | 기본 캔버스 |
| `--color-cream-200` | `#EFE9DE` | 구분 선 및 보조 배경 |
| `--color-sage-100` | `#E9EFE5` | 연한 포커스/선택 바탕 |
| `--color-sage-200` | `#DCE9D9` | 태그/배경 |
| `--color-sage-400` | `#A8B99B` | 차트/장식 |
| `--color-sage-600` | `#687F57` | 주요 버튼 및 아이콘(제안) |
| `--color-sage-700` | `#4D6541` | hover 및 어두운 브랜드색 |
| `--color-apricot-100` | `#F9E8D8` | 따뜻한 정보 카드 |
| `--color-apricot-300` | `#F5D9C5` | 엽서·작은 포인트 |
| `--color-sky-100` | `#EAF3F5` | 보조 정보 배경 |
| `--color-brown-900` | `#38352F` | 기본 텍스트 |
| `--color-brown-600` | `#625E56` | 보조 텍스트 |
| `--color-brown-500` | `#756F66` | 캡션(가독성 확인 필요) |
| `--color-white` | `#FFFFFF` | 입력·모달 표면 |
| `--color-danger-600` | `#B54745` | 오류 및 삭제 |
| `--color-success-700` | `#3C7051` | 저장 성공 및 안내 |

**사용 규칙**
- 기본 배경 **Ivory**, 카드 **Ivory 50/White**, 본문 **Brown 900**.
- 주 CTA **Sage 700 + 흰색 글씨**를 기본 조합으로 제안. Sage 400 위 흰 글씨처럼 대비가 약한 조합은 피한다.
- 살구색/연두색은 본문 텍스트보다 **배경이나 포인트 요소**에 사용.
- 역할색(오류·성공)은 반드시 텍스트/아이콘과 함께 전달하고 색만으로 상태를 표현하지 않는다.
- 실제 배포 전 WCAG 대비 확인: 본문 텍스트 **4.5:1 이상**, 큰 글자/주요 UI 요소는 요구 기준 확인.

## 3. Semantic Tokens

| 의미 | CSS 변수 | 초기값 |
|---|---|---|
| 페이지 배경 | `--surface-page` | `var(--color-ivory-100)` |
| 카드 | `--surface-card` | `var(--color-ivory-50)` |
| 입력/모달 | `--surface-elevated` | `var(--color-white)` |
| 부드러운 강조 | `--surface-subtle` | `var(--color-sage-100)` |
| 텍스트 | `--text-primary` | `var(--color-brown-900)` |
| 보조 텍스트 | `--text-secondary` | `var(--color-brown-600)` |
| 브랜드 | `--brand-primary` | `var(--color-sage-700)` |
| 브랜드 hover | `--brand-hover` | `#354B2B` |
| 포커스 링 | `--focus-ring` | `#385E46` |
| 기본 경계선 | `--border-default` | `#D8D8CD` |
| 오류 | `--text-danger` | `var(--color-danger-600)` |

## 4. Typography

| 유형 | 크기/행간 | 무게 | 용도 |
|---|---|---|---|
| 브랜드 영문 | `32px / 1.15` | 600 | PawMento 로고타입(선택적 세리프) |
| Display | `40px / 1.2` | 700 | PC 홈 주요 제목 |
| H1 | `32px / 1.25` | 700 | 화면 제목 |
| H2 | `24px / 1.35` | 700 | 섹션 제목 |
| H3 | `20px / 1.4` | 600 | 카드 제목 |
| Body L | `17px / 1.65` | 400 | 설명 본문 |
| Body | `16px / 1.6` | 400 | 기본 텍스트 |
| Small | `14px / 1.5` | 400 | 메타·헬프 텍스트 |
| Caption | `12px / 1.5` | 500 | 제한적 캡션 |

- 기본 스택: `Pretendard`, `SUIT`, `Noto Sans KR`, 시스템 sans-serif 순서(실제 배포 라이선스·로딩 확인).
- 영문 브랜드명에만 세리프체(예: Georgia)를 사용해도 됨. **기능 UI는 동일한 산세리프 서체**로 통일.
- 모바일에서는 Display `32px`, H1 `26px`, H2 `22px`; 본문은 가능하면 **16px 유지**.
- 텍스트 전체를 이미지로 제작하지 않는다. 버튼/라벨은 실제 HTML 텍스트로 구현한다.

## 5. Spacing · Radius · Shadow

**간격** (4px 계열): `4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 48 / 64 / 80` px.

| 요소 | 값 |
|---|---|
| 카드 내부 여백 | `20~24px` (모바일 `16px`) |
| 섹션 사이 | `48~64px` (모바일 `32px`) |
| 버튼 높이 | 기본 `44px`, 강조 CTA `48px` |
| 입력 높이 | `44px` 이상 |
| 아이콘 크기 | `16 / 20 / 24` px |
| Radius sm | `8px` |
| Radius md | `12px` |
| Radius lg | `18px` |
| Radius xl | `24px` |
| Radius pill | `999px` |
| 그림자 낮음 | `0 2px 10px rgb(44 54 36 / .06)` |
| 그림자 카드 | `0 8px 28px rgb(44 54 36 / .08)` |
| 그림자 모달 | `0 20px 60px rgb(44 54 36 / .16)` |

그림자는 적게 사용하고, **테두리·여백·크림 톤 대비**로 구조를 만든다. GARDEN 배경 일러스트가 밝으면 반드시 읽기 좋은 불투명 카드 위에 본문을 배치한다.

## 6. Layout · Breakpoints

| 범위 | 제안 레이아웃 |
|---|---|
| `>= 1280px` 데스크톱 | 최대 컨테이너 `1200px`, 12컬럼, 큰 이미지 + 보조 패널 |
| `1024~1279px` 노트북 | 컨테이너 `calc(100%-48px)`, 8~12컬럼 유연 |
| `768~1023px` 태블릿 | 8컬럼, 카드 2열, 사이드 패널 축소 |
| `<= 767px` 모바일 | 4컬럼 또는 1열, 좌우 여백 16px, 간결한 헤더·하단 내비게이션 |
| `<= 390px` 소형 모바일 | 카드·폼 한 열, 버튼 전체 너비 검토 |

- PC 우선이지만 **모바일에서 스크롤/터치 불편이 없도록** 구조를 처음부터 유연하게 제작.
- 사진 갤러리: PC 3~4열, 태블릿 2열, 모바일 2열 또는 콘텐츠에 맞는 1열.
- 일기 작성 폼: PC 사진/답변 2열 가능, 모바일 한 열 스택.
- 최소 터치 영역 `44px` 권장, 고정 하단 CTA는 키보드가 떠도 입력창을 가리지 않도록.
- `max-width: 1200px` 전체 컨테이너, 읽기용 텍스트는 `max-width: 720px` 권장.

## 7. Component UI States

| 컴포넌트 | 기본 | 포커스/선택 | 비활성/오류 |
|---|---|---|---|
| Primary Button | 진한 세이지, 흰 텍스트 | 어두운 세이지, 포커스 링 | opacity만 줄이지 말고 명확한 비활성 |
| Secondary Button | 밝은 카드, 세이지 텍스트 | 테두리 강조 | disabled 일관화 |
| Text Input | 화이트, 옅은 경계선 | 선명한 링 | 붉은 테두리+오류 문구 |
| Question Card | 크림/세이지 배경 | 가벼운 그림자/테두리 | 작성 완료 시 완료 문구 |
| Record Card | 사진 미리보기 + 날짜 | hover elevation | 이미지 로딩 실패 대체 UI |
| Tab | 글자+언더라인/배경 | 선택된 탭 명확히 | 접근 불가 탭은 숨김 또는 안내 |
| Toast | 중립 크림 | 성공·오류 상태 | 자동 사라짐 이전 읽을 시간 보장 |
| Dialog | 흰 표면/짙은 본문 | 키보드 포커스 트랩 | 확인 중 중복 제출 차단 |

### 카드별 밀도 가이드

- **오늘의 질문:** 한 화면에 질문은 **한 개**. 질문과 CTA를 가장 강조.
- **사진 기록:** 썸네일, 제목/첫 줄, 날짜, 유형 정도만. 한 카드 안에 긴 본문 삽입 금지.
- **GARDEN:** 사진/목소리/타임라인과 조용한 정원 테마. 묘비·검정 추모 UI 금지.
- **우체통:** 도착 봉투 유무로 상태를 알리고 **강한 배지나 푸시 없음**(확정 필요). 기억의 정원 전용.
- **커뮤니티:** 제목, 카테고리, 작성 정보, 댓글/좋아요 개수; 정원 장식보다 읽기 쉬운 정보 위계 우선.

## 8. Design Tokens CSS — 복사해서 시작하는 코드

아래는 `src/app.css`에 배치하거나 `src/ui/tokens.css`로 분리해 import해도 되는 **초안**이다. 모든 CSS Module은 이 변수만 참조하도록 한다.

```css
:root {
  color-scheme: light;
  --color-ivory-50: #fffcf7;
  --color-ivory-100: #f8f3e9;
  --color-cream-200: #efe9de;
  --color-sage-100: #e9efe5;
  --color-sage-200: #dce9d9;
  --color-sage-400: #a8b99b;
  --color-sage-600: #687f57;
  --color-sage-700: #4d6541;
  --color-apricot-100: #f9e8d8;
  --color-apricot-300: #f5d9c5;
  --color-sky-100: #eaf3f5;
  --color-brown-900: #38352f;
  --color-brown-600: #625e56;
  --color-brown-500: #756f66;
  --color-white: #fff;
  --color-danger-600: #b54745;
  --color-success-700: #3c7051;

  --surface-page: var(--color-ivory-100);
  --surface-card: var(--color-ivory-50);
  --surface-elevated: var(--color-white);
  --surface-subtle: var(--color-sage-100);
  --text-primary: var(--color-brown-900);
  --text-secondary: var(--color-brown-600);
  --text-danger: var(--color-danger-600);
  --brand-primary: var(--color-sage-700);
  --brand-hover: #354b2b;
  --border-default: #d8d8cd;
  --focus-ring: #385e46;

  --font-sans: Pretendard, SUIT, 'Noto Sans KR', system-ui, sans-serif;
  --font-brand: Georgia, 'Times New Roman', serif;
  --text-xs: 0.75rem;
  --text-sm: 0.875rem;
  --text-md: 1rem;
  --text-lg: 1.0625rem;
  --heading-sm: 1.25rem;
  --heading-md: 1.5rem;
  --heading-lg: 2rem;
  --display: 2.5rem;

  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-5: 20px;
  --space-6: 24px;
  --space-8: 32px;
  --space-10: 40px;
  --space-12: 48px;
  --space-16: 64px;
  --space-20: 80px;

  --radius-sm: 8px;
  --radius-md: 12px;
  --radius-lg: 18px;
  --radius-xl: 24px;
  --radius-pill: 999px;
  --shadow-low: 0 2px 10px rgb(44 54 36 / 0.06);
  --shadow-card: 0 8px 28px rgb(44 54 36 / 0.08);
  --shadow-modal: 0 20px 60px rgb(44 54 36 / 0.16);
  --layout-max: 1200px;
  --reading-max: 720px;
  --control-height: 44px;
  --control-height-lg: 48px;
}

*, *::before, *::after { box-sizing: border-box; }
body {
  margin: 0;
  background: var(--surface-page);
  color: var(--text-primary);
  font: 400 var(--text-md)/1.6 var(--font-sans);
}
button, input, textarea, select { font: inherit; }
:focus-visible { outline: 3px solid var(--focus-ring); outline-offset: 3px; }

@media (max-width: 767px) {
  :root { --display: 2rem; --heading-lg: 1.625rem; --heading-md: 1.375rem; }
}
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}
```

## 9. Motion · Icon · Photography

- 기본 전환 시간 `150~220ms`, easing `ease-out`. 버튼 hover는 살짝 밝기/그림자 변화만.
- GARDEN 우체통 오픈 애니메이션은 사용자가 클릭했을 때만, `prefers-reduced-motion` 준수.
- 아이콘은 하나의 일관된 라인 아이콘 세트 사용(예: Lucide). 둥근 모서리와 1.75~2px 스트로크.
- 동물/발바닥/꽃 모티프는 로고와 일부 보조 요소에만. 모든 버튼에 발바닥을 반복하지 않음.
- 사진은 실제 기록을 우선. 샘플 데이터나 로컬 정적 이미지가 실제 사용자 정보처럼 보이지 않게.
- 정원 배경은 홈 전체에 난잡하게 깔지 않고 GARDEN Hero에 집중. 텍스트 가독성 유지.

## 10. UI 검수 체크리스트

- [ ] 모든 텍스트/버튼/배경 색상이 semantic token을 사용한다.
- [ ] PC 1440px, 태블릿 768px, 모바일 390px와 360px에서 깨지지 않는다.
- [ ] 본문 최소 16px 권장, 보조 정보 가독성 보장.
- [ ] 탭/버튼/필드는 키보드로 접근 가능하며 포커스가 보인다.
- [ ] 배경 이미지는 의미 있는 사진/콘텐츠를 방해하지 않는다.
- [ ] 질문형 일기의 질문과 저장 CTA가 최우선 시각 요소다.
- [ ] 모드 전환 확인 대화상자는 데이터 보존과 되돌리기를 설명한다.
- [ ] GARDEN의 우체통은 ACTIVE에 나타나지 않는다(기획 확정 시).
- [ ] AI 생성 엽서에는 출처와 'AI 생성' 표시를 제공한다.
- [ ] 로딩·비어 있음·오류·완료 상태의 시각 규칙이 일치한다.

---

**연계 문서:** `PawMento_Frontend_Blueprint.md` · 기준 요구사항 `PawMento_기능명세.md`.


---

## 부록. 헥사고날 아키텍처에서의 디자인 토큰 관리 (v1.1 추가)

디자인 토큰은 **비즈니스 도메인 모델이 아닌 UI 어댑터의 표현 계층 관심사**다. `domain/`이나 `application/`에서 CSS/테마/색상을 참조하지 않는다.

권장 배치:

```text
src/
├─ app.css                      # 아래 tokens.css import + 리셋
├─ shared/ui/
│  ├─ tokens.css               # 원시·semantic CSS 변수의 단일 출처
│  ├─ Button/Button.module.css # 토큰만 참조, 스타일 코로케이션
│  └─ ...
├─ diaries/adapters/inbound/
│  ├─ TodayQuestionCard.tsx
│  └─ TodayQuestionCard.module.css
└─ garden/adapters/inbound/
   ├─ GardenHomeScreen.tsx
   └─ GardenHomeScreen.module.css
```

- `domain`의 `PetMode`를 UI가 받아 `data-mode="active"` 또는 `data-mode="garden"`처럼 **표현용 속성으로 매핑**한다. 도메인 엔티티 안에 CSS 클래스나 컬러값을 저장하지 않는다.
- 원시 컬러는 디자인 시스템에서, ACTIVE/GARDEN 배경과 우체통 스타일은 각 **inbound UI**에서 관리한다.
- 서버에서 카테고리명이나 기록 종류가 늘어도 색상/뱃지 표현은 별도 view-model 매핑만 수정한다.
- **웹 우선:** 데스크톱 타이포/그리드를 기준으로 만들고 태블릿/모바일 미디어쿼리를 적용. PWA에서도 동일 토큰을 재사용한다.
- 프론트 테스트에서는 비즈니스 규칙 테스트와 별개로 Storybook/컴포넌트 테스트로 hover/focus/disabled/error 및 접근성을 확인한다.
