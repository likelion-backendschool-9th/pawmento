import type { JournalItem } from "../../diary/types/JournalItem.ts";
import type { SessionSource } from "../../member/types/account.ts";

export type GardenMemory = { key: string; href: string; item: JournalItem };
// Current detail routes read workspace demo records only. Never send a server ID there.
export function gardenMemories(
  source: SessionSource,
  petId: string,
  items: readonly JournalItem[],
): GardenMemory[] {
  if (source !== "demo") return [];
  return items
    .filter((item) => item.petId === petId)
    .map((item) => ({
      key: `${item.kind}:${item.id}`,
      href: `/pets/${petId}/${item.kind === "DIARY" ? "diary" : "questions/answers"}/${item.id}`,
      item,
    }));
}
export function anotherMemory(
  items: readonly GardenMemory[],
  previous: string | null,
  random = Math.random,
): GardenMemory | null {
  const candidates =
    items.length > 1 ? items.filter((item) => item.key !== previous) : items;
  if (!candidates.length) return null;
  return candidates[
    Math.min(
      candidates.length - 1,
      Math.max(0, Math.floor(random() * candidates.length)),
    )
  ];
}
