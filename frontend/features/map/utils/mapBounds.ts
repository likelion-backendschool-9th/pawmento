import type { MapBounds, MapPoint } from "../types/MapBounds.ts";

const MAX_LATITUDE = 90;
const MAX_LONGITUDE = 180;

const clamp = (value: number, limit: number) =>
  Math.min(limit, Math.max(-limit, value));

// 지도를 아주 멀리 줄이면 SDK가 범위를 넘는 값을 줄 수 있어서 서버가 받는 범위로 맞춘다.
export function clampBounds(bounds: MapBounds): MapBounds {
  return {
    minLatitude: clamp(bounds.minLatitude, MAX_LATITUDE),
    minLongitude: clamp(bounds.minLongitude, MAX_LONGITUDE),
    maxLatitude: clamp(bounds.maxLatitude, MAX_LATITUDE),
    maxLongitude: clamp(bounds.maxLongitude, MAX_LONGITUDE),
  };
}

export function isValidBounds(bounds: MapBounds): boolean {
  const values = Object.values(bounds);
  return (
    values.every(Number.isFinite) &&
    Math.abs(bounds.minLatitude) <= MAX_LATITUDE &&
    Math.abs(bounds.maxLatitude) <= MAX_LATITUDE &&
    Math.abs(bounds.minLongitude) <= MAX_LONGITUDE &&
    Math.abs(bounds.maxLongitude) <= MAX_LONGITUDE &&
    bounds.minLatitude <= bounds.maxLatitude &&
    bounds.minLongitude <= bounds.maxLongitude
  );
}

export function containsPoint(bounds: MapBounds, point: MapPoint): boolean {
  return (
    point.latitude >= bounds.minLatitude &&
    point.latitude <= bounds.maxLatitude &&
    point.longitude >= bounds.minLongitude &&
    point.longitude <= bounds.maxLongitude
  );
}

// 좁은 영역에서 위도·경도를 화면 비율(0~1)로 바꾼다. 북쪽이 위쪽이다.
export function toViewportRatio(bounds: MapBounds, point: MapPoint) {
  const width = bounds.maxLongitude - bounds.minLongitude;
  const height = bounds.maxLatitude - bounds.minLatitude;
  return {
    x: width === 0 ? 0.5 : (point.longitude - bounds.minLongitude) / width,
    y: height === 0 ? 0.5 : (bounds.maxLatitude - point.latitude) / height,
  };
}
