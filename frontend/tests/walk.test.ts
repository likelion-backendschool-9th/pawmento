import test from "node:test";
import assert from "node:assert/strict";
import { ApplicationError } from "../src/shared/application/ApplicationError.ts";
import { ApiError, type ApiClient } from "../src/shared/http/apiClient.ts";
import {
  distanceBetween,
  elapsedSeconds,
  formatDistance,
  formatDuration,
  isValidWalkPoint,
  MAX_POINTS_PER_REQUEST,
  MAX_POINTS_PER_WALK,
  pathDistance,
  type WalkPoint,
} from "../src/map/domain/Walk.ts";
import { WalkRecorder } from "../src/map/application/WalkRecorder.ts";
import { createWalkUseCases } from "../src/map/application/walkUseCases.ts";
import type {
  ActiveWalk,
  ActiveWalkStore,
  WalkRepository,
} from "../src/map/application/walkPorts.ts";
import { HttpWalkRepository } from "../src/map/adapters/outbound/HttpWalkRepository.ts";
import { DemoWalkRepository } from "../src/map/adapters/outbound/DemoWalkRepository.ts";
import { PetScopedWalkRepository } from "../src/map/adapters/outbound/PetScopedWalkRepository.ts";
import { LocalActiveWalkStore } from "../src/map/adapters/outbound/LocalActiveWalkStore.ts";

const point = (n: number, extra: Partial<WalkPoint> = {}): WalkPoint => ({
  latitude: 37.5445 + n * 0.0001,
  longitude: 127.0374,
  recordedAt: new Date(Date.UTC(2026, 10, 20, 1, 0, 0) + n * 15_000).toISOString(),
  ...extra,
});

function fakeRepository(sent: WalkPoint[][] = [], failOn: number[] = []): WalkRepository & { calls: number } {
  const repo = {
    calls: 0,
    start: async (petId: string) => ({ walkId: "5", petId, startedAt: "2026-11-20T01:00:00Z" }),
    addPoints: async (_walkId: string, points: readonly WalkPoint[]) => {
      repo.calls += 1;
      if (failOn.includes(repo.calls)) throw new ApplicationError("NETWORK", "연결이 끊겼어요.");
      sent.push([...points]);
    },
    end: async () => ({ status: "ENDED" as const, endedAt: "2026-11-20T01:30:00Z" }),
  };
  return repo;
}

function fakeApi(handlers: {
  post?: (path: string, body?: unknown) => unknown;
}, seen: { path: string; body?: unknown }[] = []): ApiClient {
  return {
    get: async () => {
      throw Error("not used");
    },
    post: async <T>(path: string, body?: unknown) => {
      seen.push({ path, body });
      return (handlers.post ? handlers.post(path, body) : undefined) as T;
    },
    patch: async () => {
      throw Error("not used");
    },
    postEmpty: async () => {
      throw Error("not used");
    },
  };
}

test("#73-S1 걸은 거리와 시간은 점들과 시작 시각으로 계산하고 보기 좋게 적는다", () => {
  // 위도 0.0001도는 약 11.1m
  const d = distanceBetween({ latitude: 37.5445, longitude: 127.0374 }, { latitude: 37.5446, longitude: 127.0374 });
  assert.ok(d > 11 && d < 11.3, String(d));
  assert.equal(pathDistance([]), 0);
  assert.equal(pathDistance([point(0)]), 0);
  assert.ok(Math.abs(pathDistance([point(0), point(1), point(2)]) - 22.2) < 0.4);

  assert.equal(formatDuration(0), "00:00:00");
  assert.equal(formatDuration(24 * 60 + 18), "00:24:18");
  assert.equal(formatDuration(3600 + 5), "01:00:05");
  assert.equal(formatDuration(-3), "00:00:00");
  assert.deepEqual(formatDistance(320.4), { value: "320", unit: "m" });
  assert.deepEqual(formatDistance(1240), { value: "1.24", unit: "km" });

  const start = "2026-11-20T01:00:00Z";
  assert.equal(elapsedSeconds(start, Date.parse(start) + 90_500), 90);
  assert.equal(elapsedSeconds(start, Date.parse(start) - 5_000), 0);
  assert.equal(elapsedSeconds("not-a-date", Date.now()), 0);
});

