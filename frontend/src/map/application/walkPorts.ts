import type { Walk, WalkEnd, WalkPoint } from "../domain/Walk.ts";

export interface WalkRepository {
  // 산책을 시작한다. 이미 진행 중인 산책이 있으면 ApplicationError("CONFLICT")
  start(petId: string): Promise<Walk>;
  // 좌표를 쌓는다. 산책이 이미 끝났거나 중단됐으면 ApplicationError("WALK_CLOSED")
  addPoints(walkId: string, points: readonly WalkPoint[]): Promise<void>;
  end(walkId: string): Promise<WalkEnd>;
}

// 진행 중인 산책을 새로고침해도 이어 가려고 브라우저에 남겨 두는 값
export type ActiveWalk = {
  walk: Walk;
  points: WalkPoint[];
  // points 앞쪽 몇 개를 서버에 보냈는지
  sentCount: number;
};

// 진행 중인 산책은 로그인한 계정(owner)별로 따로 둔다. 같은 반려동물을 여러 계정이 쓰더라도 서로의 경로가 섞이지 않는다.
export interface ActiveWalkStore {
  load(owner: string, petId: string): ActiveWalk | null;
  save(owner: string, active: ActiveWalk): void;
  clear(owner: string, petId: string): void;
}

// 날짜별로 다시 보는 산책
export type WalkDayList = {
  walks: import("../domain/WalkRecord.ts").WalkRecord[];
  // 실제 기록이 아닌 화면 확인용 예시 산책이 들어 있다(데모 계정)
  sample: boolean;
};

export interface WalkHistoryRepository {
  // date는 Asia/Seoul 기준 `YYYY-MM-DD`. 그날 시작한 산책이 startedAt 순서로 나온다.
  listByDate(petId: string, date: string): Promise<WalkDayList>;
}
