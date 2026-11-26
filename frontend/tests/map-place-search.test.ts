import test, { mock } from "node:test";
import assert from "node:assert/strict";
import { ApplicationError } from "../lib/application/ApplicationError.ts";
import type { ApiClient } from "../lib/http/apiClient.ts";
import { createPlaceApi } from "../features/map/api/placeApi.ts";
import type { PlaceSearchResult } from "../features/map/types/Place.ts";
import {
  checkSearchText,
  comparableSearchText,
} from "../features/map/utils/placeSearchText.ts";
import {
  addRecentSearch,
  createRecentSearches,
  parseRecentSearches,
  RECENT_SEARCH_LIMIT,
  recentSearchKey,
  removeRecentSearch,
} from "../features/map/utils/recentSearches.ts";
import {
  PlaceSearchController,
  SEARCH_DELAY_MS,
} from "../features/map/utils/placeSearchController.ts";
import { DemoPlaceRepository } from "../mocks/handlers/DemoPlaceRepository.ts";

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

const hit = {
  id: 7,
  category: "HOSPITAL",
  name: "서울숲 동물병원",
  address: "서울 성동구 서울숲4길 18",
  phone: null,
  latitude: 37.5461,
  longitude: 127.0373,
  open24h: false,
  open24hBasis: null,
  openingHours: null,
  closedDays: null,
  distanceMeters: 140.4,
};

// ── 검색어 가늠(#195-S6, S10) ──────────────────────────────────────────────────────────────

test("#195-S10 검색어는 띄어쓰기·괄호·구두점을 지운 뒤 2자 이상이어야 검색한다", () => {
  assert.deepEqual(checkSearchText("서울숲"), { ok: true, text: "서울숲" });
  // 앞뒤 공백은 지우고 보낸다
  assert.deepEqual(checkSearchText("  서울숲  "), { ok: true, text: "서울숲" });
  assert.deepEqual(checkSearchText("서울"), { ok: true, text: "서울" });
  assert.deepEqual(checkSearchText("서"), { ok: false, problem: "TOO_SHORT" });
  assert.deepEqual(checkSearchText(""), { ok: false, problem: "TOO_SHORT" });
  // 지우고 나면 한 글자인 입력은 짧다. 공백·구두점뿐인 입력은 비어서 짧다(#195-S6)
  assert.deepEqual(checkSearchText(" 서 (.) "), { ok: false, problem: "TOO_SHORT" });
  assert.deepEqual(checkSearchText("_ _"), { ok: false, problem: "TOO_SHORT" });
});

test("#195-S6 정규화한 길이가 30자를 넘으면 검색하지 않고, 구두점으로 길이를 늘려도 정규화한 길이로 센다", () => {
  assert.equal(checkSearchText("가".repeat(30)).ok, true);
  assert.deepEqual(checkSearchText("가".repeat(31)), { ok: false, problem: "TOO_LONG" });
  // 지워지는 글자가 많아도 정규화한 길이가 30자 이하면 보낸다
  assert.equal(checkSearchText("가 ".repeat(30)).ok, true);
});

test("#195-S6 제어 문자가 든 검색어는 검색하지 않는다", () => {
  assert.deepEqual(checkSearchText("서울\u0000숲"), { ok: false, problem: "INVALID_CHARACTER" });
  assert.deepEqual(checkSearchText("서울\u007f숲"), { ok: false, problem: "INVALID_CHARACTER" });
});

test("#195-S2 비교용 값은 띄어쓰기·괄호·구두점·대소문자를 무시한다", () => {
  assert.equal(comparableSearchText("스테이 동물병원"), "스테이동물병원");
  assert.equal(comparableSearchText("스테이(동물병원)"), "스테이동물병원");
  assert.equal(comparableSearchText(" Seoul-Forest "), "seoulforest");
});

// ── 서버 호출(#195-S1, S3, S4, S6) ────────────────────────────────────────────────────────

