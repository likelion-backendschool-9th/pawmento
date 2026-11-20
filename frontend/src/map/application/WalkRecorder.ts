import {
  isValidWalkPoint,
  MAX_POINTS_PER_REQUEST,
  MAX_POINTS_PER_WALK,
  type WalkPoint,
} from "../domain/Walk.ts";
import type { WalkRepository } from "./walkPorts.ts";

// 걷는 동안 모은 좌표를 서버로 보내는 일을 맡는다. 보내지 못한 좌표는 남겨 두었다가 다음에 다시 보낸다.
// 서버는 같은 시각(recordedAt)의 좌표를 한 번만 쌓으므로, 응답을 못 받고 다시 보내도 중복되지 않는다.
export class WalkRecorder {
  private readonly repository: WalkRepository;
  private readonly walkId: string;
  private readonly all: WalkPoint[];
  private sent: number;
  private flushing: Promise<void> | null = null;

  constructor(
    repository: WalkRepository,
    walkId: string,
    initial: { points: readonly WalkPoint[]; sentCount: number } = {
      points: [],
      sentCount: 0,
    },
  ) {
    this.repository = repository;
    this.walkId = walkId;
    this.all = [...initial.points];
    this.sent = Math.min(initial.sentCount, this.all.length);
  }

  get points(): readonly WalkPoint[] {
    return this.all;
  }

  get sentCount(): number {
    return this.sent;
  }

  get pendingCount(): number {
    return this.all.length - this.sent;
  }

  // 점 하나를 모은다. 잘못된 점, 같은 시각의 점, 산책 한도(5,000개)를 넘는 점은 모으지 않고 false를 돌려준다.
  record(point: WalkPoint): boolean {
    if (!isValidWalkPoint(point)) return false;
    if (this.all.length >= MAX_POINTS_PER_WALK) return false;
    const last = this.all[this.all.length - 1];
    if (last && last.recordedAt === point.recordedAt) return false;
    this.all.push(point);
    return true;
  }

  // 아직 안 보낸 점을 100개씩 나눠 보낸다. 중간에 실패하면 보낸 만큼은 인정하고 나머지는 남기고 오류를 그대로 던진다.
  // 동시에 두 번 불러도 한 번에 하나씩만 보낸다.
  flush(): Promise<void> {
    const run = async () => {
      while (this.sent < this.all.length) {
        const batch = this.all.slice(this.sent, this.sent + MAX_POINTS_PER_REQUEST);
        await this.repository.addPoints(this.walkId, batch);
        this.sent += batch.length;
      }
    };
    const next = (this.flushing ?? Promise.resolve()).catch(() => undefined).then(run);
    this.flushing = next;
    return next;
  }
}
