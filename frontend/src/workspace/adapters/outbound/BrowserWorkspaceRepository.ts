import type { WorkspaceRepository } from "../../application/ports.ts";
import type { Workspace } from "../../domain/models.ts";

export class BrowserWorkspaceRepository implements WorkspaceRepository {
  async load(accountId: string): Promise<Workspace> {
    try {
      const raw = localStorage.getItem(`pawmento.workspace.v1.${accountId}`);
      if (!raw) return { pets: [], records: [], selectedPetId: "" };
      const value = JSON.parse(raw) as Workspace;
      if (
        !value ||
        !Array.isArray(value.pets) ||
        !Array.isArray(value.records) ||
        typeof value.selectedPetId !== "string"
      )
        throw Error();
      const strings = (item: unknown, keys: string[]) =>
        typeof item === "object" &&
        item !== null &&
        keys.every(
          (key) => typeof (item as Record<string, unknown>)[key] === "string",
        );
      if (
        !value.pets.every(
          (pet) =>
            strings(pet, [
              "id",
              "name",
              "species",
              "breed",
              "birthday",
              "photoUrl",
              "mode",
            ]) &&
            ["DOG", "CAT", "OTHER"].includes(pet.species) &&
            ["ACTIVE", "GARDEN"].includes(pet.mode),
        )
      )
        throw Error();
      if (
        !value.records.every(
          (record) =>
            strings(record, [
              "id",
              "petId",
              "type",
              "recordedDate",
              "createdAt",
              "updatedAt",
              "body",
              "question",
              "photoUrl",
            ]) &&
            ["DIARY", "TEXT", "PHOTO", "VIDEO", "VOICE"].includes(
              record.type,
            ) &&
            /^\d{4}-\d{2}-\d{2}$/.test(record.recordedDate) &&
            (record.attachments === undefined ||
              (Array.isArray(record.attachments) &&
                record.attachments.every(
                  (file) =>
                    strings(file, ["id", "type", "url", "name", "mimeType"]) &&
                    ["PHOTO", "VIDEO", "VOICE"].includes(file.type),
                ))),
        )
      )
        throw Error();
      return value;
    } catch {
      throw Error(
        "브라우저의 기록을 읽을 수 없어요. 저장소 설정을 확인해 주세요. 기존 데이터는 유지됩니다.",
      );
    }
  }
  async save(accountId: string, state: Workspace): Promise<void> {
    try {
      localStorage.setItem(
        `pawmento.workspace.v1.${accountId}`,
        JSON.stringify(state),
      );
    } catch {
      throw Error(
        "브라우저 저장 공간이 부족하거나 차단되어 있어요. 사진 크기를 줄여 다시 시도해 주세요.",
      );
    }
  }
}
