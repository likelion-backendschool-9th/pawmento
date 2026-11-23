import { createDiaryUseCases } from "../../diary/application/useCases.ts";
import { createQuestionAnswerUseCases } from "../../question/application/useCases.ts";
import type { QuestionPoolPort } from "../../question/application/useCases.ts";
import { validatePet } from "../domain/models.ts";
import type { PetInput, Workspace } from "../domain/models.ts";
import type { RuntimePort, WorkspaceRepository } from "./ports.ts";

export function createWorkspaceUseCases(
  repository: WorkspaceRepository,
  runtime: RuntimePort,
  pool: QuestionPoolPort = { list: async () => [] },
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
  const diary = createDiaryUseCases(
    {
      read: async (id) => {
        const state = await repository.load(id);
        return { collection: state.diaries, pets: state.pets };
      },
      mutate: (id, change) =>
        mutate(id, (state) => {
          const ctx = { collection: state.diaries, pets: state.pets };
          const result = change(ctx);
          state.diaries = ctx.collection;
          return result;
        }),
    },
    runtime,
  );
  const questions = createQuestionAnswerUseCases(
    {
      read: async (id) => {
        const state = await repository.load(id);
        return {
          collection: {
            answers: state.questionAnswers,
            prompts: state.questionPrompts,
          },
          pets: state.pets,
        };
      },
      mutate: (id, change) =>
        mutate(id, (state) => {
          const ctx = {
            collection: {
              answers: state.questionAnswers,
              prompts: state.questionPrompts,
            },
            pets: state.pets,
          };
          const result = change(ctx);
          state.questionAnswers = ctx.collection.answers;
          state.questionPrompts = ctx.collection.prompts;
          return result;
        }),
    },
    pool,
    runtime,
  );
  return {
    diary,
    questions,
    seedDemoAnswers: (
      accountId: string,
      inputs: import("../../question/domain/QuestionAnswer.ts").DemoQuestionAnswerInput[],
    ) =>
      mutate(accountId, (state) => {
        if (accountId !== "demo")
          throw Error("데모 계정에서만 샘플을 추가할 수 있어요.");
        for (const input of inputs) {
          const pet = state.pets.find((p) => p.id === input.petId);
          if (!pet || pet.accessRole === "VIEWER")
            throw Error("기록을 추가할 수 없어요.");
          if (
            state.questionAnswers.some(
              (a) => a.petId === pet.id && a.questionId === input.questionId,
            )
          )
            continue;
          const promptId = runtime.id();
          state.questionPrompts.push({
            id: promptId,
            petId: pet.id,
            questionId: input.questionId,
            questionSnapshot: input.questionSnapshot,
            mode: pet.mode,
            createdAt: input.registeredAt,
            completedAt: input.registeredAt,
          });
          state.questionAnswers.push({
            ...input,
            id: runtime.id(),
            promptId,
            authorId: accountId,
            updatedAt: input.registeredAt,
          });
        }
      }),
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
        if (
          [...state.diaries, ...state.questionAnswers].some(
            (record) => record.petId === id,
          )
        )
          throw Error(
            "기록이 있는 친구는 삭제할 수 없어요. 기록을 보관하는 정책이 확정되기 전에는 프로필을 유지합니다.",
          );
        state.pets = state.pets.filter((pet) => pet.id !== id);
        if (state.selectedPetId === id)
          state.selectedPetId = state.pets[0]?.id ?? "";
      }),
    clear: (accountId: string) =>
      mutate(accountId, (state) => {
        state.pets = state.pets.filter(
          (pet) => pet.ownerId && pet.ownerId !== accountId,
        );
        state.diaries = state.diaries.filter((d) =>
          state.pets.some((p) => p.id === d.petId),
        );
        state.questionAnswers = state.questionAnswers.filter((d) =>
          state.pets.some((p) => p.id === d.petId),
        );
        state.questionPrompts = state.questionPrompts.filter((d) =>
          state.pets.some((p) => p.id === d.petId),
        );
        state.selectedPetId = state.pets[0]?.id ?? "";
      }),
  };
}
