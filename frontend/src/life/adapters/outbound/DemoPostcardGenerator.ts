import type { PostcardGenerator } from "../../application/ports.ts";
import type { Memory } from "../../../workspace/domain/models.ts";
export class DemoPostcardGenerator implements PostcardGenerator {
  async generate(records: readonly Memory[], petName: string) {
    await new Promise((resolve) => setTimeout(resolve, 900));
    return {
      imageUrl: records.find((record) => record.photoUrl)?.photoUrl ?? "",
      text: `${petName}와 함께한 ${records[0].recordedDate}.\n\n${records
        .map((record) => record.body)
        .filter(Boolean)
        .join("\n\n")
        .slice(
          0,
          700,
        )}\n\n작은 순간들이 모여 오래 간직할 우리의 이야기가 됩니다.`,
    };
  }
}
