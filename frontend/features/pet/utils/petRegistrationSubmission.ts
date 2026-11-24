import { ApplicationError } from "../../../lib/application/ApplicationError.ts";
import type { SessionSource } from "../../member/types/account.ts";
import { messageOf } from "../../shell/utils/presentation.ts";
import type { ManagedPet, RegisterPetInput } from "../types/ManagedPet.ts";

type RegistrationSession = { source: SessionSource; accountId: string | null };

export type RegistrationState =
  | { status: "idle"; message?: string }
  | { status: "saving" | "saved" }
  | { status: "unconfirmed" | "login-required"; message: string };

export function createPetRegistrationSubmission(getSession: () => RegistrationSession | null) {
  let status: RegistrationState["status"] = "idle";

  return async (
    input: RegisterPetInput,
    register: (input: RegisterPetInput) => Promise<ManagedPet>,
    publish: (state: RegistrationState) => void,
    onSaved: (pet: ManagedPet) => void,
  ) => {
    const started = getSession();
    if (status !== "idle" || started?.source !== "http" || !started.accountId) return;
    const accountId = started.accountId;

    function isCurrent() {
      const session = getSession();
      return session?.source === "http" && session.accountId === accountId;
    }

    function update(state: RegistrationState) {
      status = state.status;
      publish(state);
    }

    update({ status: "saving" });
    let pet: ManagedPet;
    try {
      pet = await register(input);
    } catch (cause) {
      if (!isCurrent()) return;
      const code = cause instanceof ApplicationError ? cause.code : undefined;
      const message = messageOf(cause);
      if (code === "PET_REGISTRATION_UNCONFIRMED") update({ status: "unconfirmed", message });
      else if (code === "UNAUTHORIZED") update({ status: "login-required", message });
      else update({ status: "idle", message });
      return;
    }

    if (!isCurrent()) return;
    update({ status: "saved" });
    onSaved(pet);
  };
}
