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
  DEMO_WALK_PREFIX,
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

function fakeRepository(
  sent: WalkPoint[][] = [],
  failOn: number[] = [],
  closedOn: number[] = [],
  endStatus: "ENDED" | "INTERRUPTED" = "ENDED",
): WalkRepository & { calls: number } {
  const repo = {
    calls: 0,
    start: async (petId: string) => ({ walkId: "5", petId, startedAt: "2026-11-20T01:00:00Z" }),
    addPoints: async (_walkId: string, points: readonly WalkPoint[]) => {
      repo.calls += 1;
      if (failOn.includes(repo.calls)) throw new ApplicationError("NETWORK", "연결이 끊겼어요.");
      if (closedOn.includes(repo.calls)) throw new ApplicationError("WALK_CLOSED", "이미 끝난 산책이에요.");
      sent.push([...points]);
    },
    end: async () => ({
      status: endStatus,
      endedAt: endStatus === "ENDED" ? "2026-11-20T01:30:00Z" : null,
    }),
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


test("#73-S6 같은 시각의 점은 모으지 않는다", () => {
  const recorder = new WalkRecorder(fakeRepository(), "5");
  assert.equal(recorder.record(point(0)), true);
  assert.equal(recorder.record(point(0, { latitude: 37.6 })), false, "같은 시각");
  assert.equal(recorder.points.length, 1);
});

test("#73-S14 산책 한도(5,000개)를 넘는 점은 모으지 않는다", () => {
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


function memoryStore() {
  const memory = new Map<string, ActiveWalk>();
  const store: ActiveWalkStore = {
    load: (owner, petId) => memory.get(`${owner}:${petId}`) ?? null,
    save: (owner, active) => void memory.set(`${owner}:${active.walk.petId}`, active),
    clear: (owner, petId) => void memory.delete(`${owner}:${petId}`),
  };
  return { memory, store };
}

test("#73-S2 산책을 끝낼 때 남은 좌표를 먼저 보내고, 못 보내면 끝내지 않는다", async () => {
  const sent: WalkPoint[][] = [];
  const { memory, store } = memoryStore();
  const walks = createWalkUseCases(fakeRepository(sent, [1]), store);

  const { walk, recorder } = await walks.start("u1", "10");
  assert.equal(memory.get("u1:10")?.walk.walkId, "5", "시작하면 이어 갈 수 있게 남긴다");
  recorder.record(point(0));
  walks.remember("u1", walk, recorder);
  assert.equal(memory.get("u1:10")?.points.length, 1);

  // 첫 전송이 실패하므로 끝내기도 실패하고 산책 기록은 남는다
  await assert.rejects(() => walks.end("u1", walk, recorder), /연결이 끊겼어요/);
  assert.ok(memory.has("u1:10"));
  // 다시 끝내면 남은 좌표를 보내고 끝난다
  const result = await walks.end("u1", walk, recorder);
  assert.equal(result.status, "ENDED");
  assert.equal(result.complete, true);
  assert.equal(sent.flat().length, 1);
  assert.equal(memory.has("u1:10"), false);

  // 연결이 계속 불안정하면 남은 좌표를 포기하고 끝낼 수 있다. 이때는 일부가 저장되지 않았다고 알린다
  const second = createWalkUseCases(fakeRepository([], [1, 2, 3]), store);
  const started = await second.start("u1", "10");
  started.recorder.record(point(0));
  const forced = await second.end("u1", started.walk, started.recorder, { flush: false });
  assert.equal(forced.status, "ENDED");
  assert.equal(forced.complete, false);
});

test("#73-S3 서버가 이미 닫은 산책이면 더 모으거나 보내지 않고, 종료 결과(INTERRUPTED)를 그대로 알린다", async () => {
  const sent: WalkPoint[][] = [];
  const repo = fakeRepository(sent, [], [1], "INTERRUPTED"); // 첫 전송이 WALK_CLOSED(409)
  const { memory, store } = memoryStore();
  const walks = createWalkUseCases(repo, store);
  const { walk, recorder } = await walks.start("u1", "10");
  recorder.record(point(0));

  await assert.rejects(
    () => recorder.flush(),
    (e: unknown) => e instanceof ApplicationError && e.code === "WALK_CLOSED",
  );
  assert.equal(recorder.closed, true);
  assert.equal(recorder.record(point(1)), false, "닫힌 산책에는 더 모으지 않는다");
  await recorder.flush(); // 다시 보내려 하지 않는다
  assert.equal(repo.calls, 1);

  // 끝내기는 같은 전송을 되풀이하지 않고 종료 응답만 받는다
  const result = await walks.end("u1", walk, recorder);
  assert.equal(repo.calls, 1);
  assert.equal(result.status, "INTERRUPTED");
  assert.equal(result.endedAt, null);
  assert.equal(result.complete, false, "못 보낸 좌표가 남았다");
  assert.equal(memory.has("u1:10"), false);
});

test("#73-S1 새로고침 전에 걷던 산책은 이어 갈 수 있다(보낸 개수도 이어진다)", async () => {
  const { store } = memoryStore();
  const sent: WalkPoint[][] = [];
  const walks = createWalkUseCases(fakeRepository(sent), store);
  const { walk, recorder } = await walks.start("u1", "10");
  for (let i = 0; i < 6; i += 1) recorder.record(point(i));
  await recorder.flush();
  recorder.record(point(6));
  walks.remember("u1", walk, recorder);

  const resumed = walks.resume("u1", "10");
  assert.ok(resumed);
  assert.equal(resumed.recorder.points.length, 7);
  assert.equal(resumed.recorder.sentCount, 6);
  assert.equal(resumed.recorder.pendingCount, 1);
  assert.equal(walks.resume("u1", "99"), null);
  assert.equal(walks.resume("u2", "10"), null, "다른 계정의 진행 중 산책은 이어 가지 않는다");
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


test("#73-S12 이미 진행 중인 산책이 있어 서버가 409를 주면 시작은 CONFLICT로 알린다", async () => {
  const repo = new HttpWalkRepository(
    fakeApi({
      post: () => {
        throw new ApiError(409, "CONFLICT", "state conflict");
      },
    }),
  );
  await assert.rejects(
    () => repo.start("10"),
    (error: unknown) => error instanceof ApplicationError && error.code === "CONFLICT",
  );
});

test("#73-S3 끝났거나 중단된 산책에 좌표를 보내 409를 받으면 WALK_CLOSED로 알리고, 다른 오류는 그대로 올린다", async () => {
  const repo = new HttpWalkRepository(
    fakeApi({
      post: () => {
        throw new ApiError(409, "CONFLICT", "state conflict");
      },
    }),
  );
  await assert.rejects(
    () => repo.addPoints("7", [point(0)]),
    (error: unknown) => error instanceof ApplicationError && error.code === "WALK_CLOSED",
  );
  const failing = new HttpWalkRepository(
    fakeApi({
      post: () => {
        throw new ApiError(503, "STORAGE_UNAVAILABLE", "잠시 후 다시");
      },
    }),
  );
  await assert.rejects(() => failing.addPoints("7", [point(0)]), (e: unknown) => e instanceof ApiError && e.status === 503);
});

test("#73-S12 데모 산책은 브라우저 메모리에서 시작·기록·끝내고(S2), 같은 아이의 진행 중 산책이 있으면 시작할 수 없다", async () => {
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


test("#73-S1 진행 중인 서버 산책을 계정별로 브라우저에 남기고 이어 가며, 망가진 값이나 다른 아이·계정의 값은 무시한다", () => {
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
  assert.equal(store.load("u1", "10"), null);
  store.save("u1", active);
  assert.deepEqual(store.load("u1", "10"), active);
  assert.equal(store.load("u1", "11"), null, "다른 아이의 산책은 읽지 않는다");
  assert.equal(store.load("u2", "10"), null, "같은 아이를 쓰는 다른 계정의 산책은 읽지 않는다");

  const storedKey = [...data.keys()][0];
  data.set(storedKey, "{깨진 값");
  assert.equal(store.load("u1", "10"), null);
  data.set(storedKey, JSON.stringify({ ...active, sentCount: 99 }));
  assert.equal(store.load("u1", "10"), null, "보낸 개수가 점 개수보다 크면 버린다");

  store.save("u1", active);
  store.clear("u1", "10");
  assert.equal(store.load("u1", "10"), null);

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
  assert.doesNotThrow(() => broken.save("u1", active));
  assert.equal(broken.load("u1", "10"), null);
  assert.doesNotThrow(() => broken.clear("u1", "10"));
  assert.equal(new LocalActiveWalkStore(null).load("u1", "10"), null);
});

test("#73-S1 데모 산책은 데모 저장소처럼 메모리에만 남기고, 브라우저 저장소에 남은 데모 값은 복원하지 않는다", () => {
  const data = new Map<string, string>();
  const storage = {
    getItem: (key: string) => data.get(key) ?? null,
    setItem: (key: string, value: string) => void data.set(key, value),
    removeItem: (key: string) => void data.delete(key),
  };
  const demo: ActiveWalk = {
    walk: { walkId: `${DEMO_WALK_PREFIX}1`, petId: "uuid-pet", startedAt: "2026-11-20T01:00:00Z" },
    points: [point(0)],
    sentCount: 0,
  };
  const store = new LocalActiveWalkStore(storage);
  store.save("u1", demo);
  assert.equal(data.size, 0, "데모 산책은 localStorage에 쓰지 않는다");
  assert.deepEqual(store.load("u1", "uuid-pet"), demo, "같은 앱 안에서는 이어 갈 수 있다");
  assert.equal(new LocalActiveWalkStore(storage).load("u1", "uuid-pet"), null, "새로고침(새 저장소)하면 데모 산책도 사라진다");

  // 예전에 저장소에 남은 데모 값은 복원 대상이 아니다
  data.set("pawmento.activeWalk.v2.u1.uuid-pet", JSON.stringify(demo));
  assert.equal(new LocalActiveWalkStore(storage).load("u1", "uuid-pet"), null);

  store.clear("u1", "uuid-pet");
  assert.equal(store.load("u1", "uuid-pet"), null);
});
