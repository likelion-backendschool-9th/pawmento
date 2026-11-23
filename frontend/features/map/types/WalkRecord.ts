import type { WalkPoint, WalkStatus } from "./Walk.ts";

// 날짜별로 다시 보는 산책 하나. 서버의 `GET /maps/walks` 항목과 같은 모양이다.
// 끝내지 않은 산책(중단된 산책 포함)의 endedAt은 null이다.
export type WalkRecord = {
  walkId: string;
  startedAt: string;
  endedAt: string | null;
  status: WalkStatus;
  points: WalkPoint[];
};

// 날짜 막대의 위치: 오늘, 보고 있는 주의 마지막 날, 고른 날
export type WalkDayNavigation = { today: string; weekEnd: string; selected: string };

// 날짜별로 다시 보는 산책
export type WalkDayList = {
  walks: WalkRecord[];
  // 실제 기록이 아닌 화면 확인용 예시 산책이 들어 있다(데모 계정)
  sample: boolean;
};

export type WalkHistoryOptions = {
  // 화면 확인용 예시 산책을 섞는다. 데모 계정에서만 true로 준다(일반 계정의 반려동물에는 섞지 않는다).
  sample?: boolean;
};

export type WalkDays = {
  // 날짜 → 그날의 산책. 불러오지 못한 날은 error에 이유가 있다.
  byDate: Record<string, WalkDayList>;
  failed: string[];
};

export interface WalkHistoryRepository {
  // date는 Asia/Seoul 기준 `YYYY-MM-DD`. 그날 시작한 산책이 startedAt 순서로 나온다.
  listByDate(petId: string, date: string, options?: WalkHistoryOptions): Promise<WalkDayList>;
}
