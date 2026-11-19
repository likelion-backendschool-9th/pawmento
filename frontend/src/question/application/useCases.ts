import { renderQuestion } from "../domain/renderQuestion.ts";
import type {
  QuestionAnswer,
  QuestionAnswerInput,
  QuestionPrompt,
} from "../domain/QuestionAnswer.ts";
import { validateJournalContent } from "../../workspace/domain/models.ts";
import type {
  JournalStore,
  JournalRuntime,
} from "../../shared/application/JournalStore.ts";
import {
  requireJournalPet,
  requireJournalAuthor,
  registrationDate,
} from "../../shared/application/JournalStore.ts";
export type QuestionCollection = {
  answers: QuestionAnswer[];
  prompts: QuestionPrompt[];
};
export interface QuestionPoolPort {
  list(mode: "ACTIVE" | "GARDEN"): Promise<{ id: string; text: string }[]>;
}
export function createQuestionAnswerUseCases(
  store: JournalStore<QuestionCollection>,
  pool: QuestionPoolPort,
  runtime: JournalRuntime,
) {
  return {
    current: async (accountId: string, petId: string) => {
      const snapshot = await store.read(accountId);
      const pet = requireJournalPet(snapshot.pets, petId);
      const pending = snapshot.collection.prompts.find(
        (p) => p.petId === petId && p.mode === pet.mode && !p.completedAt,
      );
      if (pending) return pending;
      const questions = await pool.list(pet.mode);
      return store.mutate(accountId, (ctx) => {
        const currentPet = requireJournalPet(ctx.pets, petId);
        if (currentPet.mode !== pet.mode)
          throw Error("모드가 바뀌었어요. 질문을 다시 불러와 주세요.");
        const history = ctx.collection.prompts.filter(
          (p) => p.petId === petId && p.mode === pet.mode,
        );
        const current = history.find((p) => !p.completedAt);
        if (current) return current;
        if (!questions.length)
          throw Error(
            "이 모드의 질문이 없어요. 관리자 질문 관리를 확인해 주세요.",
          );
        const last = history.at(-1);
        const question =
          questions.find((q) => q.id !== last?.questionId) ?? questions[0];
        const prompt: QuestionPrompt = {
          id: runtime.id(),
          petId,
          mode: pet.mode,
          questionId: question.id,
          questionSnapshot: renderQuestion(currentPet.name, question.text),
          createdAt: runtime.now(),
        };
        ctx.collection.prompts.push(prompt);
        return prompt;
      });
    },
    save: (accountId: string, input: QuestionAnswerInput, id?: string) =>
      store.mutate(accountId, (ctx) => {
        const pet = requireJournalPet(ctx.pets, input.petId, true);
        validateJournalContent(input);
        if ((input.attachments?.length ?? 0) > 5)
          throw Error("첨부는 최대 5개예요.");
        const prompt = ctx.collection.prompts.find(
          (p) => p.id === input.promptId && p.petId === input.petId,
        );
        if (!prompt) throw Error("질문을 찾을 수 없어요.");
        const old = id
          ? ctx.collection.answers.find(
              (a) => a.id === id && a.petId === input.petId,
            )
          : undefined;
        if (id && !old) throw Error("답변을 찾을 수 없어요.");
        if (old) {
          requireJournalAuthor(old.authorId, accountId);
          if (old.promptId !== prompt.id)
            throw Error("질문은 변경할 수 없어요.");
          if (old.legacy && old.attachments?.some((a) => a.type === "VIDEO"))
            throw Error("영상이 포함된 이전 데모 답변은 읽기만 가능해요.");
        } else {
          if (prompt.mode !== pet.mode)
            throw Error("모드가 바뀌었어요. 현재 모드의 질문에 답해 주세요.");
          if (
            ctx.collection.answers.some(
              (a) => a.promptId === prompt.id && a.authorId === accountId,
            )
          )
            throw Error("이미 답변한 질문이에요. 기존 답변을 수정해 주세요.");
        }
        const now = runtime.now();
        const answer: QuestionAnswer = {
          ...input,
          id: id ?? runtime.id(),
          authorId: old?.authorId ?? accountId,
          authorName: old ? old.authorName : input.authorName,
          questionId: old?.questionId ?? prompt.questionId,
          questionSnapshot: old?.questionSnapshot ?? prompt.questionSnapshot,
          registeredAt: old?.registeredAt ?? now,
          updatedAt: now,
          body: input.body.trim(),
          legacy: old?.legacy,
          legacyRecordedDate: old?.legacyRecordedDate,
        };
        ctx.collection.answers = id
          ? ctx.collection.answers.map((a) => (a.id === id ? answer : a))
          : [...ctx.collection.answers, answer];
        if (!old && !prompt.completedAt) prompt.completedAt = now;
        return answer;
      }),
    remove: (accountId: string, petId: string, id: string) =>
      store.mutate(accountId, (ctx) => {
        requireJournalPet(ctx.pets, petId, true);
        const old = ctx.collection.answers.find(
          (a) => a.id === id && a.petId === petId,
        );
        if (!old) throw Error("답변을 찾을 수 없어요.");
        requireJournalAuthor(old.authorId, accountId);
        ctx.collection.answers = ctx.collection.answers.filter(
          (a) => a.id !== id,
        );
      }),
    list: async (accountId: string, petId: string, date?: string) => {
      const ctx = await store.read(accountId);
      requireJournalPet(ctx.pets, petId);
      return ctx.collection.answers
        .filter(
          (a) =>
            a.petId === petId &&
            (!date || registrationDate(a.registeredAt) === date),
        )
        .sort((a, b) => b.registeredAt.localeCompare(a.registeredAt));
    },
  };
}
