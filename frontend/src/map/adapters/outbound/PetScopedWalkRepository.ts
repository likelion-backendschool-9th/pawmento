import { isDemoWalkId, isServerPetId, type Walk, type WalkEnd, type WalkPoint } from "../../domain/Walk.ts";
import type { WalkRepository } from "../../application/walkPorts.ts";

export class PetScopedWalkRepository implements WalkRepository {
  private readonly server: WalkRepository;
  private readonly demo: WalkRepository;

  constructor(server: WalkRepository, demo: WalkRepository) {
    this.server = server;
    this.demo = demo;
  }

  start(petId: string): Promise<Walk> {
    return (isServerPetId(petId) ? this.server : this.demo).start(petId);
  }

  // 시작한 쪽이 이어서 처리해야 하므로 산책 id의 접두사로 어디서 시작했는지 가린다
  private byWalk(walkId: string): WalkRepository {
    return isDemoWalkId(walkId) ? this.demo : this.server;
  }

  addPoints(walkId: string, points: readonly WalkPoint[]): Promise<void> {
    return this.byWalk(walkId).addPoints(walkId, points);
  }

  end(walkId: string): Promise<WalkEnd> {
    return this.byWalk(walkId).end(walkId);
  }
}
