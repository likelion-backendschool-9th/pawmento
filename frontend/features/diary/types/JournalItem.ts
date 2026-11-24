import type { DiaryEntry } from "./DiaryEntry.ts";
import type { QuestionAnswer } from "../../question/types/QuestionAnswer.ts";
// 화면 합성용 모델이다. 저장은 diaries / questionAnswers로 분리한다.
export type JournalItem =
  | (DiaryEntry & {
      kind: "DIARY";
      question?: undefined;
      questionId?: undefined;
    })
  | (QuestionAnswer & {
      kind: "QUESTION_ANSWER";
      type: "QUESTION_ANSWER";
      recordedDate: string;
      createdAt: string;
      question: string;
    });