test("#73-S5 잘못된 점은 모으지 않는다. 위도·경도 범위와 시각 형식을 확인한다", () => {
  assert.equal(isValidWalkPoint(point(0)), true);
  for (const bad of [
    point(0, { latitude: 91 }),
    point(0, { longitude: -181 }),
    point(0, { latitude: Number.NaN }),
    point(0, { recordedAt: "어제" }),
  ]) {
    assert.equal(isValidWalkPoint(bad), false, JSON.stringify(bad));
  }
  const recorder = new WalkRecorder(fakeRepository(), "5");
  assert.equal(recorder.record(point(0, { latitude: 91 })), false);
  assert.equal(recorder.points.length, 0);
});

test("#73-S4 모은 좌표는 시간 순서대로 100개씩 나눠 보낸다", async () => {
  const sent: WalkPoint[][] = [];
  const recorder = new WalkRecorder(fakeRepository(sent), "5");
  for (let i = 0; i < 250; i += 1) assert.equal(recorder.record(point(i)), true);
  assert.equal(recorder.pendingCount, 250);

  await recorder.flush();
  assert.deepEqual(sent.map((batch) => batch.length), [100, 100, 50]);
  assert.ok(sent.every((batch) => batch.length <= MAX_POINTS_PER_REQUEST));
  assert.deepEqual(sent.flat().map((p) => p.recordedAt), Array.from({ length: 250 }, (_, i) => point(i).recordedAt));
  assert.equal(recorder.pendingCount, 0);
  assert.equal(recorder.sentCount, 250);
  // 보낼 것이 없으면 서버를 부르지 않는다
  const repo = fakeRepository();
  await new WalkRecorder(repo, "5").flush();
  assert.equal(repo.calls, 0);
});

test("#73-S6 보내다 실패하면 보낸 만큼만 인정하고 남은 좌표는 다시 보낸다", async () => {
  const sent: WalkPoint[][] = [];
  const repo = fakeRepository(sent, [2]); // 두 번째 요청이 실패한다
  const recorder = new WalkRecorder(repo, "5");
  for (let i = 0; i < 150; i += 1) recorder.record(point(i));

  await assert.rejects(() => recorder.flush(), /연결이 끊겼어요/);
  assert.equal(recorder.sentCount, 100, "첫 100개는 보냈다");
  assert.equal(recorder.pendingCount, 50);

  await recorder.flush(); // 다시 보내면 남은 50개만 나간다
  assert.deepEqual(sent.map((batch) => batch.length), [100, 50]);
  assert.equal(recorder.pendingCount, 0);
  assert.equal(sent.flat().length, 150, "같은 좌표가 두 번 나가지 않았다");
});

test("#73-S6 같은 시각의 점과 산책 한도(5,000개)를 넘는 점은 모으지 않는다", () => {
  const recorder = new WalkRecorder(fakeRepository(), "5");
  assert.equal(recorder.record(point(0)), true);
  assert.equal(recorder.record(point(0, { latitude: 37.6 })), false, "같은 시각");
  const full = new WalkRecorder(fakeRepository(), "5", {
    points: Array.from({ length: MAX_POINTS_PER_WALK }, (_, i) => point(i)),
    sentCount: MAX_POINTS_PER_WALK,
  });
  assert.equal(full.record(point(MAX_POINTS_PER_WALK + 1)), false);
});

test("#73-S6 동시에 두 번 보내도 한 번에 하나씩 보내 좌표가 겹치지 않는다", async () => {
  const sent: WalkPoint[][] = [];
  const recorder = new WalkRecorder(fakeRepository(sent), "5");
  for (let i = 0; i < 8; i += 1) recorder.record(point(i));
  await Promise.all([recorder.flush(), recorder.flush()]);
  assert.equal(sent.flat().length, 8);
  assert.equal(new Set(sent.flat().map((p) => p.recordedAt)).size, 8);
});

