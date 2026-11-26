import test from "node:test";
import assert from "node:assert/strict";
import { ApplicationError } from "../lib/application/ApplicationError.ts";
import type { ApiClient } from "../lib/http/apiClient.ts";
import {
  checkedPlaceCategories,
  createPlaceApi,
} from "../features/map/api/placeApi.ts";
import type { MapBounds } from "../features/map/types/MapBounds.ts";
import type { PlaceCategory } from "../features/map/types/Place.ts";
import { readFileSync } from "node:fs";
import { selectPlaceRepository } from "../features/map/placeSource.ts";
import { DemoPlaceRepository } from "../mocks/handlers/DemoPlaceRepository.ts";
import { DEMO_PLACES } from "../mocks/fixtures/demoPlaces.ts";
import { isDevelopmentMockRequested } from "../mocks/setup.ts";

const SEOUL_FOREST: MapBounds = {
  minLatitude: 37.5407,
  minLongitude: 127.0319,
  maxLatitude: 37.5482,
  maxLongitude: 127.0429,
};

function fakeApi(response: unknown, seen: string[] = []): ApiClient {
  return {
    get: async <T>(path: string) => {
      seen.push(path);
      return response as T;
    },
    post: async () => {
      throw Error("not used");
    },
    patch: async () => {
      throw Error("not used");
    },
    put: async () => {
      throw Error("not used");
    },
    delete: async () => {
      throw Error("not used");
    },
    postEmpty: async () => {
      throw Error("not used");
    },
  };
}

const validation = (error: unknown) =>
  error instanceof ApplicationError && error.code === "VALIDATION";

test("#172-S10 영역을 십진수 여섯 자리 문자열로 보내고, 종류를 보내지 않으면 세 종류 모두 받는다", async () => {
  const seen: string[] = [];
  const api = createPlaceApi(fakeApi({ places: [], truncated: false }, seen));

  await api.list(SEOUL_FOREST);

  assert.deepEqual(seen, [
    "/maps/places?minLatitude=37.540700&minLongitude=127.031900&maxLatitude=37.548200&maxLongitude=127.042900",
  ]);
});

test("#172-S10 장소의 값을 그대로 옮기고 id는 문자열이며, 없는 값은 null(정보 없음)로 둔다", async () => {
  const api = createPlaceApi(
    fakeApi({
      places: [
        {
          id: 7,
          category: "HOSPITAL",
          name: "대학로동물병원",
          address: "서울특별시 종로구 대학로 1",
          phone: "02-733-3233",
          latitude: 37.5811,
          longitude: 127.0021,
          open24h: true,
          open24hBasis: "NAME",
          openingHours: null,
          closedDays: null,
        },
        {
          id: 8,
          category: "SHELTER",
          name: "보호센터",
          address: null,
          phone: null,
          latitude: 37.5,
          longitude: 127,
          open24h: false,
          open24hBasis: null,
          openingHours: "09:00~18:00",
          closedDays: "토요일+일요일",
        },
      ],
      truncated: false,
    }),
  );

  const result = await api.list(SEOUL_FOREST);

  assert.equal(result.sample, false);
  assert.equal(result.truncated, false);
  assert.equal(result.places[0].id, "7");
  assert.equal(result.places[0].open24hBasis, "NAME");
  assert.equal(result.places[1].address, null);
  assert.equal(result.places[1].phone, null);
  assert.equal(result.places[1].closedDays, "토요일+일요일");
});

test("#172-S11 고른 종류만 보내고, 같은 종류는 한 번만 보낸다", async () => {
  const seen: string[] = [];
  const api = createPlaceApi(fakeApi({ places: [], truncated: false }, seen));

  await api.list(SEOUL_FOREST, {
    categories: ["HOSPITAL", "SHELTER", "HOSPITAL"],
  });

  assert.equal(seen.length, 1);
  assert.ok(seen[0].endsWith("&categories=HOSPITAL,SHELTER"));
});

