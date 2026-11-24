import { ApplicationError } from "../../../lib/application/ApplicationError.ts";
import { today, validateDate } from "../../../lib/date.ts";
import { ApiError, type ApiClient } from "../../../lib/http/apiClient.ts";
import type { ManagedPet, RegisterPetInput } from "../types/ManagedPet.ts";

type ManagedPetDto = Omit<ManagedPet, "id" | "profilePhotoUrl"> & {
  petId: number | string;
  profilePhoto: { thumbnailUrl: string } | null;
};

function toManagedPet(pet: ManagedPetDto): ManagedPet {
  return {
    id: String(pet.petId),
    name: pet.name,
    tag: pet.tag,
    species: pet.species,
    breed: pet.breed,
    birthday: pet.birthday,
    mode: pet.mode,
    profilePhotoUrl: pet.profilePhoto?.thumbnailUrl ?? null,
  };
}

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
      return pets.map(toManagedPet);
    },

    async register(input: RegisterPetInput): Promise<ManagedPet> {
      const { name, species, breed, birthday } = input;
      if (!name.trim() || name.length > 20) {
        throw new ApplicationError("VALIDATION", "이름은 1~20자로 입력해 주세요.");
      }
      if (breed.length > 100) {
        throw new ApplicationError("VALIDATION", "품종은 100자까지 입력해 주세요.");
      }
      if (birthday) {
        validateDate(birthday);
        if (birthday > today()) {
          throw new ApplicationError("VALIDATION", "생일은 오늘 또는 이전 날짜로 선택해 주세요.");
        }
      }

      const trimmedBreed = breed.trim();
      try {
        const pet = await http.post<ManagedPetDto>("/pets", {
          name: name.trim(),
          species,
          ...(trimmedBreed && { breed: trimmedBreed }),
          ...(birthday && { birthday }),
        });
        if (!pet?.petId || !pet.tag) throw new Error("반려동물 등록 응답이 비어 있습니다.");
        return toManagedPet(pet);
      } catch (cause) {
        if (cause instanceof ApiError && cause.status === 401) {
          throw new ApplicationError("UNAUTHORIZED", cause.message);
        }
        if (cause instanceof ApiError && cause.status >= 400 && cause.status < 500 && cause.status !== 408) {
          throw cause;
        }
        throw new ApplicationError(
          "PET_REGISTRATION_UNCONFIRMED",
          "등록 결과를 확인하지 못했어요. 다시 등록하기 전에 목록을 확인해 주세요.",
        );
      }
    },
  };
}
