import { COMPANION_KINDS } from "../types/companionKinds.ts";

export function searchCompanionKinds(query: string) {
  const text = query.trim().replace(/\s/g, "").toLowerCase();
  return COMPANION_KINDS.filter((kind) => kind.label.replace(/\s/g, "").includes(text) || kind.id.toLowerCase().includes(text));
}
