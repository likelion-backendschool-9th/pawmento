import test from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { ApplicationError } from "../src/shared/application/ApplicationError.ts";
import type { ApiClient } from "../src/shared/http/apiClient.ts";
import { createMapUseCases } from "../src/map/application/useCases.ts";
import type {
  MapPinList,
  MapPinRepository,
} from "../src/map/application/ports.ts";
import {
  clampBounds,
  containsPoint,
  isValidBounds,
  toViewportRatio,
  type MapBounds,
} from "../src/map/domain/MapBounds.ts";
import { HttpMapPinRepository } from "../src/map/adapters/outbound/HttpMapPinRepository.ts";
import { DemoMapPinRepository } from "../src/map/adapters/outbound/DemoMapPinRepository.ts";
import { PetScopedMapPinRepository } from "../src/map/adapters/outbound/PetScopedMapPinRepository.ts";
import { pinMarkup } from "../src/map/adapters/inbound/pinMarkup.ts";
import {
  isOpenableDiary,
  pinDetail,
} from "../src/map/adapters/inbound/pinDetail.ts";

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
    postEmpty: async () => {
      throw Error("not used");
    },
  };
}

test("#19-S5 영역은 서버가 받는 범위로 맞추고, 최솟값이 최댓값보다 크면 요청하지 않는다", async () => {
  let called = 0;
  const repository: MapPinRepository = {
    list: async (): Promise<MapPinList> => {
      called += 1;
      return { pins: [], truncated: false, sample: false };
    },
  };
  const map = createMapUseCases(repository);

  // 멀리 줄여서 지도 SDK가 범위를 넘는 값을 줄 때
  assert.deepEqual(
    clampBounds({ minLatitude: -95, minLongitude: -200, maxLatitude: 95, maxLongitude: 200 }),
    { minLatitude: -90, minLongitude: -180, maxLatitude: 90, maxLongitude: 180 },
  );
  await map.listPins("10", { minLatitude: -95, minLongitude: -200, maxLatitude: 95, maxLongitude: 200 });
  assert.equal(called, 1);

  for (const bad of [
    { ...SEOUL_FOREST, minLatitude: 38, maxLatitude: 37 },
    { ...SEOUL_FOREST, minLongitude: 128, maxLongitude: 127 },
    { ...SEOUL_FOREST, maxLatitude: Number.NaN },
  ]) {
    await assert.rejects(
      () => map.listPins("10", bad),
      (error: unknown) =>
        error instanceof ApplicationError && error.code === "VALIDATION",
    );
  }
  await assert.rejects(() => map.listPins(" ", SEOUL_FOREST), ApplicationError);
  assert.equal(called, 1, "잘못된 영역은 서버에 묻지 않는다");
  assert.equal(isValidBounds(SEOUL_FOREST), true);
});

test("#19-S5 서버 요청은 쿼리 문자열로만 보내고 영역 값은 지수 표기 없는 십진수다", async () => {
  const seen: string[] = [];
  const repository = new HttpMapPinRepository(
    fakeApi(
      {
        pins: [
          { diaryId: 7, latitude: 37.5462, longitude: 127.0352, placeName: "서울숲" },
          { diaryId: 8, latitude: 37.54, longitude: 127.03, placeName: null },
        ],
        truncated: false,
      },
      seen,
    ),
  );

  const list = await repository.list("10", SEOUL_FOREST);
  assert.equal(
    seen[0],
    "/maps/pins?petId=10&minLatitude=37.540700&minLongitude=127.031900&maxLatitude=37.548200&maxLongitude=127.042900",
  );
  assert.deepEqual(list.pins[0], {
    diaryId: "7",
    latitude: 37.5462,
    longitude: 127.0352,
    placeName: "서울숲",
  });
  assert.equal(list.pins[1].placeName, null);
  assert.equal(list.sample, false);

  // 적도 근처의 아주 작은 값도 1e-7이 아니라 십진수로 나간다
  await repository.list("10", {
    minLatitude: -1e-7,
    minLongitude: -1e-7,
    maxLatitude: 1e-7,
    maxLongitude: 1e-7,
  });
  assert.doesNotMatch(seen[1], /e[-+]?\d/i);
});

test("#19-S6 서버가 잘렸다고 알려 주면 그대로 전달한다", async () => {
  const repository = new HttpMapPinRepository(
    fakeApi({ pins: [], truncated: true }),
  );
  assert.equal((await repository.list("10", SEOUL_FOREST)).truncated, true);
});

