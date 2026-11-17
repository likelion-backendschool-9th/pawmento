import type { Question } from "../domain/Question.ts";
import { validateDate } from "../domain/models.ts";
export interface QuestionRepository {
  findForDate(petId: string, date: string): Promise<Question>;
}
export function createGetTodayQuestion(questions: QuestionRepository) {
  return async (petId: string, date: string) => {
    if (!petId) throw Error("반려동물을 먼저 선택해 주세요.");
    validateDate(date);
    return questions.findForDate(petId, date);
  };
}
