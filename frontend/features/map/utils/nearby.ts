import type { MapBounds, MapPoint } from "../types/MapBounds.ts";
import type { Place } from "../types/Place.ts";
import { clampBounds } from "./mapBounds.ts";

// 내 주변 가까운 순 목록의 규칙(#226). 화면 없이 테스트한다. 서버는 바꾸지 않고 영역 조회 결과를 화면에서 거르고 정렬한다.

// 반경은 500m(처음), 1km, 3km다(#226-S6)
export const NEARBY_RADII = [500, 1000, 3000] as const;
export type NearbyRadius = (typeof NEARBY_RADII)[number];
export const DEFAULT_NEARBY_RADIUS: NearbyRadius = 500;

const EARTH_RADIUS_METERS = 6_371_008.8;
const toRadians = (degrees: number) => (degrees * Math.PI) / 180;
const toDegrees = (radians: number) => (radians * 180) / Math.PI;

// 두 지점 사이의 지도 위 직선거리(m). Haversine이다. 도로를 따라 걷는 거리가 아니다(#226-S5)
export function haversineMeters(from: MapPoint, to: MapPoint): number {
  const dLat = toRadians(to.latitude - from.latitude);
  const dLng = toRadians(to.longitude - from.longitude);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRadians(from.latitude)) * Math.cos(toRadians(to.latitude)) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(a)));
}

// 기준점을 가운데로 하고 한 변이 반경의 두 배인 사각형 영역. 목록용 조회에 쓴다.
// 이 사각형은 반경(원)을 모두 덮는다. 그래서 원 밖의 장소가 섞일 수 있고, 화면에서 반경으로 한 번 더 거른다(#226-S7)
export function boundsAround(center: MapPoint, radiusMeters: number): MapBounds {
  const dLat = toDegrees(radiusMeters / EARTH_RADIUS_METERS);
  // 위도가 높을수록 같은 거리가 경도로는 더 넓다. 극점 근처에서 0으로 나누지 않게 한다
  const dLng = dLat / Math.max(Math.cos(toRadians(center.latitude)), 0.01);
  return clampBounds({
    minLatitude: center.latitude - dLat,
    minLongitude: center.longitude - dLng,
    maxLatitude: center.latitude + dLat,
    maxLongitude: center.longitude + dLng,
  });
}

export type NearbyPlace = { place: Place; meters: number };

// 기준점에서 반경 안의 장소를 가까운 순으로 만든다. 거리가 같으면 이름순이다(#226-S5).
// 반경 위에 정확히 걸친 장소는 안에 든 것으로 본다. 좌표가 잘못된 장소는 뺀다.
export function nearbyPlaces(
  places: readonly Place[],
  origin: MapPoint,
  radiusMeters: number,
): NearbyPlace[] {
  return places
    .map((place) => ({ place, meters: haversineMeters(origin, place) }))
    .filter((item) => Number.isFinite(item.meters) && item.meters <= radiusMeters)
    .sort((a, b) => a.meters - b.meters || a.place.name.localeCompare(b.place.name, "ko"));
}

// 반경을 한 단계 넓힌다. 이미 가장 넓으면 null이다("1km로 넓혀 보기", #226-S6)
export function widerRadius(radius: NearbyRadius): NearbyRadius | null {
  const index = NEARBY_RADII.indexOf(radius);
  return index >= 0 && index < NEARBY_RADII.length - 1 ? NEARBY_RADII[index + 1] : null;
}

// 반경 글자: 500m, 1km, 3km
export function radiusLabel(radius: NearbyRadius): string {
  return radius < 1000 ? `${radius}m` : `${radius / 1000}km`;
}

// 목록의 기준은 가장 최근에 정한 지점이다(#226-S9). 내 위치를 누르면 "내 위치", 검색에서 지역·역을 고르면 그 이름이다.
export type NearbyReference = { kind: "me" | "area"; point: MapPoint; name: string };

export const myLocationReference = (point: MapPoint): NearbyReference => ({
  kind: "me",
  point,
  name: "내 위치",
});

export const areaReference = (point: MapPoint, name: string): NearbyReference => ({
  kind: "area",
  point,
  name: name.trim() || "선택한 지점",
});

// 지도를 손으로 옮기기만 하면 기준은 바뀌지 않는다. 바꾸는 동작은 "내 위치"와 지역·역 선택뿐이라, 새 기준을 받았을 때만 바꾼다
export function nextReference(
  current: NearbyReference | null,
  next: NearbyReference | null,
): NearbyReference | null {
  return next ?? current;
}

// 목록 위 문구(#226-S5, S9). 기준이 없으면 지도 화면의 영역 조회 결과를 보이는 것이라 반경이 없고 거리도 없다
export function nearbyHeading(
  reference: NearbyReference | null,
  radius: NearbyRadius,
  count: number,
): { title: string; summary: string } {
  if (!reference) return { title: "이 지역 장소", summary: `${count}곳` };
  return {
    title: reference.kind === "me" ? "내 주변 가까운 순" : `${reference.name} 주변 가까운 순`,
    summary: `${reference.name}에서 반경 ${radiusLabel(radius)} · ${count}곳`,
  };
}