test("#19-S9 서버 반려동물(양의 정수 id)만 서버에 묻고, 데모 반려동물은 예시 핀을 본다", async () => {
  const asked: string[] = [];
  const server: MapPinRepository = {
    list: async (petId) => {
      asked.push(petId);
      return { pins: [], truncated: false, sample: false };
    },
  };
  const repository = new PetScopedMapPinRepository(
    server,
    new DemoMapPinRepository(),
  );

  assert.equal((await repository.list("10", SEOUL_FOREST)).sample, false);
  const demo = await repository.list(
    "6f1c0a3e-3c52-4d8e-9d0e-2a2b4c8f9a10",
    SEOUL_FOREST,
  );
  assert.equal(demo.sample, true);
  assert.ok(demo.pins.length > 0);
  assert.deepEqual(asked, ["10"]);
  for (const id of ["0", "-1", "1.5", "01", "abc"]) {
    assert.equal((await repository.list(id, SEOUL_FOREST)).sample, true, id);
  }
});

test("#19-S5 예시 핀도 보이는 영역 안의 것만 돌려준다", async () => {
  const demo = new DemoMapPinRepository();
  const all = await demo.list("x", SEOUL_FOREST);
  const narrow = await demo.list("x", {
    ...SEOUL_FOREST,
    minLatitude: 37.5455,
  });
  assert.ok(narrow.pins.length < all.pins.length);
  for (const pin of narrow.pins) assert.equal(containsPoint({ ...SEOUL_FOREST, minLatitude: 37.5455 }, pin), true);
  const none = await demo.list("x", { minLatitude: 10, minLongitude: 10, maxLatitude: 11, maxLongitude: 11 });
  assert.deepEqual(none.pins, []);
});

test("#19-S9 예시 지도의 위치 비율은 북쪽이 위쪽이다", () => {
  const bounds: MapBounds = { minLatitude: 0, minLongitude: 0, maxLatitude: 10, maxLongitude: 20 };
  assert.deepEqual(toViewportRatio(bounds, { latitude: 10, longitude: 0 }), { x: 0, y: 0 });
  assert.deepEqual(toViewportRatio(bounds, { latitude: 0, longitude: 20 }), { x: 1, y: 1 });
  assert.deepEqual(toViewportRatio(bounds, { latitude: 5, longitude: 10 }), { x: 0.5, y: 0.5 });
  // 점 영역도 나누기 0이 되지 않는다
  assert.deepEqual(
    toViewportRatio({ minLatitude: 1, minLongitude: 1, maxLatitude: 1, maxLongitude: 1 }, { latitude: 1, longitude: 1 }),
    { x: 0.5, y: 0.5 },
  );
});

test("#19-S9 핀 모양은 일상은 발자국, 정원은 꽃이고 선택하면 후광이 생긴다", () => {
  const paw = pinMarkup("ACTIVE");
  const flower = pinMarkup("GARDEN");
  assert.match(paw, /rx="7.4"/);
  assert.doesNotMatch(paw, /rotate\(72\)/);
  assert.match(flower, /rotate\(72\)/);
  assert.doesNotMatch(flower, /rx="7.4"/);
  assert.match(paw, /var\(--brand-primary\)/);
  assert.doesNotMatch(paw, /opacity="\.16"/);
  assert.match(pinMarkup("ACTIVE", true), /opacity="\.16"/);
});

test("#19-R6 지도 비밀 키는 프론트엔드 코드에 두지 않는다", () => {
  function files(folder: string): string[] {
    return readdirSync(folder, { withFileTypes: true }).flatMap((item) => {
      const path = resolve(folder, item.name);
      return item.isDirectory() ? files(path) : /\.(tsx?|css)$/.test(path) ? [path] : [];
    });
  }
  for (const path of files(resolve("src"))) {
    assert.doesNotMatch(
      readFileSync(path, "utf8"),
      /CLIENT_SECRET|X-NCP-APIGW-API-KEY\b/,
      path,
    );
  }
});

test("#19-S9 서버에만 있는 일기 id의 핀은 상세 링크를 열지 않는다(서버 일기 상세 연결 전)", () => {
  // 브라우저에 저장된 기록
  const diaries = [{ id: "6f1c0a3e-3c52-4d8e-9d0e-2a2b4c8f9a10", petId: "pet-a" }];

  // API에만 있는 diaryId는 상세 화면이 찾지 못해 404가 되므로 열지 않는다
  assert.equal(isOpenableDiary(diaries, "pet-a", "7"), false);
  assert.equal(pinDetail(false, isOpenableDiary(diaries, "pet-a", "7")), "NOT_READY");
  // 다른 반려동물의 기록 id도 열지 않는다
  assert.equal(isOpenableDiary(diaries, "pet-b", diaries[0].id), false);
  // 브라우저에 있는 기록의 핀만 열고, 예시 핀은 늘 예시로 안내한다
  assert.equal(pinDetail(false, isOpenableDiary(diaries, "pet-a", diaries[0].id)), "OPEN");
  assert.equal(pinDetail(true, true), "SAMPLE");
});
