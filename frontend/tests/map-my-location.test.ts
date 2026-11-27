import test from "node:test";
import assert from "node:assert/strict";
import {
  MyLocationController,
  type IntroSeen,
  type LocationFix,
  type LocationPermission,
  type LocationReadResult,
  type LocationSource,
} from "../features/map/utils/myLocationController.ts";
import {
  LOCATE_LABEL,
  LOCATION_DENIED,
  LOCATION_FAILED,
  LOCATION_INTRO,
  LOCATION_UNSUPPORTED,
  locateLabel,
} from "../features/map/utils/myLocationText.ts";
import { nextTabIndex } from "../features/map/utils/tabKeys.ts";
import { STATIC_SPAN, staticSpanScale } from "../features/map/utils/placeCanvas.ts";
import { EARTH_RADIUS_METERS, toDegrees, toRadians } from "../features/map/utils/geo.ts";

const FIX: LocationFix = { point: { latitude: 37.4979, longitude: 127.0276 }, accuracy: 35 };

// 위치 읽기를 테스트가 풀어 주는 가짜 위치 기능
function makeSource(permission: LocationPermission, supported = true) {
  const reads: Array<(result: LocationReadResult) => void> = [];
  const source: LocationSource = {
    supported,
    permission: async () => permission,
    read: () => new Promise((resolve) => reads.push(resolve)),
  };
  return { source, reads, permissionSet: (value: LocationPermission) => void (permission = value) };
}

function makeIntro(seen = false) {
  const state = { seen };
  const intro: IntroSeen = { get: () => state.seen, set: () => void (state.seen = true) };
  return { intro, state };
}

const flush = () => new Promise((resolve) => setImmediate(resolve));

test("#226-S1 처음 누르면 권한 창보다 먼저 안내를 보이고, 화면에 들어오자마자 권한을 묻지 않는다", async () => {
  const { source, reads } = makeSource("prompt");
  const { intro } = makeIntro(false);
  const controller = new MyLocationController(source, intro);
  // 만든 것만으로는 아무것도 읽지 않는다
  assert.equal(controller.getState().status, "idle");
  assert.equal(reads.length, 0);
  await controller.press();
  assert.equal(controller.getState().status, "intro");
  assert.equal(reads.length, 0);
  assert.ok(LOCATION_INTRO.body.includes("저장하지 않아요"));
});

test("#226-S1 안내의 계속을 누르면 위치를 읽고, 나중에를 누르면 아무것도 바뀌지 않는다", async () => {
  const { source, reads } = makeSource("prompt");
  const { intro, state } = makeIntro(false);
  const controller = new MyLocationController(source, intro);
  await controller.press();
  controller.dismiss();
  assert.deepEqual(controller.getState(), { status: "idle", fix: null });
  assert.equal(reads.length, 0);
  assert.equal(state.seen, false);
  await controller.press();
  void controller.continueIntro();
  await flush();
  assert.equal(controller.getState().status, "reading");
  assert.equal(reads.length, 1);
  assert.equal(state.seen, true);
});

test("#226-S1 이미 허락했거나 안내를 본 적이 있으면 안내 없이 바로 읽는다", async () => {
  for (const [permission, seen] of [["granted", false], ["prompt", true], ["unknown", true]] as const) {
    const { source, reads } = makeSource(permission);
    const controller = new MyLocationController(source, makeIntro(seen).intro);
    void controller.press();
    await flush();
    assert.equal(controller.getState().status, "reading", `${permission}/${seen}`);
    assert.equal(reads.length, 1);
  }
});

test("#226-S2 읽는 동안에는 다시 눌러도 겹쳐 읽지 않고, 읽으면 위치를 알린다", async () => {
  const { source, reads } = makeSource("granted");
  const received: LocationFix[] = [];
  const controller = new MyLocationController(source, makeIntro(true).intro, (fix) => received.push(fix));
  void controller.press();
  await flush();
  void controller.press();
  void controller.press();
  await flush();
  assert.equal(reads.length, 1);
  reads[0]({ ok: true, fix: FIX });
  await flush();
  assert.deepEqual(controller.getState(), { status: "idle", fix: FIX });
  assert.deepEqual(received, [FIX]);
});

