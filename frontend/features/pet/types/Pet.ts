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
export type PetInput = Pick<
  Pet,
  "name" | "species" | "breed" | "birthday" | "photoUrl"
>;
