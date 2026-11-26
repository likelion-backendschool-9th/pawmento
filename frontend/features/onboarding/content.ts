// 문구와 그림 종류는 이 목록에서 교체한다. 로그인/반려동물 데이터와 독립적인 소개 화면이다.
export const ONBOARDING_SLIDES = [
  { id: "companions", eyebrow: "모든 아이의 이야기", title: "우리의 모든 순간이,\n소중한 기억이 되도록", description: "어떤 모습의 친구와 함께하든,\n함께한 하루를 따뜻하게 기록해요.", artwork: "companions", artworkLabel: "강아지, 고양이, 새와 작은 파충류가 함께 있는 PawMento 로고" },
  { id: "days", eyebrow: "오늘을 담는 작은 습관", title: "함께한 일상을\n차곡차곡", description: "사진 한 장, 짧은 이야기, 익숙한 목소리.\n작은 순간도 편하게 남겨보세요.", artwork: "days", artworkLabel: "일상의 사진과 이야기를 담은 두 장의 기록 카드" },
  { id: "memories", eyebrow: "언제든 다시 만나는 기억", title: "소중한 기억을\n오래도록", description: "함께 웃었던 날도, 문득 그리운 순간도.\n우리만의 공간에서 다시 꺼내보세요.", artwork: "memories", artworkLabel: "따뜻한 정원에 책처럼 모인 소중한 기억 카드" },
] as const;
export const SPLASH_DURATION_MS = 2600;
