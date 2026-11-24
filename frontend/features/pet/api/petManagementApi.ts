import { ApplicationError } from "../../../lib/application/ApplicationError.ts";
import { ApiError, type ApiClient } from "../../../lib/http/apiClient.ts";
import type { ManagedPet } from "../types/ManagedPet.ts";

type ManagedPetDto = Omit<ManagedPet, "id" | "profilePhotoUrl"> & {
  petId: number | string;
  profilePhoto: { thumbnailUrl: string } | null;
};

export function createPetManagementApi(http: ApiClient) {
  return {
    async listMine(): Promise<ManagedPet[]> {
      let pets: ManagedPetDto[];
      try {
        pets = await http.get<ManagedPetDto[]>("/pets");
      } catch (cause) {
        if (cause instanceof ApiError && cause.status === 401) {
          throw new ApplicationError("UNAUTHORIZED", cause.message);
        }
        throw cause;
      }
      return pets.map((pet) => ({
        id: String(pet.petId),
        name: pet.name,
        tag: pet.tag,
        species: pet.species,
        breed: pet.breed,
        birthday: pet.birthday,
        mode: pet.mode,
        profilePhotoUrl: pet.profilePhoto?.thumbnailUrl ?? null,
      }));
    },
  };
}
