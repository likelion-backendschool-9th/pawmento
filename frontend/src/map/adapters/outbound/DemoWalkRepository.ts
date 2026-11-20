import { ApplicationError } from "../../../shared/application/ApplicationError.ts";
import { DEMO_WALK_PREFIX, type Walk, type WalkEnd, type WalkPoint } from "../../domain/Walk.ts";
import type {
  WalkDayList,
  WalkHistoryOptions,
  WalkHistoryRepository,
  WalkRepository,
} from "../../application/walkPorts.ts";
import { seoulDate, type WalkRecord } from "../../domain/WalkRecord.ts";
import { sampleWalksOn, todayInSeoul } from "./demoWalkSamples.ts";

// 데모 산책은 브라우저 메모리에만 둔다(새로고침하면 사라진다).

type DemoWalk = {
  petId: string;
  startedAt: string;
  endedAt: string | null;
  points: WalkPoint[];
};

export class DemoWalkRepository implements WalkRepository, WalkHistoryRepository {
  private readonly walks = new Map<string, DemoWalk>();
  private sequence = 0;
  private readonly now: () => Date;

  constructor(now: () => Date = () => new Date()) {
    this.now = now;
  }

  async start(petId: string): Promise<Walk> {
    for (const walk of this.walks.values()) {
      // 진행 중인 산책은 반려동물 한 마리에 하나다. 다른 반려동물의 산책은 막지 않는다.
      if (walk.petId === petId && walk.endedAt === null) {
        throw new ApplicationError("CONFLICT", "이미 진행 중인 산책이 있어요.");
      }
    }
    this.sequence += 1;
    const walkId = `${DEMO_WALK_PREFIX}${this.sequence}`;
    const startedAt = this.now().toISOString();
    this.walks.set(walkId, { petId, startedAt, endedAt: null, points: [] });
    return { walkId, petId, startedAt };
  }

  async addPoints(walkId: string, points: readonly WalkPoint[]): Promise<void> {
    const walk = this.walks.get(walkId);
    // 새로고침으로 메모리가 비면 이어 갈 수 없다
    if (!walk || walk.endedAt !== null) {
      throw new ApplicationError("WALK_CLOSED", "산책이 이미 끝났거나 중단되어 더 기록할 수 없어요.");
    }
    walk.points.push(...points);
  }

  async end(walkId: string): Promise<WalkEnd> {
    const walk = this.walks.get(walkId);
    if (!walk) return { status: "INTERRUPTED", endedAt: null };
    walk.endedAt ??= this.now().toISOString();
    return { status: "ENDED", endedAt: walk.endedAt };
  }

  // 이 앱에서 기록한 연습 산책을 그날 기준으로 돌려준다. 화면 확인용 예시 산책은 데모 계정(options.sample)에만 섞는다.
  async listByDate(
    petId: string,
    date: string,
    options: WalkHistoryOptions = {},
  ): Promise<WalkDayList> {
    const mine: WalkRecord[] = [...this.walks.entries()]
      .filter(([, walk]) => walk.petId === petId && seoulDate(walk.startedAt) === date)
      .map(([walkId, walk]) => ({
        walkId,
        startedAt: walk.startedAt,
        endedAt: walk.endedAt,
        status: walk.endedAt === null ? ("IN_PROGRESS" as const) : ("ENDED" as const),
        points: [...walk.points],
      }));
    const samples = options.sample
      ? sampleWalksOn(date, todayInSeoul(this.now()))
      : [];
    const walks = [...samples, ...mine].sort((a, b) => a.startedAt.localeCompare(b.startedAt));
    return { walks, sample: samples.length > 0 };
  }
}
