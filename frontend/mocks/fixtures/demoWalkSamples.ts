import type { WalkPoint } from "../../src/map/domain/Walk.ts";
import { seoulDate, shiftDate, type WalkRecord } from "../../src/map/domain/WalkRecord.ts";

// 데모 계정은 서버 기록이 없어서 화면을 볼 수 있게 서울숲 근처의 예시 산책을 오늘 기준 며칠 전 날짜에 보여 준다.
const CENTER = { latitude: 37.5445, longitude: 127.0374 };
const DEG_PER_METER_LAT = 1 / 111_000;

// 시작 시각(한국 시각)부터 intervalSeconds 간격으로 만든 점들
function route(
  date: string,
  time: string,
  count: number,
  intervalSeconds: number,
  at: (progress: number) => { dx: number; dy: number },
): WalkPoint[] {
  const start = Date.parse(`${date}T${time}:00+09:00`);
  return Array.from({ length: count }, (_, index) => {
    const { dx, dy } = at(index / (count - 1));
    return {
      latitude: CENTER.latitude + dy * DEG_PER_METER_LAT,
      longitude: CENTER.longitude + dx * DEG_PER_METER_LAT / Math.cos((CENTER.latitude * Math.PI) / 180),
      recordedAt: new Date(start + index * intervalSeconds * 1000).toISOString(),
    };
  });
}

// 한 바퀴 도는 길(반지름 약 140m)
const loop = (progress: number) => {
  const angle = progress * Math.PI * 2 - Math.PI / 2;
  return { dx: Math.cos(angle) * 150 - 120, dy: Math.sin(angle) * 130 };
};
// 동쪽으로 휘어 가는 길
const curve = (progress: number) => ({
  dx: 160 + Math.sin(progress * Math.PI * 1.4) * 70 + progress * 40,
  dy: 200 - progress * 380,
});
const line = (progress: number) => ({ dx: -250 + progress * 480, dy: -120 + Math.sin(progress * 6) * 40 });

function walk(
  id: string,
  date: string,
  time: string,
  count: number,
  intervalSeconds: number,
  shape: (progress: number) => { dx: number; dy: number },
  interrupted: boolean,
): WalkRecord {
  const points = route(date, time, count, intervalSeconds, shape);
  return {
    walkId: id,
    startedAt: points[0].recordedAt,
    endedAt: interrupted ? null : new Date(Date.parse(points[points.length - 1].recordedAt) + 15_000).toISOString(),
    status: interrupted ? "INTERRUPTED" : "ENDED",
    points,
  };
}

// 오늘(한국 날짜) 기준으로 1일 전·3일 전·5일 전에 예시 산책을 둔다. 3일 전에는 자정을 넘겨 중단된 산책도 있다.
export function sampleWalksOn(date: string, today: string): WalkRecord[] {
  const day = (offset: number) => shiftDate(today, -offset);
  if (date === day(3)) {
    return [
      walk("demo-sample-a", date, "10:00", 168, 15, loop, false),
      walk("demo-sample-b", date, "23:50", 120, 15, curve, true),
    ];
  }
  if (date === day(1)) return [walk("demo-sample-c", date, "17:30", 140, 15, line, false)];
  if (date === day(5)) return [walk("demo-sample-d", date, "08:10", 100, 15, loop, false)];
  return [];
}

export const todayInSeoul = (now: Date): string => seoulDate(now.getTime());
