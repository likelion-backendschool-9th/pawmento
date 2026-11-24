import type { JournalItem } from "../types/JournalItem.ts";
import type { Workspace } from "../../shell/types/workspace.ts";
export function journalItems(
  state: Pick<Workspace, "diaries" | "questionAnswers">,
): JournalItem[] {
  return [
    ...state.diaries.map((entry) => ({ ...entry, kind: "DIARY" as const })),
    ...state.questionAnswers.map((answer) => ({
      ...answer,
      kind: "QUESTION_ANSWER" as const,
      type: "QUESTION_ANSWER" as const,
      recordedDate:
        answer.legacyRecordedDate ??
        new Intl.DateTimeFormat("en-CA", {
          timeZone: "Asia/Seoul",
          year: "numeric",
          month: "2-digit",
          day: "2-digit",
        }).format(new Date(answer.registeredAt)),
      createdAt: answer.registeredAt,
      question: answer.questionSnapshot,
    })),
  ];
}
