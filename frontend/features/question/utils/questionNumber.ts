import type { QuestionPrompt } from "../types/QuestionAnswer.ts";

/** Number assignments, not answers: family replies and answer deletion do not change it. */
export function questionNumber(petId: string, promptId: string, prompts: readonly QuestionPrompt[]): number | undefined {
  const ordered = prompts
    .filter((p) => p.petId === petId)
    .map((prompt, index) => ({ prompt, index }))
    .sort((a, b) => Date.parse(a.prompt.createdAt) - Date.parse(b.prompt.createdAt) || a.index - b.index);
  const index = ordered.findIndex(({ prompt }) => prompt.id === promptId);
  return index < 0 ? undefined : index + 1;
}
