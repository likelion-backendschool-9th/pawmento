export type Pet = {
  id: string;
  ownerId?: string;
  accessRole?: "OWNER" | "EDITOR" | "VIEWER";
  name: string;
  species: "DOG" | "CAT" | "OTHER";
  // 데모에서 OTHER의 세부 동물 종류를 표시한다. 서버 species는 기존 세 종류를 쓴다.
  speciesDetail?: string;
  breed: string;
  birthday: string;
  photoUrl: string;
  mode: "ACTIVE" | "GARDEN";
};
export type PetInput = Pick<
  Pet,
  "name" | "species" | "speciesDetail" | "breed" | "birthday" | "photoUrl"
>;
