import type { Place } from "../types/Place.ts";

// 검색 결과를 눌러 고른 장소의 선택을 어떻게 다루는지(#195-S11). 화면 없이 테스트한다.

// 영역 조회 결과에 검색으로 고른 장소를 더한다. 그 장소가 지금 영역 밖이거나 종류 칩이 꺼져 있어도 지도에 그리고 카드를 열기 위해서다.
// 같은 장소가 이미 조회 결과에 있으면 조회 결과를 쓴다.
export function withPinned(places: Place[], pinned: Place | null): Place[] {
  if (!pinned || places.some((place) => place.id === pinned.id)) return places;
  return [...places, pinned];
}

// 새 영역을 조회한 뒤 선택을 이어 갈지. 새 목록에 있거나 검색으로 고른 장소이면 이어 가고(영역이 옮겨 가도 선택된 채 유지된다), 아니면 푼다.
export function keepSelectedId(
  listIds: readonly string[],
  selectedId: string | null,
  pinnedId: string | null,
): string | null {
  if (selectedId === null) return null;
  return listIds.includes(selectedId) || selectedId === pinnedId ? selectedId : null;
}

// 지도에서 장소를 누르거나 빈 곳을 눌러 선택이 바뀔 때, 검색으로 고른 장소와 검색어를 이어 둘지.
// 같은 장소를 다시 누르면 이어 두고, 다른 장소나 빈 곳이면 검색어가 지금 선택과 맞지 않으므로 지운다.
export function searchSelectionAfterTap(
  tappedId: string | null,
  pinnedId: string | null,
): "keep" | "clear" {
  return tappedId !== null && tappedId === pinnedId ? "keep" : "clear";
}
