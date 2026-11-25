import type { QuestionAnswer } from "../types/QuestionAnswer.ts";
// A family member's answer is never used as a fallback for the list preview.
export function myAnswer(
  answers: readonly QuestionAnswer[],
  memberId: string | undefined,
) {
  return memberId
    ? answers.find((answer) => answer.authorId === memberId)
    : undefined;
}
