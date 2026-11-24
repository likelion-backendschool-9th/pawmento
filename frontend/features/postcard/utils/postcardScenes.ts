import type { JournalItem } from "../../diary/types/JournalItem.ts";

export const postcardScenes = {
  PARK: {
    title: "햇살 아래 공놀이",
    imageUrl: "/images/postcards/demo-park.png",
  },
  BEACH: {
    title: "노을빛 바닷가 산책",
    imageUrl: "/images/postcards/demo-beach.png",
  },
  NAP: {
    title: "포근한 오후의 낮잠",
    imageUrl: "/images/postcards/demo-nap.png",
  },
} as const;
export type PostcardScene = keyof typeof postcardScenes;
export function drawJournalItem(
  records: readonly JournalItem[],
  random = Math.random,
): JournalItem | undefined {
  if (!records.length) return undefined;
  return records[
    Math.min(
      records.length - 1,
      Math.max(0, Math.floor(random() * records.length)),
    )
  ];
}