test("#195-S1 검색어를 인코딩해 보내고, 장소와 거리를 그대로 옮기며 id는 문자열이다", async () => {
  const seen: string[] = [];
  const api = createPlaceApi(
    fakeApi({ places: [hit], truncated: false, dataAsOf: "2026-11-26T00:00:00Z" }, seen),
  );
  const result = await api.search({ q: "  서울숲 동물병원 " });
  assert.deepEqual(seen, ["/maps/places/search?q=%EC%84%9C%EC%9A%B8%EC%88%B2%20%EB%8F%99%EB%AC%BC%EB%B3%91%EC%9B%90"]);
  assert.equal(result.places.length, 1);
  assert.equal(result.places[0].id, "7");
  assert.equal(result.places[0].name, "서울숲 동물병원");
  assert.equal(result.places[0].distanceMeters, 140);
  assert.equal(result.truncated, false);
  assert.equal(result.dataAsOf, "2026-11-26T00:00:00Z");
  assert.equal(result.sample, false);
});

test("#195-S3 지도 중심을 위도·경도 함께 십진수 여섯 자리로 보내고, 보내지 않으면 거리는 null(정보 없음)이다", async () => {
  const seen: string[] = [];
  const api = createPlaceApi(fakeApi({ places: [{ ...hit, distanceMeters: null }], truncated: false }, seen));
  const result = await api.search({ q: "서울숲", center: { latitude: 37.5461, longitude: 127.0373 } });
  assert.match(seen[0], /&latitude=37\.546100&longitude=127\.037300$/);
  assert.equal(result.places[0].distanceMeters, null);
  assert.equal(result.dataAsOf, null);
  await api.search({ q: "서울숲" });
  assert.doesNotMatch(seen[1], /latitude|longitude/);
});

test("#195-S4 고른 종류만 보내고 같은 종류는 한 번만 보내며, 고른 종류가 없으면 서버에 묻지 않고 빈 결과다", async () => {
  const seen: string[] = [];
  const api = createPlaceApi(fakeApi({ places: [], truncated: false }, seen));
  await api.search({ q: "서울숲", categories: ["HOSPITAL", "GROOMING", "HOSPITAL"] });
  assert.match(seen[0], /&categories=HOSPITAL,GROOMING$/);
  const none = await api.search({ q: "서울숲", categories: [] });
  assert.equal(seen.length, 1);
  assert.deepEqual(none, { places: [], truncated: false, dataAsOf: null, sample: false });
});

test("#195-S5 20곳을 넘어 잘렸으면 truncated를 그대로 알려 준다", async () => {
  const api = createPlaceApi(fakeApi({ places: [hit], truncated: true }));
  assert.equal((await api.search({ q: "서울" })).truncated, true);
});

test("#195-S6 잘못된 검색어·종류·지도 중심은 서버에 묻지 않고 VALIDATION이며 이유에 검색어를 담지 않는다", async () => {
  const seen: string[] = [];
  const api = createPlaceApi(fakeApi({ places: [], truncated: false }, seen));
  await assert.rejects(api.search({ q: "서" }), validation);
  await assert.rejects(api.search({ q: "가".repeat(31) }), validation);
  await assert.rejects(api.search({ q: "서울\u0000숲" }), (error) => {
    assert.ok(validation(error));
    assert.doesNotMatch((error as Error).message, /서울/);
    return true;
  });
  await assert.rejects(api.search({ q: "서울숲", categories: ["CAFE" as never] }), validation);
  await assert.rejects(api.search({ q: "서울숲", center: { latitude: 91, longitude: 127 } }), validation);
  await assert.rejects(api.search({ q: "서울숲", center: { latitude: 37, longitude: Number.NaN } }), validation);
  assert.deepEqual(seen, []);
});

