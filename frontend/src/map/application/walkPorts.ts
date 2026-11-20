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

export interface ActiveWalkStore {
  load(petId: string): ActiveWalk | null;
  save(active: ActiveWalk): void;
  clear(petId: string): void;
}
