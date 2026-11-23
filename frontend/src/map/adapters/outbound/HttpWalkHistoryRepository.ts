import type { ApiClient } from "../../../shared/http/apiClient.ts";
import type { WalkDayList, WalkHistoryRepository } from "../../application/walkPorts.ts";
import type { WalkStatus } from "../../domain/Walk.ts";

type WalksDto = {
  walks: {
    walkId: number | string;
    startedAt: string;
    endedAt: string | null;
    status: WalkStatus;
    points: { latitude: number; longitude: number; recordedAt: string }[];
  }[];
};

export class HttpWalkHistoryRepository implements WalkHistoryRepository {
  private readonly http: ApiClient;

  constructor(http: ApiClient) {
    this.http = http;
  }

  async listByDate(petId: string, date: string): Promise<WalkDayList> {
    const query = `petId=${encodeURIComponent(petId)}&date=${encodeURIComponent(date)}`;
    const result = await this.http.get<WalksDto>(`/maps/walks?${query}`);
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
}