test("#195-S13 서버가 주는 오류는 예시 결과로 바꾸지 않고 그대로 올린다", async () => {
  const failing: ApiClient = {
    ...fakeApi({}),
    get: async () => {
      throw new ApplicationError("SERVER", "서버에 연결하지 못했어요.");
    },
  };
  await assert.rejects(createPlaceApi(failing).search({ q: "서울숲" }), (error) => {
    return error instanceof ApplicationError && error.code === "SERVER";
  });
});

test("#195-S9 개발용 예시 저장소는 예시 장소의 이름·주소를 같은 기준으로 찾고 sample을 켠다", async () => {
  const demo = new DemoPlaceRepository();
  const result = await demo.search({ q: "서울숲 동물병원", center: { latitude: 37.5461, longitude: 127.0373 } });
  assert.equal(result.sample, true);
  assert.ok(result.places.length >= 1);
  assert.ok(result.places.every((place) => typeof place.distanceMeters === "number"));
  assert.deepEqual((await demo.search({ q: "존재하지않는장소이름" })).places, []);
});

// ── 최근 검색어(#195-S14) ────────────────────────────────────────────────────────────────

test("#195-S14 새 검색어는 맨 앞에 저장되고, 같은 검색어(띄어쓰기·대소문자 무시)는 중복 없이 맨 앞으로 옮긴다", () => {
  assert.deepEqual(addRecentSearch([], "서울숲"), ["서울숲"]);
  assert.deepEqual(addRecentSearch(["강남", "서울숲"], "서울숲"), ["서울숲", "강남"]);
  assert.deepEqual(addRecentSearch(["강남", "Seoul Forest"], " seoul forest "), ["seoul forest", "강남"]);
});

test("#195-S14 최대 10개이고 넘으면 가장 오래된 것부터 지운다", () => {
  let list: string[] = [];
  for (let i = 1; i <= 12; i += 1) list = addRecentSearch(list, `검색어${i}`);
  assert.equal(list.length, RECENT_SEARCH_LIMIT);
  assert.equal(list[0], "검색어12");
  assert.equal(list.at(-1), "검색어3");
});

test("#195-S14 검색할 수 없는 글자(너무 짧음 등)는 저장하지 않고, 하나 지우기·전체 지우기가 된다", () => {
  assert.deepEqual(addRecentSearch(["강남"], "서"), ["강남"]);
  assert.deepEqual(removeRecentSearch(["강남", "서울숲"], " 서울 숲 "), ["강남"]);
  const memoryStorage = new Map<string, string>();
  const store = createRecentSearches(() => ({
    getItem: (key) => memoryStorage.get(key) ?? null,
    setItem: (key, value) => void memoryStorage.set(key, value),
    removeItem: (key) => void memoryStorage.delete(key),
  }));
  store.add("1", "서울숲");
  store.add("1", "강남");
  assert.deepEqual(store.load("1"), ["강남", "서울숲"]);
  assert.deepEqual(store.remove("1", "강남"), ["서울숲"]);
  assert.deepEqual(store.clear("1"), []);
  assert.equal(memoryStorage.has(recentSearchKey("1")), false);
});

test("#195-S14 깨진 값이나 형식이 다른 값은 무시하고 빈 목록으로 시작한다", () => {
  assert.deepEqual(parseRecentSearches(null), []);
  assert.deepEqual(parseRecentSearches("{깨진 JSON"), []);
  assert.deepEqual(parseRecentSearches('{"a":1}'), []);
  assert.deepEqual(parseRecentSearches('["서울숲", 3]'), []);
  assert.deepEqual(parseRecentSearches('["서울숲", " "]'), []);
  assert.deepEqual(parseRecentSearches('["서울숲","강남"]'), ["서울숲", "강남"]);
  // 열 개를 넘게 저장돼 있어도 열 개까지만 읽는다
  const many = JSON.stringify(Array.from({ length: 14 }, (_, i) => `검색어${i}`));
  assert.equal(parseRecentSearches(many).length, RECENT_SEARCH_LIMIT);
});

