import type { MediaAttachment } from "../../../types/MediaAttachment.ts";
export type DiaryEntry = {
  id: string;
  petId: string;
  authorId?: string;
  authorName?: string;
  type: "TEXT" | "PHOTO" | "VOICE" | "VIDEO";
  recordedDate: string;
  createdAt: string;
  updatedAt: string;
  /** Missing only on older demo records. */
  title?: string;
  body: string;
  photoUrl: string;
  attachments?: MediaAttachment[];
  legacy?: boolean;
};
export type DiaryEntryInput = Pick<
  DiaryEntry,
  | "title"
  | "petId"
  | "recordedDate"
  | "body"
  | "photoUrl"
  | "attachments"
  | "authorName"
>;
