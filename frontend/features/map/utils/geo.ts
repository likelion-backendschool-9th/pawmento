import type { MapPoint } from "../types/MapBounds.ts";

// 지도 위 두 점 사이의 직선거리를 구하는 공용 계산(하버사인). 산책 거리(walk.ts)와 내 주변 목록(nearby.ts)이 함께 쓴다.
// 지구 반경은 서버(PlaceSelection)와 같은 6,371,000m다. 서버가 주는 검색 결과의 거리와 화면이 계산한 거리가 어긋나지 않게 하려는 것이다.
export const EARTH_RADIUS_METERS = 6_371_000;

export const toRadians = (degrees: number) => (degrees * Math.PI) / 180;
export const toDegrees = (radians: number) => (radians * 180) / Math.PI;

// 두 점 사이의 거리(미터)
export function haversineMeters(from: MapPoint, to: MapPoint): number {
  const dLat = toRadians(to.latitude - from.latitude);
  const dLng = toRadians(to.longitude - from.longitude);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRadians(from.latitude)) * Math.cos(toRadians(to.latitude)) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(a)));
}

// 서버로 보내는 좌표의 소수점 자릿수. 지도 영역과 검색 기준 좌표가 모두 이 자리까지 반올림되어 간다(placeApi의 toFixed).
// 서버는 보낸 좌표로 거리를 구하므로, 화면이 같은 거리를 보이려면 같은 자리로 반올림한 좌표로 계산해야 한다(#226).
export const REQUEST_COORDINATE_DIGITS = 6;

// 서버가 받게 될 좌표(소수점 여섯 자리로 반올림)
export function asRequestPoint(point: MapPoint): MapPoint {
  return {
    latitude: Number(point.latitude.toFixed(REQUEST_COORDINATE_DIGITS)),
    longitude: Number(point.longitude.toFixed(REQUEST_COORDINATE_DIGITS)),
  };
}
