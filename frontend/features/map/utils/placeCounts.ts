import {
  PLACE_CATEGORIES,
  type Place,
  type PlaceCategory,
} from "../types/Place.ts";

// 영역 안의 장소를 종류별로 센다. 종류 칩의 숫자다. 목록에 없는 종류는 0이다.
export function countByCategory(
  places: readonly Place[],
): Record<PlaceCategory, number> {
  const counts = Object.fromEntries(
    PLACE_CATEGORIES.map((category) => [category, 0]),
  ) as Record<PlaceCategory, number>;
  for (const place of places) counts[place.category] += 1;
  return counts;
}

// 칩을 눌러 종류를 켜고 끈다. 켜진 종류의 순서는 PLACE_CATEGORIES를 따라 항상 같다.
export function toggleCategory(
  selected: readonly PlaceCategory[],
  category: PlaceCategory,
): PlaceCategory[] {
  const next = new Set(selected);
  if (next.has(category)) next.delete(category);
  else next.add(category);
  return PLACE_CATEGORIES.filter((item) => next.has(item));
}
