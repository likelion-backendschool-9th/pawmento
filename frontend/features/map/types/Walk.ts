import type { MapPoint } from "./MapBounds.ts";

// 산책 하나. 상태는 서버가 마지막 좌표를 받은 시각으로 계산한다(5분 동안 좌표가 없으면 INTERRUPTED).
export type WalkStatus = "IN_PROGRESS" | "ENDED" | "INTERRUPTED";

export type Walk = {
  walkId: string;
  petId: string;
  startedAt: string;
};

export type WalkEnd = {
  status: WalkStatus;
  endedAt: string | null;
};

// 산책 경로의 점 하나. recordedAt은 UTC ISO 시각이다(서버는 같은 시각의 점을 한 번만 쌓는다).
export type WalkPoint = MapPoint & { recordedAt: string };

// 끝낸 결과. complete가 false면 서버에 저장하지 못한 좌표가 남아 있다.
export type WalkOutcome = WalkEnd & { complete: boolean };

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