test("#195-S14 계정마다 따로 저장해 같은 기기를 다른 사람이 써도 섞이지 않는다", () => {
  const memoryStorage = new Map<string, string>();
  const store = createRecentSearches(() => ({
    getItem: (key) => memoryStorage.get(key) ?? null,
    setItem: (key, value) => void memoryStorage.set(key, value),
    removeItem: (key) => void memoryStorage.delete(key),
  }));
  store.add("member-1", "서울숲");
  store.add("member-2", "강남");
  assert.deepEqual(store.load("member-1"), ["서울숲"]);
  assert.deepEqual(store.load("member-2"), ["강남"]);
  assert.notEqual(recentSearchKey("member-1"), recentSearchKey("member-2"));
});

test("#195-S14 저장소를 쓸 수 없으면(없거나 읽기·쓰기가 실패하면) 오류 없이 이번 방문 동안만 기억한다", () => {
  const none = createRecentSearches(() => null);
  none.add("1", "서울숲");
  assert.deepEqual(none.add("1", "강남"), ["강남", "서울숲"]);
  assert.deepEqual(none.load("1"), ["강남", "서울숲"]);

  const broken = createRecentSearches(() => ({
    getItem: () => {
      throw new Error("denied");
    },
    setItem: () => {
      throw new Error("quota");
    },
    removeItem: () => {
      throw new Error("denied");
    },
  }));
  assert.deepEqual(broken.add("1", "서울숲"), ["서울숲"]);
  assert.deepEqual(broken.load("1"), ["서울숲"]);
  assert.deepEqual(broken.clear("1"), []);

  // 쓰기만 실패하면 그 뒤로는 저장소의 옛 값이 아니라 이번 방문의 값을 쓴다
  const old = new Map<string, string>([[recentSearchKey("1"), JSON.stringify(["옛검색어"])]]);
  const readOnly = createRecentSearches(() => ({
    getItem: (key) => old.get(key) ?? null,
    setItem: () => {
      throw new Error("quota");
    },
    removeItem: () => undefined,
  }));
  assert.deepEqual(readOnly.add("1", "서울숲"), ["서울숲", "옛검색어"]);
  assert.deepEqual(readOnly.load("1"), ["서울숲", "옛검색어"]);
});

// ── 입력에서 검색까지(#195-S10, S13) ─────────────────────────────────────────────────────

const empty: PlaceSearchResult = { places: [], truncated: false, dataAsOf: null, sample: false };
const found = (name: string): PlaceSearchResult => ({
  ...empty,
  places: [{ ...hit, id: "1", category: "HOSPITAL" as const, name, distanceMeters: null }],
});

const flush = () => new Promise<void>((resolve) => setImmediate(resolve));

function deferred() {
  let resolve!: (value: PlaceSearchResult) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<PlaceSearchResult>((ok, fail) => {
    resolve = ok;
    reject = fail;
  });
  return { promise, resolve, reject };
}

test("#195-S10 입력이 멈추고 250ms 뒤에 마지막 입력으로 한 번만 검색한다", async () => {
  mock.timers.enable({ apis: ["setTimeout"] });
  try {
    const queries: string[] = [];
    const controller = new PlaceSearchController(async (query) => {
      queries.push(query.q);
      return found(query.q);
    });
    controller.setInput("서울");
    mock.timers.tick(100);
    controller.setInput("서울숲");
    mock.timers.tick(SEARCH_DELAY_MS - 1);
    assert.deepEqual(queries, []);
    assert.equal(controller.getState().status, "loading");
    mock.timers.tick(1);
    await flush();
    assert.deepEqual(queries, ["서울숲"]);
    assert.equal(controller.getState().status, "done");
    assert.equal(controller.getState().result?.places[0].name, "서울숲");
  } finally {
    mock.timers.reset();
  }
});

