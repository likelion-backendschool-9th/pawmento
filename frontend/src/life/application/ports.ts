import type { LifeState } from "../domain/models.ts";
import type { Memory } from "../../workspace/domain/models.ts";
export interface LifeRepository {
  load(): Promise<LifeState>;
  save(state: LifeState): Promise<void>;
}
export interface PostcardGenerator {
  generate(
    records: readonly Memory[],
    petName: string,
  ): Promise<{ text: string; imageUrl: string }>;
}
