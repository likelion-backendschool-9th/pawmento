import type { Pet } from "./Pet.ts";

export type ManagedPet = Pick<Pet, "id" | "name" | "species" | "mode"> & {
  tag: string;
  breed: string | null;
  birthday: string | null;
  profilePhotoUrl: string | null;
};

export type RegisterPetInput = Pick<Pet, "name" | "species" | "breed" | "birthday">;
