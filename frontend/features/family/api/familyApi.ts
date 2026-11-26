import { ApplicationError } from "../../../lib/application/ApplicationError.ts";
import type { ApiClient } from "../../../lib/http/apiClient.ts";
import type {
  FamilyList,
  FamilyPet,
  FamilyPerson,
  InviteCode,
  JoinedPet,
} from "../types/serverFamily.ts";

type PersonDto = { memberId: number | string; nickname: string | null; tag: string | null };
type FamilyDto = { owner: PersonDto; members: (PersonDto & { joinedAt: string })[] };
type PetDto = { petId: number; name: string; tag: string };

// 서버의 Long id를 화면용 문자열로 바꾼다.
// 2^53 이상인 숫자는 JSON을 읽는 순간 다른 값으로 바뀌었을 수 있어 쓰지 않는다(null)
function toId(value: number | string): string | null {
  if (typeof value === "string") return /^[1-9]\d*$/.test(value) ? value : null;
  return Number.isSafeInteger(value) && value > 0 ? String(value) : null;
}

const toPerson = (dto: PersonDto): FamilyPerson => ({
  memberId: toId(dto.memberId),
  nickname: dto.nickname,
  tag: dto.tag,
});

function requirePetId(petId: string) {
  if (!/^[1-9]\d*$/.test(petId)) {
    throw new ApplicationError("NOT_FOUND", "반려동물을 찾을 수 없어요.");
  }
}

export function createFamilyApi(http: ApiClient) {
  return {
    async list(petId: string): Promise<FamilyList> {
      requirePetId(petId);
      const family = await http.get<FamilyDto>(`/families?petId=${petId}`);
      return {
        owner: toPerson(family.owner),
        members: family.members.map((member) => ({
          ...toPerson(member),
          joinedAt: member.joinedAt,
        })),
      };
    },

    async issueCode(petId: string): Promise<InviteCode> {
      requirePetId(petId);
      return http.post<InviteCode>("/families/invite-codes", { petId: Number(petId) });
    },

    async join(code: string): Promise<JoinedPet> {
      const pet = await http.post<PetDto>("/families/members", { code: code.trim() });
      return { id: String(pet.petId), name: pet.name, tag: pet.tag };
    },

    async myPets(): Promise<FamilyPet[]> {
      const pets = await http.get<(PetDto & { joinedAt: string })[]>("/families/pets");
      return pets.map((pet) => ({
        id: String(pet.petId),
        name: pet.name,
        tag: pet.tag,
        joinedAt: pet.joinedAt,
      }));
    },

    async removeMember(petId: string, memberId: string): Promise<void> {
      requirePetId(petId);
      if (!/^[1-9]\d*$/.test(memberId)) {
        throw new ApplicationError("NOT_FOUND", "가족을 찾을 수 없어요.");
      }
      await http.delete(`/families/members/${memberId}?petId=${petId}`);
    },

    async leave(petId: string): Promise<void> {
      requirePetId(petId);
      await http.delete(`/families/members/me?petId=${petId}`);
    },
  };
}
