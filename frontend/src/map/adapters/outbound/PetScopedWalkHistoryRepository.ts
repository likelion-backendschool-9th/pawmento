import { isServerPetId } from "../../domain/Walk.ts";
import type { WalkDayList, WalkHistoryRepository } from "../../application/walkPorts.ts";

// 서버 반려동물은 서버에서, 브라우저에서 만든 반려동물은 데모 기록에서 읽는다.
export class PetScopedWalkHistoryRepository implements WalkHistoryRepository {
  private readonly server: WalkHistoryRepository;
  private readonly demo: WalkHistoryRepository;

  constructor(server: WalkHistoryRepository, demo: WalkHistoryRepository) {
    this.server = server;
    this.demo = demo;
  }

  listByDate(petId: string, date: string): Promise<WalkDayList> {
    return (isServerPetId(petId) ? this.server : this.demo).listByDate(petId, date);
  }
}
