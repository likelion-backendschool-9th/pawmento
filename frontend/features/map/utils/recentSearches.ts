import { checkSearchText, comparableSearchText } from "./placeSearchText.ts";

// 최근 검색어(#195-S14). 이 기기의 브라우저에만 남기고 서버에 보내지 않는다. 계정마다 따로 저장해 같은 기기를 다른 사람이
// 써도 섞이지 않는다. 저장소를 쓸 수 없으면(사생활 보호 모드 등) 오류 없이 이번 방문 동안만 기억한다.
export const RECENT_SEARCH_LIMIT = 10;
// 저장된 값 하나의 길이 상한. 정규화하면 30자 이하인 검색어가 구두점을 포함해도 넘기 어려운 값이다
const MAX_STORED_LENGTH = 100;

type StorageLike = Pick<Storage, "getItem" | "setItem" | "removeItem">;

export const recentSearchKey = (owner: string) =>
  `pawmento.recentSearches.v1.${encodeURIComponent(owner)}`;

// 저장된 값을 목록으로 읽는다. JSON이 깨졌거나 문자열 배열이 아니면(형식이 다르면) 빈 목록으로 시작한다.
export function parseRecentSearches(raw: string | null): string[] {
  if (!raw) return [];
  try {
    const value: unknown = JSON.parse(raw);
    if (
      !Array.isArray(value) ||
      !value.every((item) => typeof item === "string" && item.trim() !== "" && item.length <= MAX_STORED_LENGTH)
    ) {
      return [];
    }
    return value.slice(0, RECENT_SEARCH_LIMIT);
  } catch {
    return [];
  }
}

// 맨 앞에 더한다. 같은 검색어(띄어쓰기·대소문자를 무시하고 같으면)는 중복 없이 맨 앞으로 옮기고, 10개를 넘으면 가장 오래된 것부터 지운다.
// 검색할 수 없는 글자(너무 짧음 등)는 저장하지 않는다.
export function addRecentSearch(list: readonly string[], text: string): string[] {
  const checked = checkSearchText(text);
  if (!checked.ok) return [...list];
  const key = comparableSearchText(checked.text);
  return [checked.text, ...list.filter((item) => comparableSearchText(item) !== key)].slice(
    0,
    RECENT_SEARCH_LIMIT,
  );
}

export function removeRecentSearch(list: readonly string[], text: string): string[] {
  const key = comparableSearchText(text);
  return list.filter((item) => comparableSearchText(item) !== key);
}

// 저장소는 쓸 때마다 가져온다(브라우저 전역을 이 파일에서 직접 쓰지 않는다). 저장소가 없거나 읽기·쓰기가 실패하면
// 이 기억장치 안의 값으로 이어 간다.
export function createRecentSearches(getStorage: () => StorageLike | null) {
  const memory = new Map<string, string[]>();
  // 저장소에 쓰지 못한 계정. 그 뒤로는 저장소의 옛 값이 아니라 이번 방문의 값을 쓴다
  const volatile = new Set<string>();

  function load(owner: string): string[] {
    if (volatile.has(owner)) return memory.get(owner) ?? [];
    try {
      const storage = getStorage();
      if (storage) {
        const list = parseRecentSearches(storage.getItem(recentSearchKey(owner)));
        memory.set(owner, list);
        return list;
      }
    } catch {
      /* 저장소를 읽지 못하면 이번 방문의 값으로 이어 간다 */
    }
    return memory.get(owner) ?? [];
  }

  function save(owner: string, list: string[]): string[] {
    memory.set(owner, list);
    try {
      const storage = getStorage();
      if (!storage) {
        volatile.add(owner);
      } else if (list.length === 0) {
        storage.removeItem(recentSearchKey(owner));
      } else {
        storage.setItem(recentSearchKey(owner), JSON.stringify(list));
      }
    } catch {
      volatile.add(owner);
    }
    return list;
  }

  return {
    load,
    add: (owner: string, text: string) => save(owner, addRecentSearch(load(owner), text)),
    remove: (owner: string, text: string) => save(owner, removeRecentSearch(load(owner), text)),
    clear: (owner: string) => save(owner, []),
  };
}
