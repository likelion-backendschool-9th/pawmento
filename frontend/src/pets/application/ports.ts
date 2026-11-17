import type { PetSummary } from "../domain/PetSummary.ts";

export interface PetListRepository {
  listMine(): Promise<PetSummary[]>;
}