test("#226-S2 다시 누르면 다시 읽고, 읽는 동안 이전 위치(점)는 그대로 남는다", async () => {
  const { source, reads } = makeSource("granted");
  const controller = new MyLocationController(source, makeIntro(true).intro);
  void controller.press();
  await flush();
  reads[0]({ ok: true, fix: FIX });
  await flush();
  void controller.press();
  await flush();
  assert.equal(controller.getState().status, "reading");
  assert.deepEqual(controller.getState().fix, FIX);
  const moved: LocationFix = { point: { latitude: 37.5, longitude: 127.03 }, accuracy: 20 };
  reads[1]({ ok: true, fix: moved });
  await flush();
  assert.deepEqual(controller.getState().fix, moved);
});

test("#226-S3 읽지 못하면(시간 초과·위치를 알 수 없음) 안내하고 지도의 위치는 그대로다", async () => {
  for (const reason of ["timeout", "unavailable"] as const) {
    const { source, reads } = makeSource("granted");
    const received: LocationFix[] = [];
    const controller = new MyLocationController(source, makeIntro(true).intro, (fix) => received.push(fix));
    void controller.press();
    await flush();
    reads[0]({ ok: false, reason });
    await flush();
    assert.equal(controller.getState().status, "failed", reason);
    assert.equal(controller.getState().fix, null);
    assert.equal(received.length, 0);
  }
  assert.ok(LOCATION_FAILED.includes("잠시 뒤 다시 눌러 주세요"));
});

test("#226-S3 이 브라우저가 위치를 지원하지 않으면 안내하고 읽지 않는다", async () => {
  const { source, reads } = makeSource("granted", false);
  const controller = new MyLocationController(source, makeIntro(true).intro);
  await controller.press();
  assert.equal(controller.getState().status, "unsupported");
  assert.equal(reads.length, 0);
  assert.ok(LOCATION_UNSUPPORTED.includes("쓸 수 없어요"));
});

test("#226-S4 권한이 꺼져 있으면 읽지 않고 이유와 해결 방법을 안내한다. 기본 위치로 옮기지 않는다", async () => {
  const { source, reads } = makeSource("denied");
  const received: LocationFix[] = [];
  const controller = new MyLocationController(source, makeIntro(false).intro, (fix) => received.push(fix));
  await controller.press();
  assert.equal(controller.getState().status, "denied");
  assert.equal(reads.length, 0);
  assert.equal(received.length, 0);
  assert.equal(controller.getState().fix, null);
  assert.ok(LOCATION_DENIED.reason.includes("위치가 필요해요"));
  assert.ok(LOCATION_DENIED.fix.includes("위치를 허용"));
  assert.deepEqual(LOCATION_DENIED.guides.map((guide) => guide.device), ["iPhone", "Android"]);
});

test("#226-S4 읽는 중에 브라우저가 권한을 거부해도 거부 안내를 보이고, 설정을 바꾼 뒤 다시 누르면 다시 시도한다", async () => {
  const fake = makeSource("prompt");
  const controller = new MyLocationController(fake.source, makeIntro(true).intro);
  void controller.press();
  await flush();
  fake.reads[0]({ ok: false, reason: "denied" });
  await flush();
  assert.equal(controller.getState().status, "denied");
  fake.permissionSet("granted");
  void controller.press();
  await flush();
  assert.equal(controller.getState().status, "reading");
  assert.equal(fake.reads.length, 2);
});

test("#226-S2 화면이 사라진 뒤에 도착한 위치는 버린다", async () => {
  const { source, reads } = makeSource("granted");
  const received: LocationFix[] = [];
  const controller = new MyLocationController(source, makeIntro(true).intro, (fix) => received.push(fix));
  void controller.press();
  await flush();
  controller.dispose();
  reads[0]({ ok: true, fix: FIX });
  await flush();
  assert.equal(received.length, 0);
  assert.deepEqual(controller.getState(), { status: "idle", fix: null });
});

test("#226-S10 위치는 저장소에 쓰지 않는다. 남기는 것은 안내를 봤다는 표시뿐이다", async () => {
  const { source, reads } = makeSource("prompt");
  const writes: string[] = [];
  const controller = new MyLocationController(source, { get: () => false, set: () => void writes.push("introSeen") });
  await controller.press();
  void controller.continueIntro();
  await flush();
  reads[0]({ ok: true, fix: FIX });
  await flush();
  assert.deepEqual(writes, ["introSeen"]);
  // 위치 값은 상태(메모리)에만 있다
  assert.deepEqual(controller.getState().fix, FIX);
});

