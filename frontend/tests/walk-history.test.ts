import test from "node:test";
import assert from "node:assert/strict";
import { ApplicationError } from "../src/shared/application/ApplicationError.ts";
import { ApiError, type ApiClient } from "../src/shared/http/apiClient.ts";
import {
  datesEndingAt,
  formatClock,
  formatHourMinute,
  formatTimeRange,
  formatMinutes,
  formatMonthDay,
  isValidDate,
  seoulDate,
  shiftDate,
  walkEndedAt,
  walkMinutes,
  weekdayLabel,
  type WalkRecord,
} from "../src/map/domain/WalkRecord.ts";
import { fitRoutes } from "../src/map/domain/WalkRoutes.ts";
import { createWalkHistoryUseCases } from "../src/map/application/walkHistoryUseCases.ts";
import type { WalkDayList, WalkHistoryRepository } from "../src/map/application/walkPorts.ts";
import { HttpWalkHistoryRepository } from "../src/map/adapters/outbound/HttpWalkHistoryRepository.ts";
import { PetScopedWalkHistoryRepository } from "../src/map/adapters/outbound/PetScopedWalkHistoryRepository.ts";
import { DemoWalkRepository } from "../src/map/adapters/outbound/DemoWalkRepository.ts";

const NOW = new Date("2026-11-20T03:00:00Z"); // 한국 시각 11월 20일 12:00

const walkAt = (id: string, startedAt: string, endedAt: string | null, lastPoint?: string): WalkRecord => ({
  walkId: id,
  startedAt,
  endedAt,
  status: endedAt ? "ENDED" : "INTERRUPTED",
  points: lastPoint
    ? [
        { latitude: 37.5, longitude: 127, recordedAt: startedAt },
        { latitude: 37.5005, longitude: 127, recordedAt: lastPoint },
      ]
    : [],
});

test("#73-S7 날짜는 한국 시각 기준으로 나누고, 자정을 넘긴 산책도 시작한 날에 속한다", () => {
  assert.equal(seoulDate("2026-11-17T14:50:00Z"), "2026-11-17"); // 한국 시각 23:50
  assert.equal(seoulDate("2026-11-17T15:20:00Z"), "2026-11-18"); // 한국 시각 00:20
  assert.equal(seoulDate("2026-11-17T01:00:00Z"), "2026-11-17");
  assert.equal(shiftDate("2026-11-01", -1), "2026-10-31");
  assert.equal(shiftDate("2026-12-31", 1), "2027-01-01");
  assert.deepEqual(datesEndingAt("2026-11-20", 3), ["2026-11-18", "2026-11-19", "2026-11-20"]);
  assert.equal(weekdayLabel("2026-11-17"), "화");
  assert.equal(formatMonthDay("2026-11-07"), "11월 7일");
});

test("#73-S7 시각은 한국 시각의 오전·오후로, 시간은 분 단위로 적고, 중단된 산책의 끝은 마지막 좌표 시각이다", () => {
  assert.equal(formatClock("2026-11-17T01:00:00Z"), "오전 10:00");
  assert.equal(formatClock("2026-11-17T14:50:00Z"), "오후 11:50");
  assert.equal(formatClock("2026-11-17T15:20:00Z"), "오전 12:20");
  assert.equal(formatClock("2026-11-17T03:05:00Z"), "오후 12:05");
  assert.equal(formatMinutes(42), "42분");
  assert.equal(formatMinutes(60), "1시간");
  assert.equal(formatMinutes(95), "1시간 35분");

  const ended = walkAt("1", "2026-11-17T01:00:00Z", "2026-11-17T01:42:00Z");
  assert.equal(walkEndedAt(ended), "2026-11-17T01:42:00Z");
  assert.equal(walkMinutes(ended), 42);
  const interrupted = walkAt("2", "2026-11-17T14:50:00Z", null, "2026-11-17T15:20:00Z");
  assert.equal(walkEndedAt(interrupted), "2026-11-17T15:20:00Z");
  assert.equal(walkMinutes(interrupted), 30);
  const noPoints = walkAt("3", "2026-11-17T14:50:00Z", null);
  assert.equal(walkEndedAt(noPoints), "2026-11-17T14:50:00Z");
  assert.equal(walkMinutes(noPoints), 0);
});

