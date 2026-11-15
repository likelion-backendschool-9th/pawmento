import { validateDate, validateMemory, validatePet } from "../domain/models.ts";
import type { MemoryInput, PetInput, Workspace } from "../domain/models.ts";
import type { RuntimePort, WorkspaceRepository } from "./ports.ts";

export function createWorkspaceUseCases(
  repository: WorkspaceRepository,
  runtime: RuntimePort,
) {
  // Serialize writes so concurrent form submissions cannot overwrite each other.
  let queue: Promise<unknown> = Promise.resolve();
  const mutate = <T>(
    accountId: string,
    change: (state: Workspace) => T,
  ): Promise<T> => {
    const operation = queue.then(async () => {
      const state = structuredClone(await repository.load(accountId));
      const result = change(state);
      await repository.save(accountId, state);
      return result;
    });
    queue = operation.catch(() => undefined);
    return operation;
  };
  return {
    load: (accountId: string) => repository.load(accountId),
    savePet: (accountId: string, input: PetInput, id?: string) =>
      mutate(accountId, (state) => {
        validatePet(input);
        if (
          id &&
          state.pets.find((pet) => pet.id === id)?.accessRole &&
          state.pets.find((pet) => pet.id === id)?.accessRole !== "OWNER"
        )
          throw Error("프로필은 소유자만 수정할 수 있어요.");
        if (id && !state.pets.some((pet) => pet.id === id))
          throw Error("반려동물을 찾을 수 없어요.");
        const pet = {
          ...input,
          name: input.name.trim(),
          id: id ?? runtime.id(),
          mode:
            (id
              ? state.pets.find((existing) => existing.id === id)?.mode
              : undefined) ?? "ACTIVE",
        };
        state.pets = id
          ? state.pets.map((existing) =>
              existing.id === id ? { ...existing, ...pet } : existing,
            )
          : [...state.pets, pet];
        state.selectedPetId = pet.id;
        return pet;
      }),
    selectPet: (accountId: string, id: string) =>
      mutate(accountId, (state) => {
        if (!state.pets.some((pet) => pet.id === id))
          throw Error("반려동물을 찾을 수 없어요.");
        state.selectedPetId = id;
      }),
    changeMode: (accountId: string, id: string, mode: "ACTIVE" | "GARDEN") =>
      mutate(accountId, (state) => {
        const pet = state.pets.find((item) => item.id === id);
        if (!pet) throw Error("반려동물을 찾을 수 없어요.");
        if (!["ACTIVE", "GARDEN"].includes(mode))
          throw Error("모드를 확인해 주세요.");
        if (pet.accessRole && pet.accessRole !== "OWNER")
          throw Error("모드는 소유자만 변경할 수 있어요.");
        pet.mode = mode;
      }),
    deletePet: (accountId: string, id: string) =>
      mutate(accountId, (state) => {
        if (
          state.pets.find((pet) => pet.id === id)?.accessRole &&
          state.pets.find((pet) => pet.id === id)?.accessRole !== "OWNER"
        )
          throw Error("소유자만 삭제할 수 있어요.");
        if (state.records.some((record) => record.petId === id))
          throw Error(
            "기록이 있는 친구는 삭제할 수 없어요. 기록을 보관하는 정책이 확정되기 전에는 프로필을 유지합니다.",
          );
        state.pets = state.pets.filter((pet) => pet.id !== id);
        if (state.selectedPetId === id)
          state.selectedPetId = state.pets[0]?.id ?? "";
      }),
    saveMemory: (accountId: string, input: MemoryInput, id?: string) =>
      mutate(accountId, (state) => {
        validateMemory(input);
        if (!state.pets.some((pet) => pet.id === input.petId))
          throw Error("반려동물을 먼저 선택해 주세요.");
        if (
          state.pets.find((pet) => pet.id === input.petId)?.accessRole ===
          "VIEWER"
        )
          throw Error("열람자는 기록을 수정할 수 없어요.");
        const old = id
          ? state.records.find(
              (record) => record.id === id && record.petId === input.petId,
            )
          : undefined;
        if (id && !old) throw Error("기록을 찾을 수 없어요.");
        if (
          old?.type === "DIARY" &&
          (input.type !== "DIARY" || input.recordedDate !== old.recordedDate)
        )
          throw Error("질문 일기의 날짜와 종류는 바꿀 수 없어요.");
        if (
          input.type === "DIARY" &&
          state.records.some(
            (record) =>
              record.id !== id &&
              record.petId === input.petId &&
              record.type === "DIARY" &&
              record.recordedDate === input.recordedDate,
          )
        )
          throw Error(
            "이날의 질문 일기가 이미 있어요. 기존 일기를 수정해 주세요.",
          );
        const record = {
          ...input,
          question: old?.type === "DIARY" ? old.question : input.question,
          questionId: old?.type === "DIARY" ? old.questionId : input.questionId,
          body: input.body.trim(),
          authorId: old
            ? (old.authorId ??
              state.pets.find((pet) => pet.id === input.petId)?.ownerId ??
              accountId)
            : accountId,
          authorName: old ? old.authorName : input.authorName,
          id: id ?? runtime.id(),
          createdAt: old?.createdAt ?? runtime.now(),
          updatedAt: runtime.now(),
        };
        state.records = id
          ? state.records.map((existing) =>
              existing.id === id ? record : existing,
            )
          : [...state.records, record];
        return record;
      }),
    clear: (accountId: string) =>
      mutate(accountId, (state) => {
        state.pets = state.pets.filter(
          (pet) => pet.ownerId && pet.ownerId !== accountId,
        );
        state.records = state.records.filter((record) =>
          state.pets.some((pet) => pet.id === record.petId),
        );
        state.selectedPetId = state.pets[0]?.id ?? "";
      }),
    deleteMemory: (accountId: string, petId: string, id: string) =>
      mutate(accountId, (state) => {
        if (state.pets.find((pet) => pet.id === petId)?.accessRole === "VIEWER")
          throw Error("열람자는 기록을 삭제할 수 없어요.");
        if (
          !state.records.some(
            (record) => record.id === id && record.petId === petId,
          )
        )
          throw Error("기록을 찾을 수 없어요.");
        state.records = state.records.filter((record) => record.id !== id);
      }),
    list: async (
      accountId: string,
      petId: string,
      options: { date?: string; offset?: number; limit?: number } = {},
    ) => {
      if (options.date) validateDate(options.date);
      const state = await repository.load(accountId);
      const records = state.records
        .filter(
          (record) =>
            record.petId === petId &&
            (!options.date || record.recordedDate === options.date),
        )
        .sort(
          (a, b) =>
            b.recordedDate.localeCompare(a.recordedDate) ||
            b.createdAt.localeCompare(a.createdAt),
        );
      const offset = Math.max(0, options.offset ?? 0);
      const limit = Math.max(1, options.limit ?? 9);
      return {
        records: records.slice(offset, offset + limit),
        total: records.length,
        hasMore: offset + limit < records.length,
      };
    },
  };
}
