import type { MediaAttachment } from "../../shared/domain/MediaAttachment.ts";
export type DiaryEntry = {
  id: string;
  petId: string;
  authorId?: string;
  authorName?: string;
  type: "TEXT" | "PHOTO" | "VOICE" | "VIDEO";
  recordedDate: string;
  createdAt: string;
  updatedAt: string;
  body: string;
  photoUrl: string;
  attachments?: MediaAttachment[];
  legacy?: boolean;
};
export type DiaryEntryInput = Pick<
  DiaryEntry,
  "petId" | "recordedDate" | "body" | "photoUrl" | "attachments" | "authorName"
>;
