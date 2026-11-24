import type { DiaryEntry } from "../../diary/types/DiaryEntry.ts";
import type { Pet } from "../../pet/types/Pet.ts";
import type {
  QuestionAnswer,
  QuestionPrompt,
} from "../../question/types/QuestionAnswer.ts";
export type Workspace = {
  pets: Pet[];
  diaries: DiaryEntry[];
  questionAnswers: QuestionAnswer[];
  questionPrompts: QuestionPrompt[];
  selectedPetId: string;
};
export function emptyWorkspace(): Workspace {
  return {
    pets: [],
    diaries: [],
    questionAnswers: [],
    questionPrompts: [],
    selectedPetId: "",
  };
}
