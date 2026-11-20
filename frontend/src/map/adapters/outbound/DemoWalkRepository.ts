import { ApplicationError } from "../../../shared/application/ApplicationError.ts";
import { DEMO_WALK_PREFIX, type Walk, type WalkEnd, type WalkPoint } from "../../domain/Walk.ts";
import type { WalkRepository } from "../../application/walkPorts.ts";

// 데모 산책은 브라우저 메모리에만 둔다(새로고침하면 사라진다).

type DemoWalk = { endedAt: string | null; count: number };

export class DemoWalkRepository implements WalkRepository {
  private readonly walks = new Map<string, DemoWalk>();
  private sequence = 0;
  private readonly now: () => Date;

  constructor(now: () => Date = () => new Date()) {
    this.now = now;
  }

  async start(petId: string): Promise<Walk> {
    for (const walk of this.walks.values()) {
      if (walk.endedAt === null) {
        throw new ApplicationError("CONFLICT", "이미 진행 중인 산책이 있어요.");
      }
    }
    this.sequence += 1;
    const walkId = `${DEMO_WALK_PREFIX}${this.sequence}`;
    this.walks.set(walkId, { endedAt: null, count: 0 });
    return { walkId, petId, startedAt: this.now().toISOString() };
  }

  async addPoints(walkId: string, points: readonly WalkPoint[]): Promise<void> {
    const walk = this.walks.get(walkId);
    // 새로고침으로 메모리가 비면 이어 갈 수 없다
    if (!walk || walk.endedAt !== null) {
      throw new ApplicationError("WALK_CLOSED", "산책이 이미 끝났거나 중단되어 더 기록할 수 없어요.");
    }
    walk.count += points.length;
  }

  async end(walkId: string): Promise<WalkEnd> {
    const walk = this.walks.get(walkId);
    if (!walk) return { status: "INTERRUPTED", endedAt: null };
    walk.endedAt ??= this.now().toISOString();
    return { status: "ENDED", endedAt: walk.endedAt };
  }
}
