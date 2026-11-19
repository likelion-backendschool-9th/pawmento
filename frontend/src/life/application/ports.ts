import type { LifeState, Postcard } from "../domain/models.ts";
import type { JournalItem } from "../../workspace/domain/models.ts";
export interface LifeRepository {
  load(): Promise<LifeState>;
  save(state: LifeState): Promise<void>;
}
export interface PostcardGenerator {
  generate(
    records: readonly JournalItem[],
    petName: string,
    petPhotoUrl?: string,
  ): Promise<{
    text: string;
    imageUrl: string;
    demoArtwork?: Postcard["demoArtwork"];
  }>;
}