test("#195-S10 한 글자만 입력하거나 지우면 검색하지 않고 처음 상태로 돌아간다", async () => {
  mock.timers.enable({ apis: ["setTimeout"] });
  try {
    const queries: string[] = [];
    const controller = new PlaceSearchController(async (query) => {
      queries.push(query.q);
      return found(query.q);
    });
    controller.setInput("서울숲");
    mock.timers.tick(SEARCH_DELAY_MS);
    await flush();
    assert.equal(controller.getState().status, "done");
    controller.setInput("서");
    assert.deepEqual(controller.getState(), { status: "idle", text: "서", result: null, message: "" });
    mock.timers.tick(1000);
    await flush();
    assert.deepEqual(queries, ["서울숲"]);
  } finally {
    mock.timers.reset();
  }
});

test("#195-S10 이전 요청의 느린 응답은 버려서 지금 입력의 결과를 덮지 않는다", async () => {
  mock.timers.enable({ apis: ["setTimeout"] });
  try {
    const first = deferred();
    const second = deferred();
    const pending = [first, second];
    const controller = new PlaceSearchController(() => pending.shift()!.promise);
    controller.setInput("서울숲");
    mock.timers.tick(SEARCH_DELAY_MS);
    controller.setInput("강남역");
    mock.timers.tick(SEARCH_DELAY_MS);
    second.resolve(found("강남역 결과"));
    await flush();
    first.resolve(found("서울숲 결과(늦게 도착)"));
    await flush();
    assert.equal(controller.getState().text, "강남역");
    assert.equal(controller.getState().result?.places[0].name, "강남역 결과");
  } finally {
    mock.timers.reset();
  }
});

test("#195-S10 입력을 지우면 요청 중이던 응답도 버린다", async () => {
  mock.timers.enable({ apis: ["setTimeout"] });
  try {
    const slow = deferred();
    const controller = new PlaceSearchController(() => slow.promise);
    controller.setInput("서울숲");
    mock.timers.tick(SEARCH_DELAY_MS);
    controller.setInput("");
    slow.resolve(found("서울숲 결과"));
    await flush();
    assert.deepEqual(controller.getState(), { status: "idle", text: "", result: null, message: "" });
  } finally {
    mock.timers.reset();
  }
});

test("#195-S13 실패하면 예시 결과로 바꾸지 않고 오류를 알리며, 다시 시도하면 같은 검색어로 바로 다시 부른다", async () => {
  mock.timers.enable({ apis: ["setTimeout"] });
  try {
    const queries: string[] = [];
    let failing = true;
    const controller = new PlaceSearchController(async (query) => {
      queries.push(query.q);
      if (failing) throw new ApplicationError("SERVER", "서버에 연결하지 못했어요.");
      return found("서울숲 동물병원");
    });
    controller.setInput("서울숲");
    mock.timers.tick(SEARCH_DELAY_MS);
    await flush();
    const failed = controller.getState();
    assert.equal(failed.status, "error");
    assert.equal(failed.result, null);
    assert.equal(failed.message, "서버에 연결하지 못했어요.");
    assert.equal(failed.text, "서울숲");

    failing = false;
    controller.retry();
    assert.equal(controller.getState().status, "loading");
    await flush();
    assert.deepEqual(queries, ["서울숲", "서울숲"]);
    assert.equal(controller.getState().status, "done");
    assert.equal(controller.getState().text, "서울숲");
  } finally {
    mock.timers.reset();
  }
});

test("#195-S13 오류 상태가 아니면 다시 시도해도 아무것도 하지 않고, 오류에 문구가 없으면 기본 안내를 쓴다", async () => {
  mock.timers.enable({ apis: ["setTimeout"] });
  try {
    let calls = 0;
    const controller = new PlaceSearchController(async () => {
      calls += 1;
      throw "문자열 오류";
    });
    controller.retry();
    assert.equal(calls, 0);
    controller.setInput("서울숲");
    mock.timers.tick(SEARCH_DELAY_MS);
    await flush();
    assert.equal(controller.getState().status, "error");
    assert.match(controller.getState().message, /다시 시도/);
  } finally {
    mock.timers.reset();
  }
});

