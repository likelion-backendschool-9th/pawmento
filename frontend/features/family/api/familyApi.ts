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
type PetDto = { petId: number | string; name: string; tag: string };

// 지금 서버는 Long id를 JSON 숫자로 준다. 2^53 이상은 읽는 순간 다른 값이 될 수 있어,
// 숫자로 왔든 그 숫자를 문자열로 바꿔 URL에 실었든 믿지 않는다.
// 서버가 id를 문자열로 내려 주도록 바뀌면 이 범위 검사를 푼다
function isSafeId(value: string) {
  return /^[1-9]\d*$/.test(value) && Number.isSafeInteger(Number(value));
}

function toId(value: number | string): string | null {
  const text = String(value);
  return isSafeId(text) ? text : null;
}

const toPerson = (dto: PersonDto): FamilyPerson => ({
  memberId: toId(dto.memberId),
  nickname: dto.nickname,
  tag: dto.tag,
});

function requirePetId(petId: string) {
  if (!isSafeId(petId)) {
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
      return { id: toId(pet.petId), name: pet.name, tag: pet.tag };
    },

    async myPets(): Promise<FamilyPet[]> {
      const pets = await http.get<(PetDto & { joinedAt: string })[]>("/families/pets");
      // 반려동물 id를 정확히 모르면 링크와 나가기가 다른 반려동물로 갈 수 있어 목록에서 뺀다
      return pets.flatMap((pet) => {
        const id = toId(pet.petId);
        return id ? [{ id, name: pet.name, tag: pet.tag, joinedAt: pet.joinedAt }] : [];
      });
    },

    async removeMember(petId: string, memberId: string): Promise<void> {
      requirePetId(petId);
      if (!isSafeId(memberId)) {
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
