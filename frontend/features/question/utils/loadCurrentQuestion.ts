import { currentQuestionText } from "./currentQuestionText.ts";

type AssignedPrompt = {
  id: string;
  questionId: string;
  questionSnapshot: string;
  completedAt?: string | null;
};

type TemplateQuestion = { id: string; mode: string; text: string };

/**
 * Publishes the assigned snapshot as soon as it arrives, then swaps only the display
 * text once the optional template pool confirms the same question.
 */
export async function loadCurrentQuestion({
  prompt,
  name,
  mode,
  loadTemplates,
  isActive,
  publish,
}: {
  prompt: AssignedPrompt;
  name: string;
  mode: string | undefined;
  loadTemplates: () => Promise<TemplateQuestion[]>;
  isActive: () => boolean;
  publish: (question: { id: string; text: string }) => void;
}) {
  if (!isActive()) return;
  const snapshot = prompt.questionSnapshot;
  publish({ id: prompt.id, text: snapshot });
  if (prompt.completedAt) return;
  let template: string | undefined;
  try {
    template = (await loadTemplates()).find(
      (item) => item.id === prompt.questionId && item.mode === mode,
    )?.text;
  } catch {
    // The assigned snapshot remains readable if optional display data is unavailable.
    return;
  }
  const text = currentQuestionText(name, snapshot, template);
  if (text !== snapshot && isActive()) publish({ id: prompt.id, text });
}
