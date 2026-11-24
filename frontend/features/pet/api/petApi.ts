import type { ApiClient } from "../../../lib/http/apiClient.ts";
import type { PetSummary } from "../types/PetSummary.ts";

type PetDto = {
  petId: number | string;
  name: string;
};

export function createPetApi(http: ApiClient) {
  return {
    async listMine(): Promise<PetSummary[]> {
      const pets = await http.get<PetDto[]>("/pets");
      return pets.map((pet) => ({
        id: String(pet.petId),
        name: pet.name,
      }));
    },
  };
}