test("#73-S7 날짜는 YYYY-MM-DD만 받고 달력에 없는 날은 받지 않는다", () => {
  for (const ok of ["2026-11-17", "2028-02-29"]) assert.equal(isValidDate(ok), true, ok);
  for (const bad of ["2026-02-30", "2026-13-01", "2026-1-1", "20261117", "2026-11-17T00:00:00", "", "어제"]) {
    assert.equal(isValidDate(bad), false, bad);
  }
});

function fakeApi(handler: (path: string) => unknown, seen: string[] = []): ApiClient {
  return {
    get: async <T>(path: string) => {
      seen.push(path);
      return handler(path) as T;
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

test("#73-S7 서버에는 petId와 날짜를 쿼리로 보내고, 좌표와 상태를 그대로 받는다(끝내지 않은 산책의 endedAt은 null)", async () => {
  const seen: string[] = [];
  const repo = new HttpWalkHistoryRepository(
    fakeApi(
      () => ({
        walks: [
          {
            walkId: 5,
            startedAt: "2026-11-17T01:00:00Z",
            endedAt: "2026-11-17T01:42:00Z",
            status: "ENDED",
            points: [{ latitude: 37.5, longitude: 127, recordedAt: "2026-11-17T01:00:00Z" }],
          },
          {
            walkId: 6,
            startedAt: "2026-11-17T14:50:00Z",
            endedAt: null,
            status: "INTERRUPTED",
            points: [],
          },
        ],
      }),
      seen,
    ),
  );
  const result = await repo.listByDate("10", "2026-11-17");
  assert.deepEqual(seen, ["/maps/walks?petId=10&date=2026-11-17"]);
  assert.equal(result.sample, false);
  assert.deepEqual(result.walks.map((w) => [w.walkId, w.status, w.endedAt]), [
    ["5", "ENDED", "2026-11-17T01:42:00Z"],
    ["6", "INTERRUPTED", null],
  ]);
  assert.equal(result.walks[0].points.length, 1);
});

test("#73-S8 그날 산책이 없으면 빈 목록이다. 다른 회원의 반려동물을 조회해도 서버가 주는 빈 목록을 그대로 보여 준다(#73-S9)", async () => {
  const repo = new HttpWalkHistoryRepository(fakeApi(() => ({ walks: [] })));
  assert.deepEqual(await repo.listByDate("10", "2026-11-17"), { walks: [], sample: false });
});

test("#73-S7 여러 날을 한꺼번에 읽고, 한 날이 실패해도 나머지는 보여 준다. 잘못된 날짜는 읽지 않는다", async () => {
  const repo: WalkHistoryRepository = {
    listByDate: async (_pet, date): Promise<WalkDayList> => {
      if (date === "2026-11-19") throw new ApiError(503, "STORAGE_UNAVAILABLE", "잠시 후");
      return { walks: date === "2026-11-18" ? [walkAt("1", `${date}T01:00:00Z`, `${date}T01:30:00Z`)] : [], sample: false };
    },
  };
  const history = createWalkHistoryUseCases(repo);
  const days = await history.listDays("10", ["2026-11-18", "2026-11-19", "2026-11-20"]);
  assert.deepEqual(days.failed, ["2026-11-19"]);
  assert.equal(days.byDate["2026-11-18"].walks.length, 1);
  assert.equal(days.byDate["2026-11-20"].walks.length, 0);
  await assert.rejects(
    () => history.listDays("10", ["2026-02-30"]),
    (e: unknown) => e instanceof ApplicationError && e.code === "VALIDATION",
  );
  await assert.rejects(
    () => history.listDay(" ", "2026-11-18"),
    (e: unknown) => e instanceof ApplicationError && e.code === "VALIDATION",
  );
});

test("#73-S7 데모 계정은 오늘 기준 예시 산책을 보여 준다. 3일 전에는 자정을 넘겨 중단된 산책이 함께 나온다", async () => {
  const demo = new DemoWalkRepository(() => NOW);
  const day = await demo.listByDate("uuid-pet", "2026-11-17");
  assert.equal(day.sample, true);
  assert.equal(day.walks.length, 2);
  assert.deepEqual(day.walks.map((w) => w.status), ["ENDED", "INTERRUPTED"]);
  assert.equal(day.walks[1].endedAt, null);
  assert.ok(day.walks[0].startedAt < day.walks[1].startedAt, "startedAt 순서");
  // 중단된 산책은 18일 00시를 넘겨 끝났어도 17일에 속한다
  const last = day.walks[1].points[day.walks[1].points.length - 1].recordedAt;
  assert.equal(seoulDate(day.walks[1].startedAt), "2026-11-17");
  assert.equal(seoulDate(last), "2026-11-18");
  // 점은 시간순이다
  const times = day.walks[0].points.map((p) => p.recordedAt);
  assert.deepEqual(times, [...times].sort());
});

test("#73-S8 예시도 기록도 없는 날은 빈 목록이다", async () => {
  const demo = new DemoWalkRepository(() => NOW);
  assert.deepEqual(await demo.listByDate("uuid-pet", "2026-11-18"), { walks: [], sample: false });
});

test("#73-S7 이 앱에서 기록한 연습 산책이 그날 목록에 들어가고, 다른 반려동물의 산책은 섞이지 않는다", async () => {
  const demo = new DemoWalkRepository(() => NOW);
  const walk = await demo.start("pet-a");
  await demo.addPoints(walk.walkId, [
    { latitude: 37.5, longitude: 127, recordedAt: "2026-11-20T03:00:15Z" },
    { latitude: 37.5003, longitude: 127, recordedAt: "2026-11-20T03:00:30Z" },
  ]);
  let today = await demo.listByDate("pet-a", "2026-11-20");
  assert.deepEqual(today.walks.map((w) => [w.walkId, w.status, w.points.length]), [[walk.walkId, "IN_PROGRESS", 2]]);
  await demo.end(walk.walkId);
  today = await demo.listByDate("pet-a", "2026-11-20");
  assert.equal(today.walks[0].status, "ENDED");
  assert.equal(today.sample, false);
  assert.equal((await demo.listByDate("pet-b", "2026-11-20")).walks.length, 0);
});

test("#73-S7 서버 반려동물(양의 정수 id)은 서버에서, 데모는 데모 기록에서 읽는다", async () => {
  const calls: string[] = [];
  const server: WalkHistoryRepository = {
    listByDate: async (pet, date) => (calls.push(`server:${pet}:${date}`), { walks: [], sample: false }),
  };
  const repo = new PetScopedWalkHistoryRepository(server, new DemoWalkRepository(() => NOW));
  await repo.listByDate("10", "2026-11-17");
  const demo = await repo.listByDate("6f1c0a3e-3c52-4d8e-9d0e-2a2b4c8f9a10", "2026-11-17");
  assert.deepEqual(calls, ["server:10:2026-11-17"]);
  assert.equal(demo.sample, true);
});

test("#73-S7 시작·끝 시각을 \"오전 10:00 – 10:42\"로 적고, 오전·오후가 바뀌면 끝에도 붙인다", () => {
  const same = walkAt("1", "2026-11-17T01:00:00Z", "2026-11-17T01:42:00Z");
  assert.equal(formatTimeRange(same), "오전 10:00 – 10:42");
  const across = walkAt("2", "2026-11-17T14:50:00Z", null, "2026-11-17T15:20:00Z");
  assert.equal(formatTimeRange(across), "오후 11:50 – 오전 12:20");
  assert.equal(formatHourMinute("2026-11-17T15:20:00Z"), "00:20");
});

test("#73-S7 그날의 산책을 한 화면(0~100)에 맞춰 그린다. 비율은 유지하고 점 하나여도 깨지지 않는다", () => {
  const demo = new DemoWalkRepository(() => NOW);
  return demo.listByDate("uuid-pet", "2026-11-17").then((day) => {
    const routes = fitRoutes(day.walks);
    assert.equal(routes.length, 2);
    for (const route of routes) {
      for (const point of route.path) {
        assert.ok(point.x >= 0 && point.x <= 100 && point.y >= 0 && point.y <= 100, JSON.stringify(point));
      }
    }
    assert.equal(routes[0].path.length, day.walks[0].points.length);
    // 위로 올라간 점(위도 증가)은 화면의 위쪽(작은 y)에 놓인다
    const walk = walkAt("n", "2026-11-17T01:00:00Z", "2026-11-17T01:10:00Z", "2026-11-17T01:09:00Z");
    const [north] = fitRoutes([walk]);
    assert.ok(north.path[1].y < north.path[0].y);
    // 점이 하나뿐이어도 숫자가 깨지지 않는다
    const single = fitRoutes([{ ...walk, points: [walk.points[0]] }]);
    assert.ok(Number.isFinite(single[0].path[0].x) && Number.isFinite(single[0].path[0].y));
    assert.deepEqual(fitRoutes([]), []);
    assert.deepEqual(fitRoutes([{ ...walk, points: [] }])[0]?.path ?? [], []);
  });
});
