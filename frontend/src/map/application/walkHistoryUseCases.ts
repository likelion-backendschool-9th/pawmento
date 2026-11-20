import { ApplicationError } from "../../shared/application/ApplicationError.ts";
import { isValidDate } from "../domain/WalkRecord.ts";
import type { WalkDayList, WalkHistoryRepository } from "./walkPorts.ts";

export type WalkDays = {
  // 날짜 → 그날의 산책. 불러오지 못한 날은 error에 이유가 있다.
  byDate: Record<string, WalkDayList>;
  failed: string[];
};

export function createWalkHistoryUseCases(repository: WalkHistoryRepository) {
  return {
    async listDay(petId: string, date: string): Promise<WalkDayList> {
      if (!petId.trim() || !isValidDate(date)) {
        throw new ApplicationError("VALIDATION", "날짜를 확인해 주세요.");
      }
      return repository.listByDate(petId, date);
    },

    // 주간 막대에 산책이 있는 날을 점으로 보여 주려고 여러 날을 한꺼번에 읽는다. 한 날이 실패해도 나머지는 보여 준다.
    async listDays(petId: string, dates: readonly string[]): Promise<WalkDays> {
      if (!petId.trim() || dates.some((date) => !isValidDate(date))) {
        throw new ApplicationError("VALIDATION", "날짜를 확인해 주세요.");
      }
      const settled = await Promise.allSettled(
        dates.map((date) => repository.listByDate(petId, date)),
      );
      const byDate: Record<string, WalkDayList> = {};
      const failed: string[] = [];
      settled.forEach((result, index) => {
        if (result.status === "fulfilled") byDate[dates[index]] = result.value;
        else failed.push(dates[index]);
      });
      return { byDate, failed };
    },
  };
}
