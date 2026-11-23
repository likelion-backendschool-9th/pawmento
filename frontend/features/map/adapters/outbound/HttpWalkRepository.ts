import { ApplicationError } from "../../../../lib/application/ApplicationError.ts";
import { ApiError, type ApiClient } from "../../../../lib/http/apiClient.ts";
import type { Walk, WalkEnd, WalkPoint, WalkStatus } from "../../domain/Walk.ts";
import type { WalkRepository } from "../../application/walkPorts.ts";

type StartDto = { walkId: number | string; startedAt: string; status: WalkStatus };
type EndDto = { endedAt: string | null; status: WalkStatus };

const conflict = (error: unknown) => error instanceof ApiError && error.status === 409;

export class HttpWalkRepository implements WalkRepository {
  private readonly http: ApiClient;

  constructor(http: ApiClient) {
    this.http = http;
  }

  async start(petId: string): Promise<Walk> {
    try {
      // 서버는 petId를 정수로만 받는다
      const result = await this.http.post<StartDto>("/maps/walks", { petId: Number(petId) });
      return { walkId: String(result.walkId), petId, startedAt: result.startedAt };
    } catch (error) {
      if (conflict(error)) {
        throw new ApplicationError("CONFLICT", "이미 진행 중인 산책이 있어요. 잠시 뒤에 다시 시작해 주세요.");
      }
      throw error;
    }
  }

  async addPoints(walkId: string, points: readonly WalkPoint[]): Promise<void> {
    try {
      await this.http.post<void>(`/maps/walks/${encodeURIComponent(walkId)}/points`, {
        points: points.map((point) => ({
          latitude: point.latitude,
          longitude: point.longitude,
          recordedAt: point.recordedAt,
        })),
      });
    } catch (error) {
      // 끝났거나 5분 넘게 좌표가 없어 중단된 산책이거나 좌표가 5,000개를 넘은 경우다
      if (conflict(error)) {
        throw new ApplicationError("WALK_CLOSED", "산책이 이미 끝났거나 중단되어 더 기록할 수 없어요.");
      }
      throw error;
    }
  }

  async end(walkId: string): Promise<WalkEnd> {
    const result = await this.http.post<EndDto>(`/maps/walks/${encodeURIComponent(walkId)}/end`);
    return { status: result.status, endedAt: result.endedAt };
  }
}
