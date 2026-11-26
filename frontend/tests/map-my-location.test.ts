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
import { LOCATION_DENIED, LOCATION_FAILED, LOCATION_INTRO, LOCATION_UNSUPPORTED } from "../features/map/utils/myLocationText.ts";

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
