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
export function registrationDate(timestamp: string): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date(timestamp));
}
