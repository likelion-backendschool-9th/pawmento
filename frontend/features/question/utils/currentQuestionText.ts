import { renderQuestion } from "./renderQuestion.ts";
import type { QuestionPrompt } from "../types/QuestionAnswer.ts";

/** Only rebind a template proven to be the one captured when this prompt was assigned. */
export function currentQuestionText(
  name: string,
  snapshot: string,
  template?: string,
  assignment?: QuestionPrompt["displayBinding"],
): string {
  if (!name || !template?.includes("{name}") ||
      typeof assignment?.name !== "string" || !assignment.name ||
      typeof assignment.template !== "string" || assignment.template !== template ||
      renderQuestion(assignment.name, assignment.template) !== snapshot) return snapshot;
  return renderQuestion(name, template);
}
