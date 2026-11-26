// 검색어를 서버와 같은 기준으로 가늠한다(#195-S6, S10). 서버가 판단의 기준이고, 여기서는 쓸모없는 요청을 보내지 않으려고
// 미리 거를 뿐이다. 규칙은 서버의 PlaceSearch.normalize와 PlaceSelection.comparableName이다: 앞뒤 공백을 지우고, 소문자로 바꾸고,
// 띄어쓰기·괄호·구두점을 지운 값의 길이가 2~30자이며 제어 문자가 없어야 한다.
export const MIN_SEARCH_LENGTH = 2;
export const MAX_SEARCH_LENGTH = 30;

const IGNORED_IN_NAME = /[\s()[\]{}·\-_.,'"]/g;
// Java의 Character.isISOControl과 같은 범위(U+0000~001F, U+007F~009F)
const CONTROL = /[\u0000-\u001f\u007f-\u009f]/;

export type SearchTextProblem = "TOO_SHORT" | "TOO_LONG" | "INVALID_CHARACTER";

export type SearchTextCheck =
  | { ok: true; text: string }
  | { ok: false; problem: SearchTextProblem };

// 서버가 응답에 담는 이유와 같은 문구다
export const SEARCH_TEXT_MESSAGES: Record<SearchTextProblem, string> = {
  TOO_SHORT: `검색어는 ${MIN_SEARCH_LENGTH}자 이상 입력해 주세요.`,
  TOO_LONG: `검색어는 ${MAX_SEARCH_LENGTH}자 이하로 입력해 주세요.`,
  INVALID_CHARACTER: "검색어에 쓸 수 없는 글자가 있어요.",
};

// 띄어쓰기·괄호·구두점·대소문자를 무시한 비교용 값. 최근 검색어의 중복을 가릴 때도 쓴다.
export function comparableSearchText(text: string): string {
  return text.trim().toLowerCase().replace(IGNORED_IN_NAME, "");
}

// 서버로 보낼 검색어(앞뒤 공백을 지운 입력)를 돌려주거나, 보낼 수 없는 이유를 돌려준다.
export function checkSearchText(input: string): SearchTextCheck {
  const key = comparableSearchText(input);
  if (CONTROL.test(key)) return { ok: false, problem: "INVALID_CHARACTER" };
  const length = Array.from(key).length;
  if (length < MIN_SEARCH_LENGTH) return { ok: false, problem: "TOO_SHORT" };
  if (length > MAX_SEARCH_LENGTH) return { ok: false, problem: "TOO_LONG" };
  return { ok: true, text: input.trim() };
}
