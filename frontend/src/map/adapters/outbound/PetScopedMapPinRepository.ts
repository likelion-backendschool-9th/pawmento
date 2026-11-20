import type { MapBounds } from "../../domain/MapBounds.ts";
import type { MapPinList, MapPinRepository } from "../../application/ports.ts";

// 서버의 반려동물 id는 양의 정수다. 데모 반려동물은 브라우저에서 만든 UUID라서 서버에 물을 수 없다.
const SERVER_PET_ID = /^[1-9][0-9]*$/;

export class PetScopedMapPinRepository implements MapPinRepository {
  private readonly server: MapPinRepository;
  private readonly demo: MapPinRepository;

  constructor(server: MapPinRepository, demo: MapPinRepository) {
    this.server = server;
    this.demo = demo;
  }

  list(petId: string, bounds: MapBounds): Promise<MapPinList> {
    return (SERVER_PET_ID.test(petId) ? this.server : this.demo).list(
      petId,
      bounds,
    );
  }
}
