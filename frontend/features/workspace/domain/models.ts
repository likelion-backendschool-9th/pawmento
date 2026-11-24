export type Pet = {
  id: string;
  ownerId?: string;
  accessRole?: "OWNER" | "EDITOR" | "VIEWER";
  name: string;
  species: "DOG" | "CAT" | "OTHER";
  breed: string;
  birthday: string;
  photoUrl: string;
  mode: "ACTIVE" | "GARDEN";
};
export type {
  DiaryEntry,
  DiaryEntryInput,
} from "../../diary/domain/DiaryEntry.ts";
export type {
  QuestionAnswer,
  QuestionPrompt,
  QuestionAnswerInput,
  DemoQuestionAnswerInput,
} from "../../question/domain/QuestionAnswer.ts";
export type { MediaAttachment } from "../../../lib/domain/MediaAttachment.ts";
import type { DiaryEntry } from "../../diary/domain/DiaryEntry.ts";
import type {
  QuestionAnswer,
  QuestionPrompt,
} from "../../question/domain/QuestionAnswer.ts";
import type { MediaAttachment } from "../../../lib/domain/MediaAttachment.ts";
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
export type Workspace = {
  pets: Pet[];
  diaries: DiaryEntry[];
  questionAnswers: QuestionAnswer[];
  questionPrompts: QuestionPrompt[];
  selectedPetId: string;
};
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
export function emptyWorkspace(): Workspace {
  return {
    pets: [],
    diaries: [],
    questionAnswers: [],
    questionPrompts: [],
    selectedPetId: "",
  };
}
export type PetInput = Pick<
  Pet,
  "name" | "species" | "breed" | "birthday" | "photoUrl"
>;
export function validateDate(value: string): void {
  if (
    !/^\d{4}-\d{2}-\d{2}$/.test(value) ||
    Number.isNaN(Date.parse(value)) ||
    new Date(value).toISOString().slice(0, 10) !== value
  )
    throw Error("올바른 날짜를 선택해 주세요.");
}
export function validatePet(input: PetInput): void {
  if (!input.name.trim() || input.name.trim().length > 20)
    throw Error("이름은 1~20자로 입력해 주세요.");
  if (!["DOG", "CAT", "OTHER"].includes(input.species))
    throw Error("반려동물의 종류를 선택해 주세요.");
  if (input.birthday) validateDate(input.birthday);
}
export function validateJournalContent(
  input: { body: string; photoUrl: string; attachments?: MediaAttachment[] },
  legacy = false,
): void {
  if (!input.body.trim() && !input.photoUrl && !input.attachments?.length)
    throw Error("이야기나 사진·음성 기록을 남겨 주세요.");
  if (input.body.length > 3000)
    throw Error("기록은 3,000자까지 남길 수 있어요.");
  if (
    input.attachments?.some(
      (a) => !a.url || !a.name || !["PHOTO", "VOICE"].includes(a.type),
    )
  ) {
    if (!legacy) throw Error("새 첨부는 사진과 음성 녹음만 사용할 수 있어요.");
  }
}
