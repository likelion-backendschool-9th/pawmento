import type { PlaceSearchState } from "./placeSearchController.ts";
import { checkSearchText } from "./placeSearchText.ts";

// 검색 화면이 무엇을 보일지(#195-S9, S10, S12, S13). 입력·검색 상태와 최근 검색어 유무로만 정해서 화면 없이 테스트한다.
export type SearchView =
  | "recent" // 최근 검색어 목록
  | "guide" // 최근 검색어가 없을 때의 안내
  | "invalid" // 너무 길거나 쓸 수 없는 글자가 든 입력(검색하지 않는다)
  | "searching" // 처음 찾는 중
  | "results" // 결과 목록(새로 찾는 동안에는 이전 결과를 흐리게 둔다)
  | "empty" // 결과 없음
  | "error"; // 오류와 다시 시도

export function searchView(state: PlaceSearchState, hasRecent: boolean): SearchView {
  switch (state.status) {
    case "idle": {
      // 비었거나 한 글자뿐인 입력은 검색하지 않고 최근 검색어(없으면 안내)로 돌아간다(#195-S10)
      if (state.text.trim() === "") return hasRecent ? "recent" : "guide";
      const checked = checkSearchText(state.text);
      if (checked.ok) return hasRecent ? "recent" : "guide";
      return checked.problem === "TOO_SHORT" ? (hasRecent ? "recent" : "guide") : "invalid";
    }
    case "loading":
      return state.result ? "results" : "searching";
    case "done":
      return state.result && state.result.places.length > 0 ? "results" : "empty";
    case "error":
      return "error";
  }
}

export type TextPart = { text: string; match: boolean };

// 입력한 글자가 이름·주소에 그대로 들어 있으면 그 글자를 강조한다(#195-S10). 대소문자는 무시하고, 띄어쓰기가 달라 찾은 경우는
// 그대로 들어 있지 않으므로 강조하지 않는다. 소문자로 바꾸면 길이가 달라지는 글자(`İ` 등)가 있으면 위치가 어긋나 강조하지 않는다.
export function highlightParts(text: string, query: string): TextPart[] {
  const needle = query.trim().toLowerCase();
  const lower = text.toLowerCase();
  if (needle === "" || lower.length !== text.length) return [{ text, match: false }];
  const parts: TextPart[] = [];
  let from = 0;
  for (let index = lower.indexOf(needle); index >= 0; index = lower.indexOf(needle, from)) {
    if (index > from) parts.push({ text: text.slice(from, index), match: false });
    parts.push({ text: text.slice(index, index + needle.length), match: true });
    from = index + needle.length;
  }
  if (parts.length === 0) return [{ text, match: false }];
  if (from < text.length) parts.push({ text: text.slice(from), match: false });
  return parts;
}

// 결과가 없을 때 눌러 볼 수 있는 다른 검색어(종류 이름) 제안(#195-S12)
export const SEARCH_SUGGESTIONS = ["동물병원", "동물미용", "동물보호센터"] as const;
