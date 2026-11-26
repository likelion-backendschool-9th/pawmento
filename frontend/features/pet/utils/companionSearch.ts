import { COMPANION_KINDS } from "../types/companionKinds.ts";

export function searchCompanionKinds(query: string) {
  const text = query.trim().replace(/\s/g, "").toLowerCase();
  return COMPANION_KINDS.filter((kind) => kind.label.replace(/\s/g, "").includes(text) || kind.id.toLowerCase().includes(text));
}

// 자동완성에 보여 줄 선택지. 맞는 종류가 없으면 안내대로 바로 고를 수 있게 '그 외'를 보여 준다.
export function companionOptions(query: string) {
  const found = searchCompanionKinds(query);
  return found.length > 0
    ? { options: found, noMatch: false }
    : { options: COMPANION_KINDS.filter((kind) => kind.id === "OTHER"), noMatch: true };
}
