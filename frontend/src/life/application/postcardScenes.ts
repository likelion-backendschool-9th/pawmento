import type { JournalItem } from "../../workspace/domain/models.ts";

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
export function sceneForJournalItem(record: JournalItem): PostcardScene {
  const text = `${record.question ?? ""} ${record.body}`;
  if (/낮잠|잠들|인형|졸|이불|쉬었|휴식|sleep|nap/i.test(text)) return "NAP";
  if (/바다|해변|노을|파도|모래|해질|sunset|beach|sea\b/i.test(text))
    return "BEACH";
  return "PARK";
}
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
