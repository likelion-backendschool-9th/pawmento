// 문구와 그림 종류はこの一覧で差し替える。ログイン/反려동물 데이터와 독립적인 소개 화면이다.
export const ONBOARDING_SLIDES = [
  { id: "diary", eyebrow: "다이어리 · 캘린더", title: "사진과 목소리로,\n오늘을 기록해요", description: "일기에 사진과 음성을 함께 남기고,\n캘린더에서 지난 기록을 모아보세요.", artwork: "companions", artworkLabel: "강아지, 고양이, 새와 작은 파충류가 함께 있는 PawMento 로고" },
  { id: "questions", eyebrow: "오늘의 질문", title: "질문 하나로 시작하는\n우리 아이의 이야기", description: "오늘의 질문에 나만의 답변을 남겨요.\n지난 질문과 가족의 답변도 함께 볼 수 있어요.", artwork: "questions", artworkLabel: "오늘의 질문과 가족들이 남긴 답변 카드" },
  { id: "walks", eyebrow: "산책 · 기억핀", title: "함께 걸은 길도\n소중한 기록으로", description: "산책 경로와 시간, 거리를 기록하고,\n위치가 담긴 일기는 기억핀으로 모아봐요.", artwork: "walks", artworkLabel: "산책 경로와 기억핀, 이동 거리와 시간이 표시된 지도 그림" },
  { id: "together", eyebrow: "가족 공유 · 커뮤니티", title: "가족과 함께 쓰고,\n이웃과 나눠요", description: "초대 코드로 같은 아이의 기록을 함께 쓰고,\n커뮤니티에서 다양한 이야기를 나눠보세요.", artwork: "together", artworkLabel: "서로 다른 동물 발자국으로 표현한 가족의 공동 기록과 이야기 카드" },
  { id: "garden", eyebrow: "일상 · 기억의 정원", title: "일상도, 그리운 기억도\n우리만의 공간에서", description: "아이마다 일상과 기억의 정원을 선택해요.\n정원의 나비를 눌러 남겨둔 기억을 만나보세요.", artwork: "memories", artworkLabel: "정원에 모인 소중한 기억 카드와 작은 나비" },
] as const;
export const SPLASH_DURATION_MS = 2600;
