import type { JournalItem } from "../../features/diary/types/JournalItem.ts";
import { postcardScenes } from "../../features/postcard/utils/postcardScenes.ts";
import type { PostcardScene } from "../../features/postcard/utils/postcardScenes.ts";
import type { PostcardGenerator } from "./lifeUseCases.ts";

export function sceneForJournalItem(record: JournalItem): PostcardScene {
  const text = `${record.question ?? ""} ${record.body}`;
  if (/낮잠|잠들|인형|졸|이불|쉬었|휴식|sleep|nap/i.test(text)) return "NAP";
  if (/바다|해변|노을|파도|모래|해질|sunset|beach|sea\b/i.test(text))
    return "BEACH";
  return "PARK";
}
export class DemoPostcardGenerator implements PostcardGenerator {
  async generate(
    records: readonly JournalItem[],
    petName: string,
    petPhotoUrl?: string,
  ) {
    if (records.length !== 1)
      throw Error("엽서에 담을 기억을 한 장 뽑아 주세요.");
    const record = records[0];
    const scene = sceneForJournalItem(record);
    await new Promise((resolve) => setTimeout(resolve, 1500));
    return {
      imageUrl: postcardScenes[scene].imageUrl,
      text: `${petName}와 함께한 ${record.recordedDate}.\n\n${record.question ? `그날의 질문: ${record.question}\n` : ""}${record.body || "사진으로 남겨둔 소중한 순간."}\n\n작은 순간들이 모여 오래 간직할 우리의 이야기가 됩니다.`,
      demoArtwork: {
        scene,
        referencePhotoUrl: petPhotoUrl || record.photoUrl || "",
        prompt: `참고 사진 속 ${petName}의 털색, 얼굴과 체형을 유지하며 다음 기록의 상황 속에 있는 모습을 새로운 파스텔 수채화 엽서 그림으로 그려주세요.\n날짜: ${record.recordedDate}\n${record.question ? `질문: ${record.question}\n` : ""}기록: ${record.body || "함께한 일상의 순간"}\n사진을 그대로 붙이지 않고 장면 전체를 새로 그립니다. 글자나 로고는 넣지 않습니다.`,
      },
    };
  }
}
