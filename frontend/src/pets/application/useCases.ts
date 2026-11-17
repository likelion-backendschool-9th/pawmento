import type { PetListRepository } from "./ports.ts";

export function createPetUseCases(repository: PetListRepository) {
  return { listMine: () => repository.listMine() };
}
