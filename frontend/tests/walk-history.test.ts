import test from "node:test";
import assert from "node:assert/strict";
import { ApplicationError } from "../lib/application/ApplicationError.ts";
import { ApiError, type ApiClient } from "../lib/http/apiClient.ts";
import { createWalkHistoryApi } from "../features/map/api/walkHistoryApi.ts";
import { createMapSource } from "../features/map/source.ts";
import type { WalkRecord } from "../features/map/types/WalkRecord.ts";
import {
  datesEndingAt,
  followToday,
  formatClock,
  formatHourMinute,
  formatMinutes,
  formatMonthDay,
  formatTimeRange,
  initialNavigation,
  isValidDate,
  msUntilNextSeoulMidnight,
  seoulDate,
  shiftDate,
  walkEndedAt,
  walkHistoryPath,
  walkMinutes,
  weekdayLabel,
} from "../features/map/utils/walkRecord.ts";
import { fitPoints, fitRoutes, routeMarks } from "../features/map/utils/walkRoutes.ts";
import { DemoMapPinRepository } from "../mocks/handlers/DemoMapPinRepository.ts";
import { DemoWalkRepository } from "../mocks/handlers/DemoWalkRepository.ts";

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

test("#73-S7 서버에는 petId와 날짜를 쿼리로 보내고, 좌표와 상태를 그대로 받는다(끝내지 않은 산책의 endedAt은 null)", async () => {
  const seen: string[] = [];
  const history = createWalkHistoryApi(
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
  const result = await history.listDay("10", "2026-11-17");
  assert.deepEqual(seen, ["/maps/walks?petId=10&date=2026-11-17"]);
  assert.equal(result.sample, false);
  assert.deepEqual(result.walks.map((w) => [w.walkId, w.status, w.endedAt]), [
    ["5", "ENDED", "2026-11-17T01:42:00Z"],
    ["6", "INTERRUPTED", null],
  ]);
  assert.equal(result.walks[0].points.length, 1);
});

test("#73-S8 그날 산책이 없으면 빈 목록이다. 다른 회원의 반려동물을 조회해도 서버가 주는 빈 목록을 그대로 보여 준다(#73-S9)", async () => {
  const history = createWalkHistoryApi(fakeApi(() => ({ walks: [] })));
  assert.deepEqual(await history.listDay("10", "2026-11-17"), { walks: [], sample: false });
});

test("#73-S7 여러 날을 한꺼번에 읽고, 한 날이 실패해도 나머지는 보여 준다. 잘못된 날짜는 읽지 않는다", async () => {
  const history = createWalkHistoryApi(
    fakeApi((path) => {
      const date = new URL(path, "http://local").searchParams.get("date");
      if (date === "2026-11-19") throw new ApiError(503, "STORAGE_UNAVAILABLE", "잠시 후");
      return {
        walks:
          date === "2026-11-18"
            ? [{
                walkId: 1,
                startedAt: `${date}T01:00:00Z`,
                endedAt: `${date}T01:30:00Z`,
                status: "ENDED",
                points: [],
              }]
            : [],
      };
    }),
  );
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
  const day = await demo.listByDate("uuid-pet", "2026-11-17", { sample: true });
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
  assert.deepEqual(await demo.listByDate("uuid-pet", "2026-11-18", { sample: true }), { walks: [], sample: false });
});

test("#73-S9 일반 계정의 브라우저 반려동물에는 예시 산책을 섞지 않는다. 데모 계정에만 섞는다", async () => {
  const demo = new DemoWalkRepository(() => NOW);
  // 예시가 있는 날(3일 전)이어도 옵션이 없으면 빈 목록이다
  assert.deepEqual(await demo.listByDate("ordinary-pet", "2026-11-17"), { walks: [], sample: false });
  assert.deepEqual(await demo.listByDate("ordinary-pet", "2026-11-17", { sample: false }), { walks: [], sample: false });
  assert.equal((await demo.listByDate("demo-pet", "2026-11-17", { sample: true })).walks.length, 2);
  // 일반 계정이 직접 기록한 연습 산책은 그대로 나온다
  const walk = await demo.start("ordinary-pet");
  await demo.addPoints(walk.walkId, [{ latitude: 37.5, longitude: 127, recordedAt: "2026-11-20T03:00:15Z" }]);
  const today = await demo.listByDate("ordinary-pet", "2026-11-20");
  assert.deepEqual(today.walks.map((w) => w.walkId), [walk.walkId]);
  assert.equal(today.sample, false);
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
  const history = createMapSource({
    pins: { listPins: async () => ({ pins: [], truncated: false, sample: false }) },
    walks: {
      start: async (petId) => ({ walkId: "x", petId, startedAt: "t" }),
      addPoints: async () => {},
      end: async () => ({ status: "ENDED" as const, endedAt: null }),
    },
    history: {
      listDay: async (pet, date) => (calls.push(`server:${pet}:${date}`), { walks: [], sample: false }),
      listDays: async () => ({ byDate: {}, failed: [] }),
    },
    demoPins: new DemoMapPinRepository(),
    demoWalks: new DemoWalkRepository(() => NOW),
  }).walkHistory;
  await history.listDay("10", "2026-11-17", { sample: true });
  const demo = await history.listDay("6f1c0a3e-3c52-4d8e-9d0e-2a2b4c8f9a10", "2026-11-17", { sample: true });
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
  return demo.listByDate("uuid-pet", "2026-11-17", { sample: true }).then((day) => {
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

test("#73-S7 한국 자정까지 남은 시간을 계산한다", () => {
  const hour = 60 * 60 * 1000;
  // 한국 시각 11월 20일 23:59:00 = UTC 14:59:00 → 1분 남음
  assert.equal(msUntilNextSeoulMidnight(Date.parse("2026-11-20T14:59:00Z")), 60_000);
  // 한국 시각 11월 21일 00:00:00 정각이면 다음 자정까지 24시간
  assert.equal(msUntilNextSeoulMidnight(Date.parse("2026-11-20T15:00:00Z")), 24 * hour);
  assert.equal(msUntilNextSeoulMidnight(Date.parse("2026-11-20T03:00:00Z")), 12 * hour);
});

test("#73-S7 한국 날짜가 바뀌면 오늘과 다음 주 상한을 옮긴다. 오늘을 보고 있었다면 새 오늘로 따라간다", () => {
  const before = { today: "2026-11-20", weekEnd: "2026-11-20", selected: "2026-11-20" };
  // 같은 날이면 그대로(같은 객체)
  assert.equal(followToday(before, "2026-11-20"), before);
  // 자정 뒤: 오늘을 보고 있었으므로 새 오늘로 이동하고, 21일을 고를 수 있다
  assert.deepEqual(followToday(before, "2026-11-21"), {
    today: "2026-11-21",
    weekEnd: "2026-11-21",
    selected: "2026-11-21",
  });
});

test("#73-S7 과거를 보고 있던 경우 날짜가 바뀌어도 보던 날을 유지하고, 범위 밖으로 밀리면 새 오늘로 옮긴다", () => {
  // 20일 기준 이전 주(14~...)를 보던 중
  const past = { today: "2026-11-20", weekEnd: "2026-11-13", selected: "2026-11-11" };
  assert.deepEqual(followToday(past, "2026-11-21"), { today: "2026-11-21", weekEnd: "2026-11-13", selected: "2026-11-11" });
  // 오늘을 보는 주에서 맨 앞 날(14일)을 골랐는데 21일이 되면 주가 15~21로 바뀌어 14일은 범위 밖
  const first = { today: "2026-11-20", weekEnd: "2026-11-20", selected: "2026-11-14" };
  assert.deepEqual(followToday(first, "2026-11-21"), { today: "2026-11-21", weekEnd: "2026-11-21", selected: "2026-11-21" });
  // 범위 안의 날을 골랐다면 그대로 둔다
  const inside = { today: "2026-11-20", weekEnd: "2026-11-20", selected: "2026-11-18" };
  assert.equal(followToday(inside, "2026-11-21").selected, "2026-11-18");
});

test("#73-S7 예시 지도와 네이버 지도가 같은 상태별 표시를 따른다: 끝남·진행 중은 출발점, 중단은 \"중단\" 표시", () => {
  assert.deepEqual(routeMarks("ENDED"), { startDot: true, pausedTag: false });
  assert.deepEqual(routeMarks("IN_PROGRESS"), { startDot: true, pausedTag: false });
  assert.deepEqual(routeMarks("INTERRUPTED"), { startDot: false, pausedTag: true });
});

test("#73-S7 사용 사례는 예시 옵션을 저장소에 그대로 전달한다", async () => {
  const seen: unknown[] = [];
  const history = createMapSource({
    pins: { listPins: async () => ({ pins: [], truncated: false, sample: false }) },
    walks: {
      start: async (petId) => ({ walkId: "x", petId, startedAt: "t" }),
      addPoints: async () => {},
      end: async () => ({ status: "ENDED" as const, endedAt: null }),
    },
    history: {
      listDay: async (_pet, _date, options) => (seen.push(options), { walks: [], sample: false }),
      listDays: async (_pet, _dates, options) => (seen.push(options), { byDate: {}, failed: [] }),
    },
    demoPins: new DemoMapPinRepository(),
    demoWalks: new DemoWalkRepository(),
  }).walkHistory;
  await history.listDays("10", ["2026-11-18"], { sample: true });
  await history.listDay("10", "2026-11-18");
  assert.deepEqual(seen, [{ sample: true }, undefined]);
});

test("#73-S15 걷는 중인 길은 출발점부터 지금 위치까지 한 화면(0~100)에 맞춰 그린다. 점이 하나여도 깨지지 않는다", () => {
  assert.deepEqual(fitPoints([]), []);
  // 북쪽으로 24걸음(위도 증가) + 동쪽으로 조금 걷는 길
  const path = Array.from({ length: 25 }, (_, i) => ({
    latitude: 37.5445 + i * 0.00012,
    longitude: 127.0374 + Math.sin(i / 3) * 0.0003,
  }));
  const view = fitPoints(path);
  assert.equal(view.length, 25);
  for (const point of view) {
    assert.ok(point.x >= 0 && point.x <= 100 && point.y >= 0 && point.y <= 100, JSON.stringify(point));
  }
  // 출발점(첫 점)은 아래쪽, 지금 위치(마지막 점)는 위쪽에 놓인다(북쪽이 위)
  assert.ok(view[view.length - 1].y < view[0].y);
  // 점이 하나뿐이어도 숫자가 깨지지 않는다
  const single = fitPoints([path[0]]);
  assert.ok(Number.isFinite(single[0].x) && Number.isFinite(single[0].y));
  // 길이 계속 늘어나도 항상 화면 안에 들어온다(걸을수록 줄어든다)
  const longer = fitPoints([...path, { latitude: 37.5445 + 0.02, longitude: 127.0374 }]);
  assert.ok(longer.every((p) => p.y >= 0 && p.y <= 100));
});

test("#73-S16 산책을 끝낸 뒤 열 날짜는 산책이 시작한 한국 날짜다. 자정을 넘겨 끝나도 시작한 날을 연다", () => {
  // 11월 17일 23:50(한국)에 시작해 18일 00:10에 끝낸 산책
  const startedAt = "2026-11-17T14:50:00Z";
  assert.equal(seoulDate(startedAt), "2026-11-17");
  assert.equal(seoulDate("2026-11-17T15:10:00Z"), "2026-11-18", "끝난 시각은 18일이다");
  assert.equal(walkHistoryPath("10", seoulDate(startedAt)), "/pets/10/map/walks/2026-11-17");
  // 날짜가 없거나 올바르지 않으면 날짜 없는 주소(오늘)로 간다
  for (const bad of [null, undefined, "", "2026-02-30", "어제", "2026-11-17T00:00:00"]) {
    assert.equal(walkHistoryPath("10", bad), "/pets/10/map/walks", String(bad));
  }
});

test("#73-S16 산책 길 화면은 요청한 날짜가 올바르고 오늘을 넘지 않을 때만 그날을 열고, 아니면 오늘을 연다", () => {
  const today = "2026-11-18";
  assert.deepEqual(initialNavigation(today, "2026-11-17"), { today, weekEnd: "2026-11-17", selected: "2026-11-17" });
  assert.deepEqual(initialNavigation(today, "2026-11-18"), { today, weekEnd: today, selected: today });
  for (const bad of [null, undefined, "", "2026-11-19", "2026-02-30", "abc"]) {
    assert.deepEqual(initialNavigation(today, bad), { today, weekEnd: today, selected: today }, String(bad));
  }
});
