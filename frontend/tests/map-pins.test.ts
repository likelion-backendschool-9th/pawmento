import test from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { ApplicationError } from "../lib/application/ApplicationError.ts";
import type { ApiClient } from "../lib/http/apiClient.ts";
import { createMapPinApi } from "../features/map/api/mapPinApi.ts";
import { createMapSource } from "../features/map/source.ts";
import type { MapBounds } from "../features/map/types/MapBounds.ts";
import type { MapPinOptions } from "../features/map/types/MapPin.ts";
import {
  clampBounds,
  containsPoint,
  isValidBounds,
  toViewportRatio,
} from "../features/map/utils/mapBounds.ts";
import { isDemoAccount } from "../features/member/types/account.ts";
import { DemoAuthRepository } from "../mocks/handlers/DemoAuthRepository.ts";
import { DemoMapPinRepository } from "../mocks/handlers/DemoMapPinRepository.ts";
import { DemoWalkRepository } from "../mocks/handlers/DemoWalkRepository.ts";
import {
  GUARANTEED_SIZES,
  LABELS,
  LABEL_FONT,
  PARK,
  RIVER,
  parkTrees,
  visibleWindow,
} from "../features/map/utils/staticMapShapes.ts";
import { pinMarkup } from "../features/map/utils/pinMarkup.ts";
import {
  isOpenableDiary,
  pinDetail,
} from "../features/map/utils/pinDetail.ts";

const SEOUL_FOREST: MapBounds = {
  minLatitude: 37.5407,
  minLongitude: 127.0319,
  maxLatitude: 37.5482,
  maxLongitude: 127.0429,
};

function mapSource(pins: {
  listPins(petId: string, bounds: MapBounds, options?: MapPinOptions): Promise<{
    pins: [];
    truncated: boolean;
    sample: boolean;
  }>;
}) {
  return createMapSource({
    pins,
    walks: {
      start: async (petId) => ({ walkId: "x", petId, startedAt: "t" }),
      addPoints: async () => {},
      end: async () => ({ status: "ENDED" as const, endedAt: null }),
    },
    history: {
      listDay: async () => ({ walks: [], sample: false }),
      listDays: async () => ({ byDate: {}, failed: [] }),
    },
    demoPins: new DemoMapPinRepository(),
    demoWalks: new DemoWalkRepository(),
  });
}

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

test("#19-S5 영역은 서버가 받는 범위로 맞추고, 최솟값이 최댓값보다 크면 요청하지 않는다", async () => {
  const seen: string[] = [];
  const map = createMapPinApi(fakeApi({ pins: [], truncated: false }, seen));

  // 멀리 줄여서 지도 SDK가 범위를 넘는 값을 줄 때
  assert.deepEqual(
    clampBounds({ minLatitude: -95, minLongitude: -200, maxLatitude: 95, maxLongitude: 200 }),
    { minLatitude: -90, minLongitude: -180, maxLatitude: 90, maxLongitude: 180 },
  );
  await map.listPins("10", { minLatitude: -95, minLongitude: -200, maxLatitude: 95, maxLongitude: 200 });
  assert.equal(seen.length, 1);

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
  assert.equal(seen.length, 1, "잘못된 영역은 서버에 묻지 않는다");
  assert.equal(isValidBounds(SEOUL_FOREST), true);
});

