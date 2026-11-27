import test from "node:test";
import assert from "node:assert/strict";
import { NearbyListController, type NearbyListState } from "../features/map/utils/nearbyListController.ts";
import { areaSheetView, nearbySheetView } from "../features/map/utils/nearbyView.ts";
import { boundsAround, myLocationReference } from "../features/map/utils/nearby.ts";
import { EARTH_RADIUS_METERS, toDegrees } from "../features/map/utils/geo.ts";
import type { Place, PlaceCategory, PlaceList } from "../features/map/types/Place.ts";
import type { MapBounds } from "../features/map/types/MapBounds.ts";

const ME = { latitude: 37.5445, longitude: 127.0374 };
const REFERENCE = myLocationReference(ME);

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
const northOf = (meters: number) => ME.latitude + toDegrees(meters / EARTH_RADIUS_METERS);
const done = (places: Place[], extra: Partial<NearbyListState> = {}): NearbyListState => ({
  status: "done",
  places,
  truncated: false,
  dataAsOf: null,
  sample: false,
  message: "",
  ...extra,
});
const list = (places: Place[], truncated = false): PlaceList => ({ places, truncated, dataAsOf: "2026-11-21T00:00:00Z", sample: false });

// 응답을 테스트가 풀어 주는 가짜 목록 조회
function makeList() {
  const calls: Array<{ bounds: MapBounds; categories: readonly PlaceCategory[] }> = [];
  const pending: Array<{ resolve: (value: PlaceList) => void; reject: (cause: unknown) => void }> = [];
  const fn = (bounds: MapBounds, query: { categories: readonly PlaceCategory[] }) => {
    calls.push({ bounds, categories: query.categories });
    return new Promise<PlaceList>((resolve, reject) => pending.push({ resolve, reject }));
  };
  return { fn, calls, pending };
}
const flush = () => new Promise((resolve) => setImmediate(resolve));
const query = (radius: number, categories: PlaceCategory[] = ["HOSPITAL"]) => ({ bounds: boundsAround(ME, radius), categories });

test("#226-S5 기준점을 가운데로 한 영역을 따로 조회하고, 응답을 받으면 목록과 잘림·기준일을 담는다", async () => {
  const fake = makeList();
  const controller = new NearbyListController(fake.fn);
  controller.load(query(500));
  assert.equal(controller.getState().status, "loading");
  assert.equal(fake.calls.length, 1);
  assert.deepEqual(fake.calls[0].bounds, boundsAround(ME, 500));
  fake.pending[0].resolve(list([place("1", "가까운 병원", northOf(100), ME.longitude)], true));
  await flush();
  const state = controller.getState();
  assert.equal(state.status, "done");
  assert.equal(state.places.length, 1);
  assert.equal(state.truncated, true);
  assert.equal(state.dataAsOf, "2026-11-21T00:00:00Z");
});

test("#226-S8·S9 같은 조건이면 다시 조회하지 않는다(지도를 옮기거나 장소를 눌러도 목록은 그대로). 반경이나 종류가 바뀌면 다시 조회한다", async () => {
  const fake = makeList();
  const controller = new NearbyListController(fake.fn);
  controller.load(query(500));
  fake.pending[0].resolve(list([]));
  await flush();
  controller.load(query(500));
  controller.load(query(500));
  assert.equal(fake.calls.length, 1);
  controller.load(query(1000));
  assert.equal(fake.calls.length, 2);
  controller.load(query(1000, ["HOSPITAL", "GROOMING"]));
  assert.equal(fake.calls.length, 3);
  assert.deepEqual(fake.calls[2].categories, ["HOSPITAL", "GROOMING"]);
});

test("#226-S6 반경을 바꾸면 이전 반경의 느린 응답은 버린다", async () => {
  const fake = makeList();
  const controller = new NearbyListController(fake.fn);
  controller.load(query(500));
  controller.load(query(1000));
  // 먼저 보낸 500m 응답이 나중에 도착한다
  fake.pending[1].resolve(list([place("2", "1km 결과", northOf(900), ME.longitude)]));
  await flush();
  fake.pending[0].resolve(list([place("1", "500m 결과", northOf(100), ME.longitude)]));
  await flush();
  assert.deepEqual(controller.getState().places.map((item) => item.id), ["2"]);
});

test("#226-S9 기준이 없어지면(null) 조회를 멈추고 처음 상태로 돌아가며, 요청 중이던 응답도 버린다", async () => {
  const fake = makeList();
  const controller = new NearbyListController(fake.fn);
  controller.load(query(500));
  controller.load(null);
  assert.equal(controller.getState().status, "idle");
  fake.pending[0].resolve(list([place("1", "늦은 응답", northOf(100), ME.longitude)]));
  await flush();
  assert.equal(controller.getState().status, "idle");
  assert.equal(controller.getState().places.length, 0);
});

test("#226-S5 실패하면 오류를 알리고(예시로 바꾸지 않는다), retry로 같은 조건을 다시 부른다", async () => {
  const fake = makeList();
  const controller = new NearbyListController(fake.fn);
  controller.load(query(500));
  fake.pending[0].reject(new Error("서버가 응답하지 않아요"));
  await flush();
  assert.equal(controller.getState().status, "error");
  assert.equal(controller.getState().message, "서버가 응답하지 않아요");
  controller.retry();
  assert.equal(fake.calls.length, 2);
  fake.pending[1].resolve(list([]));
  await flush();
  assert.equal(controller.getState().status, "done");
  // 오류 상태가 아니면 retry는 아무것도 하지 않는다
  controller.retry();
  assert.equal(fake.calls.length, 2);
});

