import { ApplicationError } from "../../shared/application/ApplicationError.ts";
import {
  clampBounds,
  isValidBounds,
  type MapBounds,
} from "../domain/MapBounds.ts";
import type { MapPinList, MapPinOptions, MapPinRepository } from "./ports.ts";

export function createMapUseCases(repository: MapPinRepository) {
  return {
    async listPins(
      petId: string,
      bounds: MapBounds,
      options?: MapPinOptions,
    ): Promise<MapPinList> {
      const area = clampBounds(bounds);
      if (!petId.trim() || !isValidBounds(area)) {
        throw new ApplicationError("VALIDATION", "지도 영역을 확인해 주세요.");
      }
      return repository.list(petId, area, options);
    },
  };
}