test("#19-S5 서버 요청은 쿼리 문자열로만 보내고 영역 값은 지수 표기 없는 십진수다", async () => {
  const seen: string[] = [];
  const pins = createMapPinApi(
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

  const list = await pins.listPins("10", SEOUL_FOREST);
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
  await pins.listPins("10", {
    minLatitude: -1e-7,
    minLongitude: -1e-7,
    maxLatitude: 1e-7,
    maxLongitude: 1e-7,
  });
  assert.doesNotMatch(seen[1], /e[-+]?\d/i);
});

test("#19-S6 서버가 잘렸다고 알려 주면 그대로 전달한다", async () => {
  const pins = createMapPinApi(fakeApi({ pins: [], truncated: true }));
  assert.equal((await pins.listPins("10", SEOUL_FOREST)).truncated, true);
});

test("#19-S9 서버 반려동물(양의 정수 id)만 서버에 묻고, 데모 반려동물은 예시 핀을 본다", async () => {
  const asked: string[] = [];
  const source = mapSource({
    async listPins(petId) {
      asked.push(petId);
      return { pins: [], truncated: false, sample: false };
    },
  });

  assert.equal((await source.map.listPins("10", SEOUL_FOREST, { sample: true })).sample, false);
  const demo = await source.map.listPins(
    "6f1c0a3e-3c52-4d8e-9d0e-2a2b4c8f9a10",
    SEOUL_FOREST,
    { sample: true },
  );
  assert.equal(demo.sample, true);
  assert.ok(demo.pins.length > 0);
  assert.deepEqual(asked, ["10"]);
  for (const id of ["0", "-1", "1.5", "01", "abc"]) {
    assert.equal((await source.map.listPins(id, SEOUL_FOREST, { sample: true })).sample, true, id);
  }
});

test("#19-S5 예시 핀도 보이는 영역 안의 것만 돌려준다", async () => {
  const demo = new DemoMapPinRepository();
  const all = await demo.list("x", SEOUL_FOREST, { sample: true });
  const narrow = await demo.list(
    "x",
    { ...SEOUL_FOREST, minLatitude: 37.5455 },
    { sample: true },
  );
  assert.ok(narrow.pins.length < all.pins.length);
  for (const pin of narrow.pins) assert.equal(containsPoint({ ...SEOUL_FOREST, minLatitude: 37.5455 }, pin), true);
  const none = await demo.list(
    "x",
    { minLatitude: 10, minLongitude: 10, maxLatitude: 11, maxLongitude: 11 },
    { sample: true },
  );
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

test("#19-S9 핀 모양은 일상은 발자국, 정원은 꽃이고, 선택하면 채운 원과 얇은 고리의 두 겹 후광이 생긴다", () => {
  const paw = pinMarkup("ACTIVE");
  const flower = pinMarkup("GARDEN");
  assert.match(paw, /rx="7.4"/);
  assert.doesNotMatch(paw, /rotate\(72\)/);
  assert.match(flower, /rotate\(72\)/);
  assert.doesNotMatch(flower, /rx="7.4"/);
  assert.match(paw, /var\(--brand-primary\)/);
  assert.doesNotMatch(paw, /opacity="\.16"/);
  assert.match(pinMarkup("ACTIVE", true), /opacity="\.16"/);

  // 선택한 핀은 채운 원과 얇은 고리의 두 겹 후광을 두르고, 선택하지 않은 핀에는 후광이 없다
  const plain = pinMarkup("ACTIVE", false);
  const selected = pinMarkup("ACTIVE", true);
  assert.equal((plain.match(/<circle/g) ?? []).length, 0);
  assert.equal((selected.match(/<circle[^>]*fill="var\(--brand-primary\)"/g) ?? []).length, 1);
  assert.equal((selected.match(/<circle[^>]*fill="none"[^>]*stroke="var\(--brand-primary\)"/g) ?? []).length, 1);
  assert.notEqual(pinMarkup("GARDEN", true), selected, "정원 모드는 꽃 모양이다");
});

test("#19-R6 지도 비밀 키는 프론트엔드 코드에 두지 않는다", () => {
  function files(folder: string): string[] {
    return readdirSync(folder, { withFileTypes: true }).flatMap((item) => {
      const path = resolve(folder, item.name);
      return item.isDirectory() ? files(path) : /\.(tsx?|css)$/.test(path) ? [path] : [];
    });
  }
  for (const sourceRoot of ["app", "features", "components", "lib", "styles"]) {
    for (const path of files(resolve(sourceRoot))) {
      assert.doesNotMatch(
        readFileSync(path, "utf8"),
        /CLIENT_SECRET|X-NCP-APIGW-API-KEY\b/,
        path,
      );
    }
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

test("#19-S10 일반 계정의 브라우저 반려동물에는 예시 핀을 섞지 않는다. 데모 계정에만 섞는다", async () => {
  const demo = new DemoMapPinRepository();
  for (const options of [undefined, {}, { sample: false }]) {
    assert.deepEqual(await demo.list("ordinary-pet", SEOUL_FOREST, options), {
      pins: [],
      truncated: false,
      sample: false,
    });
  }
  const sample = await demo.list("demo-pet", SEOUL_FOREST, { sample: true });
  assert.equal(sample.sample, true);
  assert.ok(sample.pins.length > 0);

  // 출처 분기도 옵션을 그대로 전달한다
  const seen: unknown[] = [];
  const source = mapSource({
    async listPins(_pet, _bounds, options) {
      seen.push(options);
      return { pins: [], truncated: false, sample: false };
    },
  });
  await source.map.listPins("10", SEOUL_FOREST, { sample: true });
  await source.map.listPins("10", SEOUL_FOREST);
  assert.deepEqual(seen, [{ sample: true }, undefined]);
});

test("#19-S10 데모 여부는 이메일이 아니라 데모 세션이 만든 계정인지로 판단한다. 이메일이 @pawmento.local인 일반 계정은 예시 핀을 받지 않는다", async () => {
  // 서버 로그인으로 받은 일반 계정: 이메일 도메인이 같아도 데모가 아니다
  for (const account of [
    { id: "42", email: "member@pawmento.local", nickname: "일반 회원" },
    { id: "demo", email: "demo@pawmento.local", nickname: "이름만 같은 일반 계정" },
    { id: "demo-google", email: "google@pawmento.local", nickname: "접두사만 같은 일반 계정" },
  ]) {
    assert.equal(isDemoAccount(account), false, account.id);
  }
  assert.equal(isDemoAccount(null), false);
  assert.equal(isDemoAccount(undefined), false);

  // 데모 인증 저장소가 만든 계정은 모두 데모다(가입 없이 둘러보기, 소셜 데모, 탭 안 가입)
  const auth = new DemoAuthRepository();
  assert.equal(isDemoAccount(await auth.startDemo()), true);
  assert.equal(isDemoAccount(await auth.signInDemoProvider("google")), true);
  assert.equal(isDemoAccount(await auth.signInDemoProvider("kakao")), true);
  await auth.signUp({ email: "tab@example.com", password: "tab-demo-pass", nickname: "탭" });
  assert.equal(isDemoAccount(await auth.signIn({ email: "tab@example.com", password: "tab-demo-pass" })), true);
  // 닉네임을 바꿔도 데모 표시가 유지된다
  assert.equal(isDemoAccount(await auth.update("새 이름")), true);
});

test("#19-S11 예시 지도의 나무는 항상 같은 배치로 공원 안에만 놓이고 서로 겹치지 않는다", () => {
  const trees = parkTrees();
  assert.deepEqual(trees, parkTrees(), "같은 값에서 늘 같은 배치");
  assert.ok(trees.length >= 30, String(trees.length));
  for (const tree of trees) {
    assert.ok(((tree.x - PARK.cx) / PARK.rx) ** 2 + ((tree.y - PARK.cy) / PARK.ry) ** 2 <= 1, JSON.stringify(tree));
    assert.ok(tree.r > 0 && tree.r < 2);
  }
  for (let i = 0; i < trees.length; i += 1)
    for (let j = i + 1; j < trees.length; j += 1)
      assert.ok(Math.hypot(trees[i].x - trees[j].x, trees[i].y - trees[j].y) >= 2.6, `${i}-${j}`);
});

test("#19-S11 예시 지도의 강과 지명은 데스크톱·넓은 화면·휴대폰 크기에서 보이는 범위 안에 있다", () => {
  const wide = visibleWindow(1188, 488);
  assert.ok(Math.abs(wide.minY - 29.46) < 0.01 && Math.abs(wide.maxY - 70.54) < 0.01, JSON.stringify(wide));
  const phone = visibleWindow(390, 700);
  assert.ok(Math.abs(phone.minX - 22.14) < 0.01 && Math.abs(phone.maxX - 77.86) < 0.01, JSON.stringify(phone));
  for (const [width, height] of GUARANTEED_SIZES) {
    const view = visibleWindow(width, height);
    assert.ok(RIVER.lowestY <= view.maxY, `${width}×${height}: 강의 가장 낮은 곳 ${RIVER.lowestY} > ${view.maxY}`);
    for (const label of LABELS) {
      const right = label.x + label.text.length * LABEL_FONT;
      const top = label.y - LABEL_FONT;
      assert.ok(label.x >= view.minX && right <= view.maxX, `${width}×${height}: ${label.text} x=${label.x}~${right}`);
      assert.ok(top >= view.minY && label.y <= view.maxY, `${width}×${height}: ${label.text} y=${top}~${label.y}`);
    }
  }
  assert.ok(LABELS.some((label) => label.water), "강 이름이 있다");
});
