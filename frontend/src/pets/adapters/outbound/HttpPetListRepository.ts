import type { ApiClient } from "../../../shared/http/apiClient.ts";
import type {
  PetListRepository,
  PetSummary,
} from "../../application/ports.ts";

type PetDto = {
  petId: number | string;
  name: string;
};

export class HttpPetListRepository implements PetListRepository {
  private readonly http: ApiClient;

  constructor(http: ApiClient) {
    this.http = http;
  }

  async listMine(): Promise<PetSummary[]> {
    const pets = await this.http.get<PetDto[]>("/pets");
    return pets.map((pet) => ({
      id: String(pet.petId),
      name: pet.name,
    }));
  }
}
