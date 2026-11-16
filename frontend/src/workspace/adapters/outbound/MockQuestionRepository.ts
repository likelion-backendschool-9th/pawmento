import type { QuestionRepository } from "../../application/questions.ts";
const prompts = [
  "오늘 {name}{subject} 당신을 웃게 만든 순간은 언제인가요?",
  "오늘 {name}에게 가장 고마웠던 순간을 들려주세요.",
  "오늘 {name}와 새롭게 발견한 작은 즐거움은 무엇인가요?",
  "{name}와 함께한 오늘, 오래 기억하고 싶은 풍경은 무엇인가요?",
  "오늘 {name}에게 해주고 싶은 말이 있나요?",
];
export class MockQuestionRepository implements QuestionRepository {
  async findForDate(petId: string, date: string) {
    const index =
      ((Math.floor(Date.parse(date) / 86400000) % prompts.length) +
        prompts.length) %
      prompts.length;
    return { id: `question-${petId}-${date}`, text: prompts[index] };
  }
}