test("#73-S1 산책을 시작하고 끝낸다. 끝낼 때 남은 좌표를 먼저 보내고, 못 보내면 끝내지 않는다", async () => {
  const sent: WalkPoint[][] = [];
  const memory = new Map<string, ActiveWalk>();
  const store: ActiveWalkStore = {
    load: (petId) => memory.get(petId) ?? null,
    save: (active) => void memory.set(active.walk.petId, active),
    clear: (petId) => void memory.delete(petId),
  };
  const walks = createWalkUseCases(fakeRepository(sent, [1]), store);

  const { walk, recorder } = await walks.start("10");
  assert.equal(walk.walkId, "5");
  assert.equal(memory.get("10")?.walk.walkId, "5", "시작하면 이어 갈 수 있게 남긴다");
  recorder.record(point(0));
  walks.remember(walk, recorder);
  assert.equal(memory.get("10")?.points.length, 1);

  // 첫 전송이 실패하므로 끝내기도 실패하고 산책 기록은 남는다
  await assert.rejects(() => walks.end(walk, recorder), /연결이 끊겼어요/);
  assert.ok(memory.has("10"));
  // 다시 끝내면 남은 좌표를 보내고 끝난다
  const result = await walks.end(walk, recorder);
  assert.equal(result.status, "ENDED");
  assert.equal(sent.flat().length, 1);
  assert.equal(memory.has("10"), false);

  // 연결이 계속 불안정하면 남은 좌표를 포기하고 끝낼 수 있다
  const second = createWalkUseCases(fakeRepository([], [1, 2, 3]), store);
  const started = await second.start("10");
  started.recorder.record(point(0));
  const forced = await second.end(started.walk, started.recorder, { flush: false });
  assert.equal(forced.status, "ENDED");
});

test("#73-S1 새로고침 전에 걷던 산책은 이어 갈 수 있다(보낸 개수도 이어진다)", async () => {
  const memory = new Map<string, ActiveWalk>();
  const store: ActiveWalkStore = {
    load: (petId) => memory.get(petId) ?? null,
    save: (active) => void memory.set(active.walk.petId, active),
    clear: (petId) => void memory.delete(petId),
  };
  const sent: WalkPoint[][] = [];
  const walks = createWalkUseCases(fakeRepository(sent), store);
  const { walk, recorder } = await walks.start("10");
  for (let i = 0; i < 6; i += 1) recorder.record(point(i));
  await recorder.flush();
  recorder.record(point(6));
  walks.remember(walk, recorder);

  const resumed = walks.resume("10");
  assert.ok(resumed);
  assert.equal(resumed.recorder.points.length, 7);
  assert.equal(resumed.recorder.sentCount, 6);
  assert.equal(resumed.recorder.pendingCount, 1);
  assert.equal(walks.resume("99"), null);
});

test("#73-S1 서버에는 petId를 정수로, 좌표는 recordedAt과 함께 보내고 409는 사용자 문구로 바꾼다", async () => {
  const seen: { path: string; body?: unknown }[] = [];
  const repo = new HttpWalkRepository(
    fakeApi(
      {
        post: (path) => {
          if (path === "/maps/walks") return { walkId: 7, startedAt: "2026-11-20T01:00:00Z", status: "IN_PROGRESS" };
          if (path.endsWith("/end")) return { endedAt: "2026-11-20T01:30:00Z", status: "ENDED" };
          return undefined;
        },
      },
      seen,
    ),
  );

  const walk = await repo.start("10");
  assert.deepEqual(walk, { walkId: "7", petId: "10", startedAt: "2026-11-20T01:00:00Z" });
  assert.deepEqual(seen[0], { path: "/maps/walks", body: { petId: 10 } });

  await repo.addPoints("7", [point(0), point(1)]);
  assert.equal(seen[1].path, "/maps/walks/7/points");
  assert.deepEqual(seen[1].body, {
    points: [
      { latitude: point(0).latitude, longitude: point(0).longitude, recordedAt: point(0).recordedAt },
      { latitude: point(1).latitude, longitude: point(1).longitude, recordedAt: point(1).recordedAt },
    ],
  });

  const ended = await repo.end("7");
  assert.equal(seen[2].path, "/maps/walks/7/end");
  assert.deepEqual(ended, { status: "ENDED", endedAt: "2026-11-20T01:30:00Z" });
});

test("#73-S3 서버가 409를 주면 시작은 CONFLICT, 좌표 전송은 WALK_CLOSED로 알린다", async () => {
  const conflict = new ApiError(409, "CONFLICT", "state conflict");
  const repo = new HttpWalkRepository(
    fakeApi({
      post: () => {
        throw conflict;
      },
    }),
  );
  await assert.rejects(
    () => repo.start("10"),
    (error: unknown) => error instanceof ApplicationError && error.code === "CONFLICT",
  );
  await assert.rejects(
    () => repo.addPoints("7", [point(0)]),
    (error: unknown) => error instanceof ApplicationError && error.code === "WALK_CLOSED",
  );
  // 409가 아닌 오류는 그대로 올린다
  const failing = new HttpWalkRepository(
    fakeApi({
      post: () => {
        throw new ApiError(503, "STORAGE_UNAVAILABLE", "잠시 후 다시");
      },
    }),
  );
  await assert.rejects(() => failing.addPoints("7", [point(0)]), (e: unknown) => e instanceof ApiError && e.status === 503);
});