test("#172-S11 고른 종류가 없으면 서버에 묻지 않고 빈 목록이다(서버는 빈 categories를 400으로 거절하고, 키를 생략해야 세 종류 모두다)", async () => {
  const seen: string[] = [];
  const api = createPlaceApi(fakeApi({ places: [], truncated: false }, seen));

  const result = await api.list(SEOUL_FOREST, { categories: [] });

  assert.deepEqual(result, {
    places: [],
    truncated: false,
    dataAsOf: null,
    sample: false,
  });
  assert.equal(seen.length, 0);
});

test("#172-S21 서버가 준 가져온 시각을 그대로 전하고, 없거나 문자열이 아니면 null이다", async () => {
  const withTime = createPlaceApi(
    fakeApi({ places: [], truncated: false, dataAsOf: "2026-11-21T13:00:00Z" }),
  );
  assert.equal((await withTime.list(SEOUL_FOREST)).dataAsOf, "2026-11-21T13:00:00Z");

  const nullTime = createPlaceApi(
    fakeApi({ places: [], truncated: false, dataAsOf: null }),
  );
  assert.equal((await nullTime.list(SEOUL_FOREST)).dataAsOf, null);

  // 서버가 아직 이 값을 주지 않는 배포여도 목록은 그대로 보인다
  const missing = createPlaceApi(fakeApi({ places: [], truncated: false }));
  assert.equal((await missing.list(SEOUL_FOREST)).dataAsOf, null);
});

test("#172-S12 500개를 넘어 잘렸으면 truncated를 그대로 알려 준다", async () => {
  const api = createPlaceApi(fakeApi({ places: [], truncated: true }));

  assert.equal((await api.list(SEOUL_FOREST)).truncated, true);
});

test("#172-S13 잘못된 영역이나 모르는 종류는 서버에 묻지 않고 VALIDATION이다", async () => {
  const seen: string[] = [];
  const api = createPlaceApi(fakeApi({ places: [], truncated: false }, seen));

  for (const bad of [
    { ...SEOUL_FOREST, minLatitude: 38, maxLatitude: 37 },
    { ...SEOUL_FOREST, minLongitude: 128, maxLongitude: 127 },
    { ...SEOUL_FOREST, maxLatitude: Number.NaN },
  ]) {
    await assert.rejects(() => api.list(bad), validation);
  }
  await assert.rejects(
    () => api.list(SEOUL_FOREST, { categories: ["CAFE" as PlaceCategory] }),
    validation,
  );
  assert.throws(
    () => checkedPlaceCategories(["PHARMACY" as PlaceCategory]),
    validation,
  );
  assert.equal(seen.length, 0, "잘못된 요청은 서버에 묻지 않는다");
});

test("#172-S13 무한대나 거꾸로 된 영역은 범위를 맞추기 전에 거절해서 서버에 전 세계 조회가 나가지 않는다", async () => {
  const seen: string[] = [];
  const api = createPlaceApi(fakeApi({ places: [], truncated: false }, seen));

  for (const bad of [
    // ±무한대는 범위를 맞추면 전 세계가 되어 정상으로 보인다
    {
      minLatitude: -Infinity,
      minLongitude: -Infinity,
      maxLatitude: Infinity,
      maxLongitude: Infinity,
    },
    // 위도 95~94는 거꾸로인데, 둘 다 90으로 맞춰지면 정상으로 보인다
    { ...SEOUL_FOREST, minLatitude: 95, maxLatitude: 94 },
    { ...SEOUL_FOREST, minLongitude: 190, maxLongitude: 185 },
  ]) {
    await assert.rejects(() => api.list(bad), validation);
  }
  assert.equal(seen.length, 0, "잘못된 영역은 서버에 묻지 않는다");
});

test("#172-S13 지도를 아주 멀리 줄여 SDK가 범위를 넘는 값을 주면 서버가 받는 범위로 맞춘다", async () => {
  const seen: string[] = [];
  const api = createPlaceApi(fakeApi({ places: [], truncated: false }, seen));

  await api.list({
    minLatitude: -95,
    minLongitude: -200,
    maxLatitude: 95,
    maxLongitude: 200,
  });

  assert.ok(
    seen[0].includes("minLatitude=-90.000000&minLongitude=-180.000000"),
  );
});

