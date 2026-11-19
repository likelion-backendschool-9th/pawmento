import type { Workspace } from "../../domain/models.ts";
import { validateDate, emptyWorkspace } from "../../domain/models.ts";

type LegacyRecord = {
  id: string;
  petId: string;
  authorId?: string;
  authorName?: string;
  type: "DIARY" | "TEXT" | "PHOTO" | "VOICE" | "VIDEO";
  recordedDate: string;
  createdAt: string;
  updatedAt: string;
  body: string;
  question: string;
  questionId?: string;
  photoUrl: string;
  attachments?: import("../../../shared/domain/MediaAttachment.ts").MediaAttachment[];
};
function object(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
function strings(value: unknown, keys: string[]): boolean {
  return object(value) && keys.every((k) => typeof value[k] === "string");
}
function timestamp(value: unknown): boolean {
  return typeof value === "string" && !Number.isNaN(Date.parse(value));
}
function attachments(value: unknown): boolean {
  return (
    value === undefined ||
    (Array.isArray(value) &&
      value.every(
        (a) =>
          strings(a, ["id", "type", "url", "name", "mimeType"]) &&
          object(a) &&
          ["PHOTO", "VOICE", "VIDEO"].includes(a.type as string),
      ))
  );
}
function pets(value: unknown): boolean {
  return (
    Array.isArray(value) &&
    value.every(
      (p) =>
        strings(p, [
          "id",
          "name",
          "species",
          "breed",
          "birthday",
          "photoUrl",
          "mode",
        ]) &&
        object(p) &&
        ["DOG", "CAT", "OTHER"].includes(p.species as string) &&
        ["ACTIVE", "GARDEN"].includes(p.mode as string),
    )
  );
}
export function migrateLegacyWorkspace(value: unknown): Workspace {
  if (
    !object(value) ||
    !pets(value.pets) ||
    !Array.isArray(value.records) ||
    typeof value.selectedPetId !== "string"
  )
    throw Error("Invalid legacy workspace");
  const state: Workspace = {
    ...emptyWorkspace(),
    pets: value.pets as Workspace["pets"],
    selectedPetId: value.selectedPetId,
  };
  const seen = new Set<string>();
  for (const raw of value.records) {
    if (
      !strings(raw, [
        "id",
        "petId",
        "type",
        "recordedDate",
        "createdAt",
        "updatedAt",
        "body",
        "question",
        "photoUrl",
      ]) ||
      !object(raw) ||
      !["DIARY", "TEXT", "PHOTO", "VOICE", "VIDEO"].includes(
        raw.type as string,
      ) ||
      !timestamp(raw.createdAt) ||
      !timestamp(raw.updatedAt) ||
      !attachments(raw.attachments)
    )
      throw Error("Invalid legacy record");
    const old = raw as unknown as LegacyRecord;
    validateDate(old.recordedDate);
    if (seen.has(old.id)) throw Error("Duplicate legacy id");
    seen.add(old.id);
    if (old.type === "DIARY") {
      const promptId = `legacy-prompt-${old.id}`;
      state.questionPrompts.push({
        id: promptId,
        petId: old.petId,
        mode: "LEGACY",
        questionId: old.questionId ?? `legacy-question-${old.id}`,
        questionSnapshot: old.question,
        createdAt: old.createdAt,
        completedAt: old.createdAt,
      });
      state.questionAnswers.push({
        id: old.id,
        petId: old.petId,
        authorId: old.authorId,
        authorName: old.authorName,
        promptId,
        questionId: old.questionId ?? `legacy-question-${old.id}`,
        questionSnapshot: old.question,
        registeredAt: old.createdAt,
        updatedAt: old.updatedAt,
        body: old.body,
        photoUrl: old.photoUrl,
        attachments: old.attachments,
        legacy: true,
        legacyRecordedDate: old.recordedDate,
      });
    } else {
      const { question: _question, questionId: _questionId, ...entry } = old;
      void _question;
      void _questionId;
      state.diaries.push({
        ...entry,
        type: old.type,
        legacy:
          old.type === "VIDEO" ||
          old.attachments?.some((a) => a.type === "VIDEO") ||
          false,
      });
    }
  }
  return state;
}
export function readWorkspaceSnapshot(value: unknown): Workspace {
  if (!object(value) || value.schemaVersion !== 2 || !object(value.state))
    throw Error("Invalid workspace version");
  const s = value.state;
  if (
    !pets(s.pets) ||
    typeof s.selectedPetId !== "string" ||
    !Array.isArray(s.diaries) ||
    !Array.isArray(s.questionAnswers) ||
    !Array.isArray(s.questionPrompts)
  )
    throw Error("Invalid workspace");
  if (
    !s.diaries.every(
      (d) =>
        strings(d, [
          "id",
          "petId",
          "type",
          "recordedDate",
          "createdAt",
          "updatedAt",
          "body",
          "photoUrl",
        ]) &&
        object(d) &&
        ["TEXT", "PHOTO", "VOICE", "VIDEO"].includes(d.type as string) &&
        attachments(d.attachments),
    )
  )
    throw Error("Invalid diary");
  if (
    !s.questionAnswers.every(
      (a) =>
        strings(a, [
          "id",
          "petId",
          "promptId",
          "questionId",
          "questionSnapshot",
          "registeredAt",
          "updatedAt",
          "body",
          "photoUrl",
        ]) &&
        object(a) &&
        timestamp(a.registeredAt) &&
        attachments(a.attachments),
    )
  )
    throw Error("Invalid answer");
  if (
    !s.questionPrompts.every(
      (p) =>
        strings(p, [
          "id",
          "petId",
          "questionId",
          "questionSnapshot",
          "mode",
          "createdAt",
        ]) &&
        object(p) &&
        ["ACTIVE", "GARDEN", "LEGACY"].includes(p.mode as string),
    )
  )
    throw Error("Invalid prompt");
  const collections = [s.diaries, s.questionAnswers, s.questionPrompts];
  for (const items of collections) {
    const ids = new Set<string>();
    for (const raw of items) {
      const item = raw as Record<string, unknown>;
      if (ids.has(item.id as string)) throw Error("Duplicate id");
      ids.add(item.id as string);
      if (
        !(s.pets as Record<string, unknown>[]).some(
          (p: Record<string, unknown>) => p.id === item.petId,
        )
      )
        throw Error("Unknown pet");
      for (const field of ["authorId", "authorName"]) {
        if (item[field] !== undefined && typeof item[field] !== "string")
          throw Error("Invalid author");
      }
    }
  }
  for (const d of s.diaries) {
    validateDate(d.recordedDate);
    if (!timestamp(d.createdAt) || !timestamp(d.updatedAt))
      throw Error("Invalid diary timestamp");
  }
  for (const p of s.questionPrompts) {
    if (
      !timestamp(p.createdAt) ||
      (p.completedAt !== undefined && !timestamp(p.completedAt))
    )
      throw Error("Invalid prompt timestamp");
  }
  const answerKeys = new Set<string>();
  for (const a of s.questionAnswers) {
    if (
      !timestamp(a.updatedAt) ||
      !s.questionPrompts.some(
        (p) =>
          p.id === a.promptId &&
          p.petId === a.petId &&
          p.questionId === a.questionId &&
          p.questionSnapshot === a.questionSnapshot,
      )
    )
      throw Error("Invalid answer prompt");
    if (a.legacyRecordedDate !== undefined) validateDate(a.legacyRecordedDate);
    if (a.authorId) {
      const key = JSON.stringify([a.promptId, a.authorId]);
      if (answerKeys.has(key)) throw Error("Duplicate author answer");
      answerKeys.add(key);
    }
  }
  return s as unknown as Workspace;
}
