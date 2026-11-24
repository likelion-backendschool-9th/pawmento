import type { MediaAttachment } from "../../types/MediaAttachment.ts";
export type JournalPet = {
  id: string;
  name: string;
  mode: "ACTIVE" | "GARDEN";
  ownerId?: string;
  accessRole?: "OWNER" | "EDITOR" | "VIEWER";
};
export type JournalContext<T> = { collection: T; pets: JournalPet[] };
export interface JournalStore<T> {
  read(accountId: string): Promise<JournalContext<T>>;
  mutate<R>(
    accountId: string,
    change: (state: JournalContext<T>) => R,
  ): Promise<R>;
}
export interface JournalRuntime {
  id(): string;
  now(): string;
}
export function requireJournalPet(
  pets: JournalPet[],
  petId: string,
  write = false,
) {
  const pet = pets.find((p) => p.id === petId);
  if (!pet) throw Error("반려동물을 먼저 선택해 주세요.");
  if (write && pet.accessRole === "VIEWER")
    throw Error("열람자는 기록을 변경할 수 없어요.");
  return pet;
}
export function requireJournalAuthor(
  authorId: string | undefined,
  accountId: string,
) {
  if (!authorId)
    throw Error("작성자를 확인할 수 없는 이전 데모 기록은 읽기만 가능해요.");
  if (authorId !== accountId) throw Error("작성자만 수정·삭제할 수 있어요.");
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
