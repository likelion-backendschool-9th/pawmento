import test from "node:test";
import assert from "node:assert/strict";
import {
  areaReference,
  boundsAround,
  myLocationReference,
  nearbyHeading,
  nearbyPlaces,
  nextReference,
  radiusLabel,
  widerRadius,
} from "../features/map/utils/nearby.ts";
import { EARTH_RADIUS_METERS, haversineMeters, toDegrees, toRadians } from "../features/map/utils/geo.ts";
import { distanceBetween } from "../features/map/utils/walk.ts";
import { distanceLabel } from "../features/map/utils/placeDistance.ts";
import { createPlaceApi } from "../features/map/api/placeApi.ts";
import type { ApiClient } from "../lib/http/apiClient.ts";
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

// 기준점의 북쪽으로 미터만큼 떨어진 곳
const northOf = (point: { latitude: number; longitude: number }, meters: number) => ({
  latitude: point.latitude + toDegrees(meters / EARTH_RADIUS_METERS),
  longitude: point.longitude,
});

// 기준점에서 방위(도, 북쪽이 0)로 정확히 미터만큼 떨어진 곳(구면 위의 목적지)
function destination(from: { latitude: number; longitude: number }, bearingDegrees: number, meters: number) {
  const delta = meters / EARTH_RADIUS_METERS;
  const bearing = toRadians(bearingDegrees);
  const lat1 = toRadians(from.latitude);
  const lat2 = Math.asin(Math.sin(lat1) * Math.cos(delta) + Math.cos(lat1) * Math.sin(delta) * Math.cos(bearing));
  const lng2 =
    toRadians(from.longitude) +
    Math.atan2(Math.sin(bearing) * Math.sin(delta) * Math.cos(lat1), Math.cos(delta) - Math.sin(lat1) * Math.sin(lat2));
  return { latitude: toDegrees(lat2), longitude: toDegrees(lng2) };
}

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

test("#226-S5 거리는 서버·산책과 같은 지구 반경(6,371,000m)의 공용 계산이고, 산책의 거리 결과는 그대로다", () => {
  assert.equal(EARTH_RADIUS_METERS, 6_371_000);
  const cityHall = { latitude: 37.5663, longitude: 126.9779 };
  // 공용 계산과 산책의 distanceBetween은 같은 값을 준다(수식을 하나로 묶었다)
  assert.equal(distanceBetween(cityHall, GANGNAM), haversineMeters(cityHall, GANGNAM));
  // 북쪽으로 1km는 반경이 달라지면 어긋난다(6,371,008.8이면 999.9986m)
  assert.ok(Math.abs(haversineMeters(GANGNAM, northOf(GANGNAM, 1000)) - 1000) < 1e-6);
});

test("#226-S5 화면의 거리 글자는 검색과 같다: 정렬·반경은 실거리, 표시는 검색처럼 반올림한 정수 미터다", () => {
  // 같은 기준점에서 실거리 990.4m는 검색 API가 990으로 주므로 `990m`이어야 한다(실거리로 올림하면 `1.0km`가 된다)
  const cases: Array<[number, number, string]> = [
    [140.4, 140, "140m"],
    [140.6, 141, "150m"],
    [990.4, 990, "990m"],
    [990.6, 991, "1.0km"],
    [999.4, 999, "1.0km"],
    [1000.4, 1000, "1.0km"],
    [1049.4, 1049, "1.1km"],
  ];
  for (const [real, rounded, label] of cases) {
    const at = destination(GANGNAM, 20, real);
    const [item] = nearbyPlaces([place("1", "장소", at.latitude, at.longitude)], GANGNAM, 3000);
    assert.ok(Math.abs(item.meters - real) < 1e-6, `${real}: ${item.meters}`);
    assert.equal(item.distanceMeters, rounded, `${real}`);
    assert.equal(distanceLabel(item.distanceMeters), label, `${real}`);
    // 검색 API가 주는 값(서버 (int) Math.round → 클라이언트 Math.round)으로 만든 글자와 같다
    const searchApiMeters = Math.round(Math.round(haversineMeters(GANGNAM, at)));
    assert.equal(distanceLabel(item.distanceMeters), distanceLabel(searchApiMeters), `${real}`);
  }
});