test("#195-S10 지도 중심·종류와 함께 검색하고, 같은 입력을 다시 넣어도 다시 부르지 않는다", async () => {
  mock.timers.enable({ apis: ["setTimeout"] });
  try {
    const seen: unknown[] = [];
    const controller = new PlaceSearchController(async (query) => {
      seen.push(query);
      return found("서울숲");
    });
    const context = { categories: ["HOSPITAL" as const], center: { latitude: 37.5, longitude: 127 } };
    controller.setInput("서울숲", context);
    mock.timers.tick(SEARCH_DELAY_MS);
    await flush();
    controller.setInput("서울숲", context);
    mock.timers.tick(SEARCH_DELAY_MS);
    await flush();
    assert.deepEqual(seen, [{ q: "서울숲", ...context }]);
  } finally {
    mock.timers.reset();
  }
});

test("#195-S10 화면이 사라지면(dispose) 기다리던 검색을 취소하고, 그 뒤에도 다시 쓸 수 있다", async () => {
  mock.timers.enable({ apis: ["setTimeout"] });
  try {
    const queries: string[] = [];
    const controller = new PlaceSearchController(async (query) => {
      queries.push(query.q);
      return found(query.q);
    });
    controller.setInput("서울숲");
    controller.dispose();
    mock.timers.tick(1000);
    await flush();
    assert.deepEqual(queries, []);
    controller.setInput("강남역");
    mock.timers.tick(SEARCH_DELAY_MS);
    await flush();
    assert.deepEqual(queries, ["강남역"]);
  } finally {
    mock.timers.reset();
  }
});

// ── 리뷰 반영: dispose 뒤 같은 입력, 로그인 전 최근 검색어, 서버와 같은 공백 처리 ──────────────

test("#195-S10 기다리던 검색이 dispose로 취소된 뒤에도 같은 입력으로 다시 검색한다(loading에 멈추지 않는다)", async () => {
  mock.timers.enable({ apis: ["setTimeout"] });
  try {
    const queries: string[] = [];
    const controller = new PlaceSearchController(async (query) => {
      queries.push(query.q);
      return found(query.q);
    });
    controller.setInput("서울숲");
    controller.dispose();
    mock.timers.tick(1000);
    await flush();
    assert.deepEqual(queries, []);
    // 같은 글자를 다시 넣으면 기다리는 검색이 없으므로 다시 검색한다
    controller.setInput("서울숲");
    mock.timers.tick(SEARCH_DELAY_MS);
    await flush();
    assert.deepEqual(queries, ["서울숲"]);
    assert.equal(controller.getState().status, "done");
  } finally {
    mock.timers.reset();
  }
});

test("#195-S10 요청 중에 dispose돼도 옛 응답은 버리고, 같은 입력으로 다시 검색한다", async () => {
  mock.timers.enable({ apis: ["setTimeout"] });
  try {
    const slow = deferred();
    const answers = [slow.promise, Promise.resolve(found("다시 받은 결과"))];
    const controller = new PlaceSearchController(() => answers.shift()!);
    controller.setInput("서울숲");
    mock.timers.tick(SEARCH_DELAY_MS);
    controller.dispose();
    slow.resolve(found("버려질 옛 응답"));
    await flush();
    assert.equal(controller.getState().status, "loading");
    assert.equal(controller.getState().result, null);
    controller.setInput("서울숲");
    mock.timers.tick(SEARCH_DELAY_MS);
    await flush();
    assert.equal(controller.getState().status, "done");
    assert.equal(controller.getState().result?.places[0].name, "다시 받은 결과");
  } finally {
    mock.timers.reset();
  }
});

