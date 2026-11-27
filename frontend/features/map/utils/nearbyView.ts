import type { Place } from "../types/Place.ts";
import type { NearbyListState } from "./nearbyListController.ts";
import {
  nearbyHeading,
  nearbyPlaces,
  radiusLabel,
  widerRadius,
  type NearbyRadius,
  type NearbyReference,
} from "./nearby.ts";
import { distanceLabel } from "./placeDistance.ts";

// 가까운 순 목록 시트에 무엇을 보일지 정하는 규칙(#226-S5~S9). 화면 없이 테스트한다.

export type NearbyRow = { place: Place; distance: string };

export type SheetBody =
  | { kind: "loading" }
  | { kind: "error"; message: string }
  | { kind: "nothingChosen"; text: string }
  | { kind: "rows"; rows: NearbyRow[]; note: string | null }
  // 반경 안에 없다: 한 단계 넓혀 보기를 권한다
  | { kind: "widen"; text: string; next: NearbyRadius; button: string; note: string | null }
  // 가장 넓은 반경에도 없다, 또는 기준이 없는 지도 영역에 없다
  | { kind: "none"; text: string; note: string | null };

export type SheetView = { title: string; summary: string; body: SheetBody };

const NOTHING_CHOSEN = "보고 싶은 종류를 골라 주세요";

// 기준(내 위치 등)이 있을 때: 기준점을 가운데로 한 조회 결과를 반경 안으로 거르고 가까운 순으로 보인다
export function nearbySheetView(
  list: NearbyListState,
  reference: NearbyReference,
  radius: NearbyRadius,
  nothingChosen: boolean,
): SheetView {
  const heading = (count: number) => nearbyHeading(reference, radius, count);
  if (nothingChosen) return { ...heading(0), body: { kind: "nothingChosen", text: NOTHING_CHOSEN } };
  if (list.status === "idle" || list.status === "loading") {
    return { title: heading(0).title, summary: "찾는 중이에요", body: { kind: "loading" } };
  }
  if (list.status === "error") {
    return { title: heading(0).title, summary: "불러오지 못했어요", body: { kind: "error", message: list.message } };
  }
  // 영역은 사각형이라 반경(원) 밖의 장소가 섞인다. 화면에서 반경으로 한 번 더 거른다(#226-S7)
  const rows = nearbyPlaces(list.places, reference.point, radius).map((item) => ({
    place: item.place,
    distance: distanceLabel(item.distanceMeters),
  }));
  if (rows.length === 0) {
    // 서버가 조회 결과를 잘랐다면 반경 안이 비어 보여도 "없다"고 단정하지 않는다
    const note = list.truncated ? "조회된 장소가 많아 일부만 확인했어요" : null;
    const next = widerRadius(radius);
    return {
      ...heading(0),
      body:
        next === null
          ? { kind: "none", text: "내 주변에는 아직 없어요. 지도를 옮겨 찾아보세요", note }
          : { kind: "widen", text: `반경 ${radiusLabel(radius)} 안에는 없어요`, next, button: `${radiusLabel(next)}로 넓혀 보기`, note },
    };
  }
  const note = list.truncated
    ? radius > 500
      ? "더 있어요. 반경을 줄여 보세요"
      : "더 있어요. 가까운 곳부터 보여 드려요"
    : null;
  return { ...heading(rows.length), body: { kind: "rows", rows, note } };
}

// 기준이 아직 없을 때: 지도 화면 영역의 조회 결과를 거리 없이 보인다(#226-S9)
export function areaSheetView(input: {
  places: readonly Place[];
  loading: boolean;
  error: string;
  truncated: boolean;
  nothingChosen: boolean;
}): SheetView {
  const heading = (count: number) => nearbyHeading(null, 500, count);
  if (input.nothingChosen) return { ...heading(0), body: { kind: "nothingChosen", text: NOTHING_CHOSEN } };
  if (input.error) return { title: heading(0).title, summary: "불러오지 못했어요", body: { kind: "error", message: input.error } };
  if (input.places.length === 0) {
    if (input.loading) return { title: heading(0).title, summary: "찾는 중이에요", body: { kind: "loading" } };
    return { ...heading(0), body: { kind: "none", text: "이 지역에는 장소가 없어요. 지도를 옮기거나 다른 종류를 켜 보세요", note: input.truncated ? "조회된 장소가 많아 일부만 확인했어요" : null } };
  }
  return {
    ...heading(input.places.length),
    body: {
      kind: "rows",
      rows: input.places.map((place) => ({ place, distance: "" })),
      note: input.truncated ? "장소가 많아서 일부만 보여요. 지도를 확대해 보세요" : null,
    },
  };
}
