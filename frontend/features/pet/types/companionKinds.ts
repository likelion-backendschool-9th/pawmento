// 온보딩 데모의 표시용 선택지. 서버의 DOG/CAT/OTHER 계약은 확장하지 않는다.
export const COMPANION_KINDS = [
  { id: "DOG", label: "강아지", species: "DOG" },
  { id: "CAT", label: "고양이", species: "CAT" },
  { id: "RABBIT", label: "토끼", species: "OTHER" },
  { id: "HAMSTER", label: "햄스터", species: "OTHER" },
  { id: "BIRD", label: "새", species: "OTHER" },
  { id: "TURTLE", label: "거북이", species: "OTHER" },
  { id: "REPTILE", label: "파충류", species: "OTHER" },
  { id: "OTHER", label: "그 외", species: "OTHER" },
] as const;
