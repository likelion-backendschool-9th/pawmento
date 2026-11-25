import { ApplicationError } from "../../../lib/application/ApplicationError.ts";
import { messageOf } from "../../shell/utils/presentation.ts";
import type { ManagedPet } from "../types/ManagedPet.ts";

type SaveMode = (petId: string, mode: ManagedPet["mode"]) => Promise<void>;

type ModeResult =
  | { status: "saved" }
  | { status: "login-required" }
  | { status: "error"; message: string };

export function createModeSubmission(isActive: () => boolean, save: SaveMode) {
  let pending = false;

  return async (pet: Pick<ManagedPet, "id" | "mode">, selected: ManagedPet["mode"]): Promise<ModeResult | undefined> => {
    if (pending || !isActive() || selected === pet.mode) return;
    pending = true;

    try {
      await save(pet.id, selected);
      return isActive() ? { status: "saved" } : undefined;
    } catch (cause) {
      if (!isActive()) return;
      if (cause instanceof ApplicationError && cause.code === "UNAUTHORIZED") {
        return { status: "login-required" };
      }
      return { status: "error", message: messageOf(cause) };
    } finally {
      pending = false;
    }
  };
}