test("#73-S1 데모 산책은 브라우저 메모리에서 시작·기록·끝내고, 같은 아이의 진행 중 산책이 있으면 시작할 수 없다", async () => {
  const demo = new DemoWalkRepository(() => new Date("2026-11-20T01:00:00Z"));
  const walk = await demo.start("uuid-pet");
  assert.match(walk.walkId, /^demo-walk-/);
  await assert.rejects(() => demo.start("uuid-pet"), (e: unknown) => e instanceof ApplicationError && e.code === "CONFLICT");
  await demo.addPoints(walk.walkId, [point(0)]);
  assert.deepEqual(await demo.end(walk.walkId), { status: "ENDED", endedAt: "2026-11-20T01:00:00.000Z" });
  // 끝난 산책에는 좌표를 더 쌓을 수 없다
  await assert.rejects(() => demo.addPoints(walk.walkId, [point(1)]), (e: unknown) => e instanceof ApplicationError && e.code === "WALK_CLOSED");
  // 같은 요청으로 다시 끝내도 시각이 바뀌지 않는다
  assert.equal((await demo.end(walk.walkId)).endedAt, "2026-11-20T01:00:00.000Z");
  // 새로 시작할 수 있다
  assert.ok(await demo.start("uuid-pet"));
});

test("#73-S1 서버 반려동물(양의 정수 id)은 서버로, 데모는 메모리로 보내고 이어지는 호출은 산책 id 접두사로 같은 쪽에 간다", async () => {
  const calls: string[] = [];
  const server: WalkRepository = {
    start: async (petId) => (calls.push(`server.start:${petId}`), { walkId: "9", petId, startedAt: "2026-11-20T01:00:00Z" }),
    addPoints: async (walkId) => void calls.push(`server.points:${walkId}`),
    end: async (walkId) => (calls.push(`server.end:${walkId}`), { status: "ENDED", endedAt: "x" }),
  };
  const demo = new DemoWalkRepository();
  const repo = new PetScopedWalkRepository(server, demo);

  const real = await repo.start("10");
  await repo.addPoints(real.walkId, [point(0)]);
  await repo.end(real.walkId);
  assert.deepEqual(calls, ["server.start:10", "server.points:9", "server.end:9"]);

  for (const id of ["0", "-1", "01", "abc", "6f1c0a3e-3c52-4d8e-9d0e-2a2b4c8f9a10"]) {
    const sample = await repo.start(id);
    assert.match(sample.walkId, /^demo-walk-/, id);
    await repo.end(sample.walkId);
  }
  assert.equal(calls.length, 3, "데모 산책은 서버를 부르지 않는다");
});

test("#73-S1 진행 중인 산책을 브라우저에 남기고 이어 가며, 망가진 값이나 다른 아이의 값은 무시한다", () => {
  const data = new Map<string, string>();
  const storage = {
    getItem: (key: string) => data.get(key) ?? null,
    setItem: (key: string, value: string) => void data.set(key, value),
    removeItem: (key: string) => void data.delete(key),
  };
  const store = new LocalActiveWalkStore(storage);
  const active: ActiveWalk = {
    walk: { walkId: "7", petId: "10", startedAt: "2026-11-20T01:00:00Z" },
    points: [point(0), point(1)],
    sentCount: 1,
  };
  assert.equal(store.load("10"), null);
  store.save(active);
  assert.deepEqual(store.load("10"), active);
  assert.equal(store.load("11"), null, "다른 아이의 산책은 읽지 않는다");

  data.set("pawmento.activeWalk.v1.10", "{깨진 값");
  assert.equal(store.load("10"), null);
  data.set("pawmento.activeWalk.v1.10", JSON.stringify({ ...active, sentCount: 99 }));
  assert.equal(store.load("10"), null, "보낸 개수가 점 개수보다 크면 버린다");

  store.save(active);
  store.clear("10");
  assert.equal(store.load("10"), null);

  // 저장소를 못 쓰는 환경에서도 오류 없이 지나간다
  const broken = new LocalActiveWalkStore({
    getItem: () => {
      throw Error("denied");
    },
    setItem: () => {
      throw Error("denied");
    },
    removeItem: () => {
      throw Error("denied");
    },
  });
  assert.doesNotThrow(() => broken.save(active));
  assert.equal(broken.load("10"), null);
  assert.doesNotThrow(() => broken.clear("10"));
  assert.equal(new LocalActiveWalkStore(null).load("10"), null);
});
