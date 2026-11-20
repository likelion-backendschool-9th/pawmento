import type { Walk, WalkEnd, WalkPoint } from "../../domain/Walk.ts";
import type { WalkRepository } from "../../application/walkPorts.ts";
import { DEMO_WALK_PREFIX } from "./DemoWalkRepository.ts";

// 서버의 반려동물 id는 양의 정수다. 데모 반려동물은 브라우저에서 만든 UUID라서 서버에 물을 수 없다.
const SERVER_PET_ID = /^[1-9][0-9]*$/;

export class PetScopedWalkRepository implements WalkRepository {
  private readonly server: WalkRepository;
  private readonly demo: WalkRepository;

  constructor(server: WalkRepository, demo: WalkRepository) {
    this.server = server;
    this.demo = demo;
  }

  start(petId: string): Promise<Walk> {
    return (SERVER_PET_ID.test(petId) ? this.server : this.demo).start(petId);
  }

  // 시작한 쪽이 이어서 처리해야 하므로 산책 id의 접두사로 어디서 시작했는지 가린다
  private byWalk(walkId: string): WalkRepository {
    return walkId.startsWith(DEMO_WALK_PREFIX) ? this.demo : this.server;
  }

  addPoints(walkId: string, points: readonly WalkPoint[]): Promise<void> {
    return this.byWalk(walkId).addPoints(walkId, points);
  }

  end(walkId: string): Promise<WalkEnd> {
    return this.byWalk(walkId).end(walkId);
  }
}
