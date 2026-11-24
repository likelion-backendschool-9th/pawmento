import type {
  QuestionAnswer,
  QuestionPrompt,
} from "../types/QuestionAnswer.ts";
/** 완료된 배정은 마지막 답변을 삭제해도 이력에 남는다. */
export function questionHistory(
  petId: string,
  prompts: QuestionPrompt[],
  answers: QuestionAnswer[],
) {
  return prompts
    .filter((p) => p.petId === petId && p.completedAt)
    .sort(
      (a, b) =>
        b.completedAt!.localeCompare(a.completedAt!) ||
        b.id.localeCompare(a.id),
    )
    .map((prompt) => ({
      prompt,
      answers: answers
        .filter((a) => a.petId === petId && a.promptId === prompt.id)
        .sort(
          (a, b) =>
            a.registeredAt.localeCompare(b.registeredAt) ||
            a.id.localeCompare(b.id),
        ),
    }));
}