test("#172-S19 예시 장소는 서울숲 근처 영역 안에서만, 고른 종류만 보이고 예시라고 알린다", async () => {
  const demo = new DemoPlaceRepository();

  const all = await demo.list(SEOUL_FOREST);
  assert.equal(all.sample, true);
  assert.equal(all.truncated, false);
  assert.equal(all.places.length, DEMO_PLACES.length);

  const shelters = await demo.list(SEOUL_FOREST, { categories: ["SHELTER"] });
  assert.ok(shelters.places.length > 0);
  assert.ok(shelters.places.every((place) => place.category === "SHELTER"));

  const none = await demo.list(SEOUL_FOREST, { categories: [] });
  assert.equal(none.places.length, 0);

  const far = await demo.list({
    minLatitude: 35,
    minLongitude: 129,
    maxLatitude: 35.1,
    maxLongitude: 129.1,
  });
  assert.equal(far.places.length, 0);
});

test("#172-S19 예시 장소는 이름에 (예시)가 붙고, 카드에서 구분해야 하는 경우를 한 번씩 담는다", () => {
  assert.ok(DEMO_PLACES.every((place) => place.name.startsWith("(예시)")));
  assert.ok(DEMO_PLACES.some((place) => place.phone === null), "전화 없음");
  assert.ok(DEMO_PLACES.some((place) => place.phone !== null), "전화 있음");
  assert.ok(
    DEMO_PLACES.some((place) => place.open24hBasis === "NAME"),
    "이름 기준 24시",
  );
  assert.ok(
    DEMO_PLACES.some((place) => place.open24hBasis === "HOURS"),
    "24시간 운영",
  );
  assert.ok(
    DEMO_PLACES.some(
      (place) => place.category === "SHELTER" && place.closedDays === null,
    ),
    "휴무일 정보 없음",
  );
  assert.ok(
    DEMO_PLACES.some((place) => place.address?.includes("***")),
    "가려진 주소",
  );
  const ids = new Set(DEMO_PLACES.map((place) => place.id));
  assert.equal(ids.size, DEMO_PLACES.length, "id는 겹치지 않는다");
});

test("#172-S19 목업을 켜지 않으면 서버를 부르고 서버가 안 돼도 예시로 대신 보이지 않으며, 켜면 서버를 부르지 않고 예시를 준다", async () => {
  const seen: string[] = [];
  const failing: ApiClient = {
    ...fakeApi({}),
    get: async (path: string): Promise<never> => {
      seen.push(path);
      throw new ApplicationError("STORAGE_UNAVAILABLE", "서버를 만날 수 없어요.");
    },
  };
  const server = createPlaceApi(failing);
  const demo = new DemoPlaceRepository();

  const real = selectPlaceRepository({ mockRequested: false, server, demo });
  await assert.rejects(
    () => real.list(SEOUL_FOREST),
    (error: unknown) =>
      error instanceof ApplicationError && error.code === "STORAGE_UNAVAILABLE",
  );
  assert.equal(seen.length, 1, "목업을 켜지 않으면 서버를 부른다");

  const sample = selectPlaceRepository({ mockRequested: true, server, demo });
  const result = await sample.list(SEOUL_FOREST);
  assert.equal(result.sample, true);
  assert.equal(seen.length, 1, "목업을 켜면 서버를 부르지 않는다");
});

test("#172-S19 composition은 목업 플래그를 장소 저장소 선택에 실제로 넘기고, 목업은 명시적으로 켰을 때만 쓴다", () => {
  const source = readFileSync(
    new URL("../app/composition.ts", import.meta.url),
    "utf8",
  );
  assert.match(source, /selectPlaceRepository\(\{/);
  assert.match(source, /mockRequested:\s*isDevelopmentMockRequested\(\)/);
  assert.match(source, /server:\s*createPlaceApi\(http\)/);
  assert.match(source, /demo:\s*new DemoPlaceRepository\(\)/);
  // 목업은 명시적으로 켰을 때만 쓴다
  assert.equal(isDevelopmentMockRequested(true), true);
  assert.equal(isDevelopmentMockRequested(false), false);
});

