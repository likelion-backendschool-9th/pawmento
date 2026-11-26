import test from "node:test";
import assert from "node:assert/strict";
import { distanceLabel } from "../features/map/utils/placeDistance.ts";
import {
  highlightParts,
  SEARCH_SUGGESTIONS,
  searchView,
} from "../features/map/utils/placeSearchView.ts";
import type { PlaceSearchState } from "../features/map/utils/placeSearchController.ts";
import type { PlaceSearchResult } from "../features/map/types/Place.ts";

const none: PlaceSearchResult = { places: [], truncated: false, dataAsOf: null, sample: false };
const some: PlaceSearchResult = {
  places: [
    {
      id: "1",
      category: "HOSPITAL",
      name: "서울숲 동물병원",
      address: "서울 성동구 서울숲4길 18",
      phone: null,
      latitude: 37.54,
      longitude: 127.04,
      open24h: false,
      open24hBasis: null,
      openingHours: null,
      closedDays: null,
      distanceMeters: 140,
    },
  ],
  truncated: false,
  dataAsOf: null,
  sample: false,
};
const state = (patch: Partial<PlaceSearchState>): PlaceSearchState => ({
  status: "idle",
  text: "",
  result: null,
  message: "",
  ...patch,
});

// ── 거리 글자(#226-S5, #195-S3) ───────────────────────────────────────────────────────────

test("#226-S5 거리 글자는 990m까지 10m 단위로 올리고, 991m부터는 100m 단위의 소수점 한 자리 km다", () => {
  assert.equal(distanceLabel(140), "140m");
  assert.equal(distanceLabel(141), "150m");
  assert.equal(distanceLabel(990), "990m");
  assert.equal(distanceLabel(991), "1.0km");
  assert.equal(distanceLabel(1000), "1.0km");
  assert.equal(distanceLabel(1001), "1.1km");
  assert.equal(distanceLabel(1200), "1.2km");
  assert.equal(distanceLabel(0), "0m");
});

test("#226-S5 거리가 숫자가 아니거나 음수이면 글자를 만들지 않는다", () => {
  assert.equal(distanceLabel(Number.NaN), "");
  assert.equal(distanceLabel(-5), "");
  assert.equal(distanceLabel(Number.POSITIVE_INFINITY), "");
});

// ── 강조(#195-S10) ────────────────────────────────────────────────────────────────────────

test("#195-S10 입력한 글자가 그대로 들어 있으면 그 글자를 강조하고, 대소문자는 무시한다", () => {
  assert.deepEqual(highlightParts("서울숲 동물병원", "서울숲"), [
    { text: "서울숲", match: true },
    { text: " 동물병원", match: false },
  ]);
  assert.deepEqual(highlightParts("해든 Seoul 병원", "seoul"), [
    { text: "해든 ", match: false },
    { text: "Seoul", match: true },
    { text: " 병원", match: false },
  ]);
  // 여러 곳에 들어 있으면 모두 강조한다
  assert.deepEqual(highlightParts("서울 서울", "서울"), [
    { text: "서울", match: true },
    { text: " ", match: false },
    { text: "서울", match: true },
  ]);
});

test("#195-S10 띄어쓰기가 달라 찾은 경우나 입력이 없으면 강조하지 않는다", () => {
  assert.deepEqual(highlightParts("스테이동물병원", "스테이 동물병원"), [{ text: "스테이동물병원", match: false }]);
  assert.deepEqual(highlightParts("서울숲 동물병원", ""), [{ text: "서울숲 동물병원", match: false }]);
  assert.deepEqual(highlightParts("서울숲 동물병원", "   "), [{ text: "서울숲 동물병원", match: false }]);
  assert.deepEqual(highlightParts("강남 병원", "서울"), [{ text: "강남 병원", match: false }]);
});

test("#195-S10 앞뒤 공백은 지우고 찾으며, 소문자로 바꾸면 길이가 달라지는 글자가 있으면 위치가 어긋나 강조하지 않는다", () => {
  assert.deepEqual(highlightParts("서울숲 병원", "  서울숲 "), [
    { text: "서울숲", match: true },
    { text: " 병원", match: false },
  ]);
  // İ(U+0130)는 소문자로 바꾸면 두 글자가 되어 위치가 어긋난다
  assert.deepEqual(highlightParts("İstanbul 병원", "병원"), [{ text: "İstanbul 병원", match: false }]);
});

test("#195-S10 강조한 조각을 이으면 원래 글자와 같다", () => {
  const text = "성동구 서울숲4길 18 (서울숲)";
  assert.equal(highlightParts(text, "서울숲").map((part) => part.text).join(""), text);
});

// ── 어떤 화면을 보일까(#195-S9, S10, S12, S13) ────────────────────────────────────────────

test("#195-S9 입력이 없으면 최근 검색어가 있을 때는 목록, 없을 때는 안내를 보인다", () => {
  assert.equal(searchView(state({}), true), "recent");
  assert.equal(searchView(state({}), false), "guide");
  assert.equal(searchView(state({ text: "   " }), true), "recent");
});

test("#195-S10 한 글자만 입력하면 검색하지 않고 최근 검색어(없으면 안내)로 돌아간다", () => {
  assert.equal(searchView(state({ text: "서" }), true), "recent");
  assert.equal(searchView(state({ text: "서" }), false), "guide");
});

test("#195-S6 너무 길거나 쓸 수 없는 글자가 든 입력은 이유를 보인다", () => {
  assert.equal(searchView(state({ text: "가".repeat(31) }), true), "invalid");
  assert.equal(searchView(state({ text: "서울\u0000숲" }), false), "invalid");
});

test("#195-S10 처음 찾는 중에는 찾는 중, 이전 결과가 있으면 결과 목록을 남긴다", () => {
  assert.equal(searchView(state({ status: "loading", text: "서울숲" }), true), "searching");
  assert.equal(searchView(state({ status: "loading", text: "서울숲", result: some }), true), "results");
});

test("#195-S10·S12 결과가 있으면 목록, 하나도 없으면 결과 없음이다(오류가 아니다)", () => {
  assert.equal(searchView(state({ status: "done", text: "서울숲", result: some }), false), "results");
  assert.equal(searchView(state({ status: "done", text: "동물카페", result: none }), false), "empty");
});

test("#195-S13 실패하면 오류와 다시 시도를 보인다", () => {
  assert.equal(searchView(state({ status: "error", text: "서울숲", message: "서버에 연결하지 못했어요." }), true), "error");
});

test("#195-S12 결과가 없을 때 제안하는 검색어는 종류 이름이다", () => {
  assert.deepEqual([...SEARCH_SUGGESTIONS], ["동물병원", "동물미용", "동물보호센터"]);
});