test("#226-S5 화면이 사라진 뒤 도착한 응답은 버리고, 같은 조건으로 다시 열면 다시 조회한다", async () => {
  const fake = makeList();
  const controller = new NearbyListController(fake.fn);
  controller.load(query(500));
  controller.dispose();
  fake.pending[0].resolve(list([place("1", "늦은 응답", northOf(100), ME.longitude)]));
  await flush();
  assert.equal(controller.getState().places.length, 0);
  controller.load(query(500));
  assert.equal(fake.calls.length, 2);
});

test("#226-S5 목록은 반경 안의 장소만 가까운 순으로, 거리 글자와 함께 보이고 위에 문구가 붙는다", () => {
  const places = [
    place("far", "먼 병원", northOf(400), ME.longitude),
    place("near", "가까운 병원", northOf(140), ME.longitude),
    place("mid", "중간 병원", northOf(990), ME.longitude),
  ];
  const view = nearbySheetView(done(places), REFERENCE, 1000, false);
  assert.equal(view.title, "내 주변 가까운 순");
  assert.equal(view.summary, "내 위치에서 반경 1km · 3곳");
  assert.equal(view.body.kind, "rows");
  if (view.body.kind !== "rows") return;
  assert.deepEqual(view.body.rows.map((row) => [row.place.id, row.distance]), [["near", "140m"], ["far", "400m"], ["mid", "990m"]]);
  assert.equal(view.body.note, null);
});

test("#226-S7 조회 영역은 사각형이라 반경(원) 밖의 모서리 장소가 섞이면 화면에서 반경으로 한 번 더 거른다", () => {
  // 북쪽·동쪽으로 각각 470m쯤: 사각형 안(500m 영역)이지만 직선거리는 약 660m다
  const corner = place("corner", "모서리 병원", northOf(470), ME.longitude + 0.0053);
  const at500 = nearbySheetView(done([corner]), REFERENCE, 500, false);
  assert.equal(at500.body.kind, "widen");
  const at1000 = nearbySheetView(done([corner]), REFERENCE, 1000, false);
  assert.equal(at1000.body.kind, "rows");
});

test("#226-S6 반경 안에 없으면 한 단계 넓혀 보기를 권하고, 가장 넓은 3km에도 없으면 지도를 옮겨 보라고 한다", () => {
  const a = nearbySheetView(done([]), REFERENCE, 500, false);
  assert.deepEqual(a.body, { kind: "widen", text: "반경 500m 안에는 없어요", next: 1000, button: "1km로 넓혀 보기" });
  const b = nearbySheetView(done([]), REFERENCE, 1000, false);
  assert.deepEqual(b.body, { kind: "widen", text: "반경 1km 안에는 없어요", next: 3000, button: "3km로 넓혀 보기" });
  const c = nearbySheetView(done([]), REFERENCE, 3000, false);
  assert.deepEqual(c.body, { kind: "none", text: "내 주변에는 아직 없어요. 지도를 옮겨 찾아보세요" });
});

test("#226-S7 서버가 잘렸다고 하면 목록 아래에 안내를 붙인다(반경을 줄일 수 있을 때만 줄이라고 한다)", () => {
  const places = [place("1", "병원", northOf(100), ME.longitude)];
  const wide = nearbySheetView(done(places, { truncated: true }), REFERENCE, 3000, false);
  assert.equal(wide.body.kind === "rows" && wide.body.note, "더 있어요. 반경을 줄여 보세요");
  const narrow = nearbySheetView(done(places, { truncated: true }), REFERENCE, 500, false);
  assert.equal(narrow.body.kind === "rows" && narrow.body.note, "더 있어요. 가까운 곳부터 보여 드려요");
});

test("#226-S5 조회 중·실패·종류를 모두 끔도 각각 보인다. 이때 개수 문구를 0곳으로 잘못 보이지 않는다", () => {
  const loading = nearbySheetView({ ...done([]), status: "loading" }, REFERENCE, 500, false);
  assert.equal(loading.body.kind, "loading");
  assert.equal(loading.summary, "찾는 중이에요");
  const error = nearbySheetView({ ...done([]), status: "error", message: "서버 오류" }, REFERENCE, 500, false);
  assert.deepEqual(error.body, { kind: "error", message: "서버 오류" });
  assert.equal(error.summary, "불러오지 못했어요");
  const off = nearbySheetView(done([place("1", "병원", northOf(100), ME.longitude)]), REFERENCE, 500, true);
  assert.equal(off.body.kind, "nothingChosen");
});

test("#226-S9 기준이 아직 없으면 지도 영역의 조회 결과를 거리 없이 '이 지역 장소 N곳'으로 보인다", () => {
  const places = [place("1", "가 병원", northOf(100), ME.longitude), place("2", "나 병원", northOf(900), ME.longitude)];
  const view = areaSheetView({ places, loading: false, error: "", truncated: false, nothingChosen: false });
  assert.equal(view.title, "이 지역 장소");
  assert.equal(view.summary, "2곳");
  assert.equal(view.body.kind, "rows");
  if (view.body.kind !== "rows") return;
  // 순서는 서버가 준 그대로이고 거리는 없다
  assert.deepEqual(view.body.rows.map((row) => [row.place.id, row.distance]), [["1", ""], ["2", ""]]);
  const empty = areaSheetView({ places: [], loading: false, error: "", truncated: false, nothingChosen: false });
  assert.equal(empty.body.kind, "none");
  const loading = areaSheetView({ places: [], loading: true, error: "", truncated: false, nothingChosen: false });
  assert.equal(loading.body.kind, "loading");
  const truncated = areaSheetView({ places, loading: false, error: "", truncated: true, nothingChosen: false });
  assert.equal(truncated.body.kind === "rows" && truncated.body.note, "장소가 많아서 일부만 보여요. 지도를 확대해 보세요");
});
