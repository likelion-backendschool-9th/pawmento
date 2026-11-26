import test from "node:test";
import assert from "node:assert/strict";
import {
  areaReference,
  boundsAround,
  haversineMeters,
  myLocationReference,
  nearbyHeading,
  nearbyPlaces,
  nextReference,
  radiusLabel,
  widerRadius,
} from "../features/map/utils/nearby.ts";
import type { Place } from "../features/map/types/Place.ts";

const GANGNAM = { latitude: 37.4979, longitude: 127.0276 };

const place = (id: string, name: string, latitude: number, longitude: number): Place => ({
  id,
  category: "HOSPITAL",
  name,
  address: null,
  phone: null,
  latitude,
  longitude,
  open24h: false,
  open24hBasis: null,
  openingHours: null,
  closedDays: null,
});

// 위도 1도는 약 111.2km다. 기준점의 북쪽으로 미터만큼 떨어진 곳
const northOf = (point: { latitude: number; longitude: number }, meters: number) => ({
  latitude: point.latitude + (meters / 6_371_008.8) * (180 / Math.PI),
  longitude: point.longitude,
});

test("#226-S5 거리는 Haversine 직선거리다(같은 지점은 0, 북쪽으로 500m는 500m)", () => {
  assert.equal(haversineMeters(GANGNAM, GANGNAM), 0);
  const north = northOf(GANGNAM, 500);
  assert.ok(Math.abs(haversineMeters(GANGNAM, north) - 500) < 0.001);
  // 서울시청~강남역은 약 8.8km다
  const cityHall = { latitude: 37.5663, longitude: 126.9779 };
  const meters = haversineMeters(cityHall, GANGNAM);
  assert.ok(meters > 8500 && meters < 9200, `${meters}`);
  // 방향이 바뀌어도 같다
  assert.equal(haversineMeters(cityHall, GANGNAM), haversineMeters(GANGNAM, cityHall));
});

test("#226-S5 반경 안의 장소만 가까운 순으로 만들고 거리가 같으면 이름순이다", () => {
  const near = northOf(GANGNAM, 140);
  const far = northOf(GANGNAM, 900);
  const out = northOf(GANGNAM, 2000);
  const places = [
    place("1", "나 병원", far.latitude, far.longitude),
    place("2", "다 병원", near.latitude, near.longitude),
    place("3", "가 병원", near.latitude, near.longitude),
    place("4", "멀리 병원", out.latitude, out.longitude),
  ];
  const result = nearbyPlaces(places, GANGNAM, 1000);
  assert.deepEqual(
    result.map((item) => item.place.id),
    ["3", "2", "1"],
  );
  assert.ok(result.every((item) => item.meters <= 1000));
  // 같은 좌표라서 거리가 같다
  assert.equal(result[0].meters, result[1].meters);
});

test("#226-S6 반경 위에 정확히 걸친 장소는 안에 든 것으로 보고, 조금이라도 넘으면 뺀다", () => {
  const edge = northOf(GANGNAM, 500);
  const exact = haversineMeters(GANGNAM, edge);
  const places = [place("1", "경계", edge.latitude, edge.longitude)];
  assert.equal(nearbyPlaces(places, GANGNAM, exact).length, 1);
  assert.equal(nearbyPlaces(places, GANGNAM, exact - 0.01).length, 0);
});

test("#226-S6 좌표가 잘못된 장소는 목록에서 뺀다", () => {
  const places = [place("1", "깨진 좌표", Number.NaN, 127), place("2", "정상", GANGNAM.latitude, GANGNAM.longitude)];
  assert.deepEqual(
    nearbyPlaces(places, GANGNAM, 500).map((item) => item.place.id),
    ["2"],
  );
});

test("#226-S7 목록용 조회 영역은 기준점이 가운데이고 반경의 원을 모두 덮는다", () => {
  const bounds = boundsAround(GANGNAM, 3000);
  assert.ok(Math.abs((bounds.minLatitude + bounds.maxLatitude) / 2 - GANGNAM.latitude) < 1e-9);
  assert.ok(Math.abs((bounds.minLongitude + bounds.maxLongitude) / 2 - GANGNAM.longitude) < 1e-9);
  // 동·서·남·북 어느 쪽으로 3km 떨어진 지점도 영역 안이다
  const north = northOf(GANGNAM, 3000);
  assert.ok(north.latitude <= bounds.maxLatitude + 1e-9);
  const east = { latitude: GANGNAM.latitude, longitude: bounds.maxLongitude };
  assert.ok(haversineMeters(GANGNAM, east) >= 3000 - 1);
  assert.ok(bounds.minLatitude < bounds.maxLatitude && bounds.minLongitude < bounds.maxLongitude);
});

test("#226-S7 목록용 조회 영역은 극점 근처와 날짜 변경선 근처에서도 서버가 받는 범위를 벗어나지 않는다", () => {
  const polar = boundsAround({ latitude: 89.99, longitude: 179.99 }, 3000);
  for (const value of [polar.minLatitude, polar.maxLatitude]) assert.ok(Math.abs(value) <= 90);
  for (const value of [polar.minLongitude, polar.maxLongitude]) assert.ok(Math.abs(value) <= 180);
});

test("#226-S6 반경은 500m·1km·3km이고 한 단계씩 넓히며 가장 넓으면 더 넓히지 않는다", () => {
  assert.equal(widerRadius(500), 1000);
  assert.equal(widerRadius(1000), 3000);
  assert.equal(widerRadius(3000), null);
  assert.deepEqual([500, 1000, 3000].map((radius) => radiusLabel(radius as 500)), ["500m", "1km", "3km"]);
});

test("#226-S9 기준은 가장 최근에 정한 지점이고 지도를 손으로 옮기기만 하면 바뀌지 않는다", () => {
  const me = myLocationReference(GANGNAM);
  assert.equal(me.name, "내 위치");
  const station = areaReference({ latitude: 37.4979, longitude: 127.0276 }, " 강남역 ");
  assert.equal(station.name, "강남역");
  // 아직 정한 적이 없다
  assert.equal(nextReference(null, null), null);
  // 내 위치 → 강남역 → 내 위치: 가장 최근 것이 쓰인다
  assert.equal(nextReference(null, me), me);
  assert.equal(nextReference(me, station), station);
  assert.equal(nextReference(station, me), me);
  // 지도를 움직이기만 하면(새 기준 없음) 그대로다
  assert.equal(nextReference(station, null), station);
  // 이름이 비어 있으면 안내 문구를 쓴다
  assert.equal(areaReference(GANGNAM, "  ").name, "선택한 지점");
});

test("#226-S5 목록 위 문구는 기준에 따라 바뀌고, 기준이 없으면 반경과 거리 없이 이 지역 장소를 센다", () => {
  assert.deepEqual(nearbyHeading(myLocationReference(GANGNAM), 500, 6), {
    title: "내 주변 가까운 순",
    summary: "내 위치에서 반경 500m · 6곳",
  });
  assert.deepEqual(nearbyHeading(areaReference(GANGNAM, "강남역"), 1000, 0), {
    title: "강남역 주변 가까운 순",
    summary: "강남역에서 반경 1km · 0곳",
  });
  assert.deepEqual(nearbyHeading(null, 500, 12), { title: "이 지역 장소", summary: "12곳" });
});
