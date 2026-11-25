import { ApplicationError } from "../../../lib/application/ApplicationError.ts";
import type { ManagedPet, RegisterPetInput } from "../types/ManagedPet.ts";

export function profileInput(pet: ManagedPet): RegisterPetInput {
  return {
    name: pet.name,
    species: pet.species,
    breed: pet.breed ?? "",
    birthday: pet.birthday ?? "",
  };
}

export function profileChanges(original: RegisterPetInput, input: RegisterPetInput) {
  const name = input.name.trim();
  const breed = input.breed.trim();
  if (!name) {
    throw new ApplicationError("VALIDATION", "이름을 입력해 주세요.");
  }
  if (original.birthday && !input.birthday) {
    throw new ApplicationError("VALIDATION", "기존 생일을 유지하거나 다른 날짜를 입력해 주세요.");
  }

  return {
    ...(name !== original.name && { name }),
    ...(input.species !== original.species && { species: input.species }),
    ...(breed !== original.breed && { breed }),
    ...(input.birthday !== original.birthday && { birthday: input.birthday }),
  };
}

export function rebaseProfileInput(
  original: RegisterPetInput,
  input: RegisterPetInput,
  latest: RegisterPetInput,
): RegisterPetInput {
  return {
    name: input.name.trim() === original.name ? latest.name : input.name,
    species: input.species === original.species ? latest.species : input.species,
    breed: input.breed.trim() === original.breed ? latest.breed : input.breed,
    birthday: input.birthday === original.birthday ? latest.birthday : input.birthday,
  };
}
