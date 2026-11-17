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
export type Memory = {
  id: string;
  petId: string;
  authorId?: string;
  authorName?: string;
  type: "DIARY" | "TEXT" | "PHOTO" | "VIDEO" | "VOICE";
  recordedDate: string;
  createdAt: string;
  updatedAt: string;
  body: string;
  question: string;
  questionId?: string;
  photoUrl: string;
  attachments?: MediaAttachment[];
};
export type MediaAttachment = {
  id: string;
  type: "PHOTO" | "VIDEO" | "VOICE";
  url: string;
  name: string;
  mimeType: string;
};
export type Workspace = {
  pets: Pet[];
  records: Memory[];
  selectedPetId: string;
};
export type PetInput = Pick<
  Pet,
  "name" | "species" | "breed" | "birthday" | "photoUrl"
>;
export type MemoryInput = Pick<
  Memory,
  | "petId"
  | "type"
  | "recordedDate"
  | "body"
  | "question"
  | "questionId"
  | "photoUrl"
  | "attachments"
  | "authorName"
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
export function validateMemory(input: MemoryInput): void {
  validateDate(input.recordedDate);
  if (!["DIARY", "TEXT", "PHOTO", "VIDEO", "VOICE"].includes(input.type))
    throw Error("기록 종류를 확인해 주세요.");
  if (
    (!input.body.trim() && !input.photoUrl && !input.attachments?.length) ||
    (input.type === "DIARY" && !input.body.trim())
  )
    throw Error("이야기를 적어 주세요. 자유 기록은 사진만 남겨도 좋아요.");
  if (input.type === "DIARY" && !input.question.trim())
    throw Error(
      "질문과 답변을 함께 저장할 수 있도록 질문을 먼저 불러와 주세요.",
    );
  if (
    input.attachments?.some(
      (file) =>
        !["PHOTO", "VIDEO", "VOICE"].includes(file.type) ||
        !file.url ||
        !file.name,
    )
  )
    throw Error("첨부 파일 정보를 확인해 주세요.");
  if (input.body.length > 3000)
    throw Error("기록은 3,000자까지 남길 수 있어요.");
}
