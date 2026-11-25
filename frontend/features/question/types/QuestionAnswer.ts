import type { MediaAttachment } from "../../../types/MediaAttachment.ts";
export type QuestionPrompt = {
  id: string;
  petId: string;
  mode: "ACTIVE" | "GARDEN" | "LEGACY";
  questionId: string;
  questionSnapshot: string;
  createdAt: string;
  completedAt?: string;
};
export type QuestionAnswer = {
  id: string;
  petId: string;
  authorId?: string;
  authorName?: string;
  promptId: string;
  questionId: string;
  questionSnapshot: string;
  registeredAt: string;
  updatedAt: string;
  body: string;
  photoUrl: string;
  attachments?: MediaAttachment[];
  legacy?: boolean;
  legacyRecordedDate?: string;
};
export type QuestionAnswerInput = Pick<
  QuestionAnswer,
  "petId" | "promptId" | "body" | "photoUrl" | "attachments" | "authorName"
>;

export type DemoQuestionAnswerInput = {
  authorId?: string;
  petId: string;
  questionId: string;
  questionSnapshot: string;
  registeredAt: string;
  body: string;
  photoUrl: string;
  authorName: string;
};