test("#195-S10 화면이 다시 보일 때(resume) 취소돼 멈춘 검색을 이어서 하고, 멈춘 것이 없으면 아무것도 하지 않는다", async () => {
  mock.timers.enable({ apis: ["setTimeout"] });
  try {
    const queries: string[] = [];
    const controller = new PlaceSearchController(async (query) => {
      queries.push(query.q);
      return found(query.q);
    });
    // 입력이 없을 때와 기다리는 검색이 살아 있을 때는 아무것도 하지 않는다
    controller.resume();
    controller.setInput("서울숲");
    controller.resume();
    mock.timers.tick(SEARCH_DELAY_MS);
    await flush();
    assert.deepEqual(queries, ["서울숲"]);
    controller.resume();
    mock.timers.tick(SEARCH_DELAY_MS);
    await flush();
    assert.deepEqual(queries, ["서울숲"]);

    // 숨겨지며 취소된 검색(Strict Mode의 정리와 같은 순서)은 다시 보일 때 이어진다
    controller.setInput("강남역");
    controller.dispose();
    controller.resume();
    assert.equal(controller.getState().status, "loading");
    mock.timers.tick(SEARCH_DELAY_MS);
    await flush();
    assert.deepEqual(queries, ["서울숲", "강남역"]);
    assert.equal(controller.getState().status, "done");
    assert.equal(controller.getState().text, "강남역");
  } finally {
    mock.timers.reset();
  }
});

test("#195-S14 계정이 없으면(로그인 전) 브라우저 저장소를 쓰지 않고 이번 방문 동안만 기억한다", () => {
  const calls: string[] = [];
  const store = createRecentSearches(() => {
    calls.push("storage");
    return {
      getItem: () => null,
      setItem: () => calls.push("set"),
      removeItem: () => calls.push("remove"),
    } as unknown as Pick<Storage, "getItem" | "setItem" | "removeItem">;
  });
  store.add("", "서울숲");
  assert.deepEqual(store.add("", "강남"), ["강남", "서울숲"]);
  assert.deepEqual(store.load(""), ["강남", "서울숲"]);
  store.clear("");
  assert.deepEqual(store.load(""), []);
  assert.equal(calls.length, 0);
  // 계정이 있으면 저장소를 쓴다
  store.add("member-1", "서울숲");
  assert.ok(calls.includes("set"));
});

test("#195-S6 앞뒤 공백은 서버의 String.strip()과 같은 글자만 지우고(NBSP는 그대로), 가운데 공백은 \\s의 여섯 글자만 무시한다", () => {
  // 전각 공백(U+3000)·줄 구분(U+2028)·U+001C는 앞뒤에서 지운다
  assert.deepEqual(checkSearchText("　서울숲　"), { ok: true, text: "서울숲" });
  assert.deepEqual(checkSearchText("\u001c서울숲 "), { ok: true, text: "서울숲" });
  // NBSP(U+00A0)는 서버가 지우지 않으므로 클라이언트도 지우지 않고 그대로 보낸다
  assert.deepEqual(checkSearchText(" 서울숲"), { ok: true, text: " 서울숲" });
  // 가운데의 전각 공백·NBSP는 무시하지 않는다(서버와 같은 글자 수로 센다)
  assert.equal(comparableSearchText("서울　숲"), "서울　숲");
  assert.equal(comparableSearchText("서울 숲"), "서울 숲");
  // 가운데의 탭·세로 탭은 \s라서 지운다. U+001C는 제어 문자라서 검색어로 쓸 수 없다
  assert.equal(comparableSearchText("서울\t\u000b숲"), "서울숲");
  assert.deepEqual(checkSearchText("서울\u001c숲"), { ok: false, problem: "INVALID_CHARACTER" });
  // 이 값들로 센 길이도 서버와 같다: 전각 공백 하나가 사이에 있으면 3글자라 짧지 않다
  assert.equal(checkSearchText("서　숲").ok, true);
  assert.deepEqual(checkSearchText("　서　"), { ok: false, problem: "TOO_SHORT" });
});
