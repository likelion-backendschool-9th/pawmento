import { ApplicationError } from "../../../lib/application/ApplicationError.ts";
import type { ApiClient } from "../../../lib/http/apiClient.ts";
import type { WalkStatus } from "../types/Walk.ts";
import type { WalkDayList, WalkDays } from "../types/WalkRecord.ts";
import { isValidDate } from "../utils/walkRecord.ts";

type WalksDto = {
  walks: {
    walkId: number | string;
    startedAt: string;
    endedAt: string | null;
    status: WalkStatus;
    points: { latitude: number; longitude: number; recordedAt: string }[];
  }[];
};

function assertWalkQuery(petId: string, dates: readonly string[]) {
  if (!petId.trim() || dates.some((date) => !isValidDate(date))) {
    throw new ApplicationError("VALIDATION", "날짜를 확인해 주세요.");
  }
}

export function assertWalkDay(petId: string, date: string) {
  assertWalkQuery(petId, [date]);
}

// 여러 날을 한꺼번에 읽는다. 한 날이 실패해도 나머지는 보여 준다.
export async function loadWalkDays(
  petId: string,
  dates: readonly string[],
  load: (date: string) => Promise<WalkDayList>,
): Promise<WalkDays> {
  assertWalkQuery(petId, dates);
  const settled = await Promise.allSettled(dates.map((date) => load(date)));
  const byDate: Record<string, WalkDayList> = {};
  const failed: string[] = [];
  settled.forEach((result, index) => {
    if (result.status === "fulfilled") byDate[dates[index]] = result.value;
    else failed.push(dates[index]);
  });
  return { byDate, failed };
}

async function requestDay(http: ApiClient, petId: string, date: string): Promise<WalkDayList> {
  const query = `petId=${encodeURIComponent(petId)}&date=${encodeURIComponent(date)}`;
  const result = await http.get<WalksDto>(`/maps/walks?${query}`);
  return {
    walks: result.walks.map((walk) => ({
      walkId: String(walk.walkId),
      startedAt: walk.startedAt,
      endedAt: walk.endedAt,
      status: walk.status,
      points: walk.points.map((point) => ({
        latitude: point.latitude,
        longitude: point.longitude,
        recordedAt: point.recordedAt,
      })),
    })),
    sample: false,
  };
}

export function createWalkHistoryApi(http: ApiClient) {
  return {
    async listDay(petId: string, date: string): Promise<WalkDayList> {
      assertWalkDay(petId, date);
      return requestDay(http, petId, date);
    },

    listDays(petId: string, dates: readonly string[]): Promise<WalkDays> {
      return loadWalkDays(petId, dates, (date) => requestDay(http, petId, date));
    },
  };
}
