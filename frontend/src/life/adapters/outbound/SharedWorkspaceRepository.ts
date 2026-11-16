import type { WorkspaceRepository } from "../../../workspace/application/ports.ts";
import type { Workspace, Pet } from "../../../workspace/domain/models.ts";
import type { LifeRepository } from "../../application/ports.ts";
/** Same-browser sharing adapter. A server implementation must enforce its own authorization. */
export class SharedWorkspaceRepository implements WorkspaceRepository {
  private base: WorkspaceRepository;
  private life: LifeRepository;
  constructor(base: WorkspaceRepository, life: LifeRepository) {
    this.base = base;
    this.life = life;
  }
  async load(accountId: string): Promise<Workspace> {
    const own = await this.base.load(accountId);
    const shared = (await this.life.load()).families.filter(
      (family) =>
        family.ownerId !== accountId &&
        family.members.some((member) => member.actorId === accountId),
    );
    const state: Workspace = {
      pets: own.pets.map((pet) => ({
        ...pet,
        ownerId: accountId,
        accessRole: "OWNER",
      })),
      records: [...own.records],
      selectedPetId: own.selectedPetId,
    };
    for (const family of shared) {
      const owner = await this.base.load(family.ownerId);
      const pet = owner.pets.find((item) => item.id === family.petId);
      if (!pet) continue;
      state.pets.push({
        ...pet,
        ownerId: family.ownerId,
        accessRole: family.members.find(
          (member) => member.actorId === accountId,
        )!.role,
      });
      state.records.push(
        ...owner.records.filter((item) => item.petId === pet.id),
      );
    }
    if (!state.pets.some((pet) => pet.id === state.selectedPetId))
      state.selectedPetId = state.pets[0]?.id ?? "";
    return state;
  }
  async save(accountId: string, incoming: Workspace) {
    const families = (await this.life.load()).families;
    const accessible = await this.load(accountId);
    const clean = (pet: Pet) => {
      const copy = { ...pet };
      delete copy.accessRole;
      delete copy.ownerId;
      return copy;
    };
    const ownerIds = new Set([
      accountId,
      ...accessible.pets.map((pet) => pet.ownerId ?? accountId),
    ]);
    for (const ownerId of ownerIds) {
      const old = await this.base.load(ownerId);
      const own = ownerId === accountId;
      const sharedPets = accessible.pets
        .filter((pet) => pet.ownerId === ownerId)
        .map((pet) => pet.id);
      const pets = own
        ? incoming.pets
            .filter((pet) => (pet.ownerId ?? accountId) === accountId)
            .map(clean)
        : old.pets.map((pet) => {
            const next = incoming.pets.find(
              (item) => item.id === pet.id && item.ownerId === ownerId,
            );
            return next && sharedPets.includes(pet.id) ? clean(next) : pet;
          });
      const records = own
        ? incoming.records.filter((record) =>
            pets.some((pet) => pet.id === record.petId),
          )
        : [
            ...old.records.filter(
              (record) => !sharedPets.includes(record.petId),
            ),
            ...incoming.records.filter((record) =>
              sharedPets.includes(record.petId),
            ),
          ];
      const next: Workspace = {
        pets,
        records,
        selectedPetId: own ? incoming.selectedPetId : old.selectedPetId,
      };
      if (JSON.stringify(next) === JSON.stringify(old)) continue;
      if (!own) {
        for (const petId of sharedPets) {
          const family = families.find(
            (item) => item.petId === petId && item.ownerId === ownerId,
          );
          const role = family?.members.find(
            (member) => member.actorId === accountId,
          )?.role;
          const oldPet = old.pets.find((item) => item.id === petId);
          const nextPet = pets.find((item) => item.id === petId);
          if (!role) throw Error("가족 공유가 해제되었어요.");
          if (
            JSON.stringify(oldPet && clean(oldPet)) !==
            JSON.stringify(nextPet && clean(nextPet))
          )
            throw Error("프로필은 소유자만 변경할 수 있어요.");
          if (
            role === "VIEWER" &&
            JSON.stringify(
              old.records.filter((item) => item.petId === petId),
            ) !== JSON.stringify(records.filter((item) => item.petId === petId))
          )
            throw Error("열람자는 기록을 수정할 수 없어요.");
        }
      }
      await this.base.save(ownerId, next);
    }
  }
}
