import { renderQuestion } from "./renderQuestion.ts";

/** Rebind only a verified name placeholder; never replace arbitrary words in a snapshot. */
export function currentQuestionText(name: string, snapshot: string, template?: string): string {
  if (!name || !template?.includes("{name}")) return snapshot;
  let captured = false;
  const pattern = template.split(/(\{name\}|\{subject\})/).map(part => {
    if (part === "{name}") {
      if (captured) return "\\k<assignedName>";
      captured = true;
      return "(?<assignedName>.+?)";
    }
    if (part === "{subject}") return "(?:이|가|\\(이\\)가)";
    return part.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  }).join("");
  const original = new RegExp(`^${pattern}$`, "u").exec(snapshot)?.groups?.assignedName;
  // A changed pool question is not the assigned question. Keep the snapshot in that case.
  if (!original || renderQuestion(original, template) !== snapshot) return snapshot;
  return renderQuestion(name, template);
}