test("#226-S2 예시 지도는 내 위치로 옮길 때 반경(500m·1km·3km)의 원이 영역 안에 들어오게 넓힌다. 기본 영역은 500m를 자른다", () => {
  const latitude = 37.5445;
  // 기본 영역의 남북 반폭(약 417m)은 500m 반경보다 작아서 그대로는 잘린다
  const half = STATIC_SPAN.latitude / 2;
  assert.ok(half < toDegrees(500 / EARTH_RADIUS_METERS));
  for (const radius of [500, 1000, 3000]) {
    const scale = staticSpanScale(radius);
    const radiusLat = toDegrees(radius / EARTH_RADIUS_METERS);
    const radiusLng = radiusLat / Math.cos(toRadians(latitude));
    assert.ok((STATIC_SPAN.latitude * scale) / 2 > radiusLat, `${radius}: 남북`);
    assert.ok((STATIC_SPAN.longitude * scale) / 2 > radiusLng, `${radius}: 동서`);
  }
  // 작은 반경이라도 기본보다 좁히지는 않는다
  assert.equal(staticSpanScale(50), 1);
});

test("#226-S4 iPhone 안내에는 Safari에서 이 사이트만 거부한 경우의 경로(웹사이트 설정 → 위치)가 있다", () => {
  const iPhone = LOCATION_DENIED.guides.find((guide) => guide.device === "iPhone");
  assert.ok(iPhone);
  assert.ok(iPhone.steps.some((step) => step.includes("웹사이트 설정") && step.includes("위치")));
  // Android 안내도 그대로 있다
  assert.ok(LOCATION_DENIED.guides.some((guide) => guide.device === "Android"));
});

test("#226-S4 기기 선택 탭은 방향키로 옮기고, 처음과 끝이 이어지며, Home·End로 처음·끝에 간다", () => {
  assert.equal(nextTabIndex("ArrowRight", 0, 2), 1);
  assert.equal(nextTabIndex("ArrowRight", 1, 2), 0);
  assert.equal(nextTabIndex("ArrowLeft", 0, 2), 1);
  assert.equal(nextTabIndex("ArrowLeft", 1, 2), 0);
  assert.equal(nextTabIndex("Home", 1, 3), 0);
  assert.equal(nextTabIndex("End", 0, 3), 2);
  // 가로 탭의 위·아래 키는 화면의 세로 스크롤에 맡긴다
  assert.equal(nextTabIndex("ArrowUp", 0, 2), null);
  assert.equal(nextTabIndex("ArrowDown", 0, 2), null);
  // 탭이 아닌 키는 건드리지 않는다
  assert.equal(nextTabIndex("Enter", 0, 2), null);
  assert.equal(nextTabIndex("Tab", 0, 2), null);
  assert.equal(nextTabIndex("ArrowRight", 0, 0), null);
});

test("#226-S3 이 기기가 위치를 지원하지 않으면 버튼 이름이 눌러서 이유를 볼 수 있다고 알린다(사용 불가로 알리지 않는다)", () => {
  assert.equal(locateLabel({ reading: false, supported: true }), LOCATE_LABEL.idle);
  assert.equal(locateLabel({ reading: true, supported: true }), LOCATE_LABEL.reading);
  const unsupported = locateLabel({ reading: false, supported: false });
  assert.ok(unsupported.includes("눌러서 이유 보기"));
  // 읽는 중이어도 지원하지 않으면 지원하지 않는다는 이름이다
  assert.equal(locateLabel({ reading: true, supported: false }), unsupported);
});

test("#226-S10 권한 안내는 앱이 위치를 저장하지 않는다는 말과 함께, 지도 범위가 서버로 가고 서버 접속 기록에 남을 수 있다는 것을 숨기지 않고 알린다", () => {
  assert.ok(LOCATION_INTRO.body.includes("앱에 저장하지 않아요"));
  assert.ok(LOCATION_INTRO.note.includes("서버로 전송"));
  assert.ok(LOCATION_INTRO.note.includes("지도 범위"));
  assert.ok(LOCATION_INTRO.note.includes("접속 기록"));
});
