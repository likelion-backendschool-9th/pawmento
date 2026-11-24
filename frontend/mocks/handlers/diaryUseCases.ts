import type { DiaryEntry, DiaryEntryInput } from "../../features/diary/types/DiaryEntry.ts";
import { registrationDate, validateDate } from "../../lib/date.ts";
import {
  requireJournalAuthor,
  requireJournalPet,
  validateJournalContent,
} from "./journalStore.ts";
import type { JournalRuntime, JournalStore } from "./journalStore.ts";
export function createDiaryUseCases(
  store: JournalStore<DiaryEntry[]>,
  runtime: JournalRuntime,
) {
  return {
    save: (accountId: string, input: DiaryEntryInput, id?: string) =>
      store.mutate(accountId, (ctx) => {
        requireJournalPet(ctx.pets, input.petId, true);
        validateDate(input.recordedDate);
        if (input.recordedDate > registrationDate(runtime.now()))
          throw Error("미래 날짜의 일기는 남길 수 없어요.");
        validateJournalContent(input);
        if ((input.attachments?.length ?? 0) > 5)
          throw Error("첨부는 최대 5개예요.");
        const old = id
          ? ctx.collection.find((d) => d.id === id && d.petId === input.petId)
          : undefined;
        if (id && !old) throw Error("일기를 찾을 수 없어요.");
        if (old) {
          requireJournalAuthor(old.authorId, accountId);
          if (old.legacy)
            throw Error("영상이 포함된 이전 데모 일기는 읽기만 가능해요.");
        }
        const entry: DiaryEntry = {
          ...input,
          id: id ?? runtime.id(),
          authorId: old?.authorId ?? accountId,
          authorName: old ? old.authorName : input.authorName,
          body: input.body.trim(),
          type: input.attachments?.some((a) => a.type === "VOICE")
            ? "VOICE"
            : input.photoUrl ||
                input.attachments?.some((a) => a.type === "PHOTO")
              ? "PHOTO"
              : "TEXT",
          createdAt: old?.createdAt ?? runtime.now(),
          updatedAt: runtime.now(),
        };
        ctx.collection = id
          ? ctx.collection.map((d) => (d.id === id ? entry : d))
          : [...ctx.collection, entry];
        return entry;
      }),
    remove: (accountId: string, petId: string, id: string) =>
      store.mutate(accountId, (ctx) => {
        requireJournalPet(ctx.pets, petId, true);
        const old = ctx.collection.find(
          (d) => d.petId === petId && d.id === id,
        );
        if (!old) throw Error("일기를 찾을 수 없어요.");
        requireJournalAuthor(old.authorId, accountId);
        ctx.collection = ctx.collection.filter((d) => d.id !== id);
      }),
    list: async (
      accountId: string,
      petId: string,
      options: { date?: string; offset?: number; limit?: number } = {},
    ) => {
      if (options.date) validateDate(options.date);
      const ctx = await store.read(accountId);
      requireJournalPet(ctx.pets, petId);
      const entries = ctx.collection
        .filter(
          (d) =>
            d.petId === petId &&
            (!options.date || d.recordedDate === options.date),
        )
        .sort(
          (a, b) =>
            b.recordedDate.localeCompare(a.recordedDate) ||
            b.createdAt.localeCompare(a.createdAt) ||
            b.id.localeCompare(a.id),
        );
      const offset = Math.max(0, options.offset ?? 0),
        limit = Math.max(1, options.limit ?? 9);
      return {
        entries: entries.slice(offset, offset + limit),
        total: entries.length,
        hasMore: offset + limit < entries.length,
      };
    },
  };
}