test("#226-S6 반경 인자에 500을 그대로 넣어도, 부동소수점 오차로 계산값이 500을 살짝 넘는 경계의 장소는 안에 든다. 조금이라도 멀면 뺀다", () => {
  // 방위를 돌려 가며 정확히 500m에 놓은 장소를 만든다. 그중 계산값이 500보다 아주 조금(1e-6m 미만) 큰 것들이 경계 오차의 경우다
  let overByRounding = 0;
  for (let bearing = 0; bearing < 360; bearing += 1) {
    const edge = destination(GANGNAM, bearing, 500);
    const real = haversineMeters(GANGNAM, edge);
    const hit = nearbyPlaces([place("1", "경계", edge.latitude, edge.longitude)], GANGNAM, 500);
    assert.equal(hit.length, 1, `방위 ${bearing}: 계산값 ${real}`);
    if (real > 500) overByRounding += 1;
  }
  // 이 테스트가 오차의 경우를 실제로 다루는지(계산값이 500을 넘는 방위가 있다)
  assert.ok(overByRounding > 0, "경계 오차의 경우가 없어 테스트가 의미 없다");
  // 1cm 멀면 반경 밖이다
  const beyond = northOf(GANGNAM, 500.01);
  assert.equal(nearbyPlaces([place("2", "밖", beyond.latitude, beyond.longitude)], GANGNAM, 500).length, 0);
  // 1km·3km도 같다
  for (const radius of [1000, 3000]) {
    for (let bearing = 0; bearing < 360; bearing += 7) {
      const edge = destination(GANGNAM, bearing, radius);
      assert.equal(nearbyPlaces([place("3", "경계", edge.latitude, edge.longitude)], GANGNAM, radius).length, 1, `${radius} 방위 ${bearing}`);
    }
  }
});

// 요청 주소에서 영역 값을 읽는다(placeApi가 toFixed(6)으로 바꾼 값)
async function sentArea(area: ReturnType<typeof boundsAround>) {
  const seen: string[] = [];
  const http = {
    get: async <T>(path: string) => {
      seen.push(path);
      return { places: [], truncated: false } as T;
    },
  } as unknown as ApiClient;
  await createPlaceApi(http).list(area);
  const query = new URLSearchParams(seen[0].split("?")[1]);
  return {
    minLatitude: Number(query.get("minLatitude")),
    minLongitude: Number(query.get("minLongitude")),
    maxLatitude: Number(query.get("maxLatitude")),
    maxLongitude: Number(query.get("maxLongitude")),
  };
}

test("#226-S7 서버로 보낼 요청 좌표(소수점 여섯 자리)까지 바꾼 뒤에도 영역이 반경의 원을 모두 덮는다(국내 끝점 포함)", async () => {
  // 서울, 부산, 제주, 마라도(최남단), 독도(최동단), 백령도(서쪽)
  const centers = [
    GANGNAM,
    { latitude: 35.1796, longitude: 129.0756 },
    { latitude: 33.4996, longitude: 126.5312 },
    { latitude: 33.1146, longitude: 126.2689 },
    { latitude: 37.2415, longitude: 131.8645 },
    { latitude: 37.9647, longitude: 124.7137 },
  ];
  for (const center of centers) {
    for (const radius of [500, 1000, 3000]) {
      const sent = await sentArea(boundsAround(center, radius));
      const where = `${center.latitude},${center.longitude} r=${radius}`;
      for (let bearing = 0; bearing < 360; bearing += 5) {
        const edge = destination(center, bearing, radius);
        assert.ok(edge.latitude >= sent.minLatitude && edge.latitude <= sent.maxLatitude, `위도 ${where} 방위=${bearing}`);
        assert.ok(edge.longitude >= sent.minLongitude && edge.longitude <= sent.maxLongitude, `경도 ${where} 방위=${bearing}`);
      }
      // 가장 바깥(동서남북 끝)은 여섯 자리 반올림으로 안쪽으로 좁아지지 않는다
      const north = destination(center, 0, radius);
      const south = destination(center, 180, radius);
      assert.ok(north.latitude <= sent.maxLatitude && south.latitude >= sent.minLatitude);
      // 영역이 지나치게 크지도 않다(반경의 두 배보다 1m 넘게 크지 않다)
      const width = haversineMeters({ latitude: center.latitude, longitude: sent.minLongitude }, { latitude: center.latitude, longitude: sent.maxLongitude });
      assert.ok(width < radius * 2 + 1.5, `${where} 폭 ${width}`);
    }
  }
});

test("#226-S7 날짜변경선(경도 180도)을 넘는 영역은 서버가 받는 범위로 잘린다 — 국내가 아닌 곳의 알려진 한계다", () => {
  const area = boundsAround({ latitude: 0, longitude: 179.99 }, 3000);
  assert.ok(area.maxLongitude <= 180 && area.minLongitude >= -180);
  // 이 앱의 지역(경도 약 124~132도)에서는 잘리지 않는다
  const korea = boundsAround(GANGNAM, 3000);
  assert.ok(korea.minLongitude > 100 && korea.maxLongitude < 140);
});
