import { ApplicationError } from "../../../lib/application/ApplicationError.ts";
import type { SessionSource } from "../../member/types/account.ts";
import { messageOf } from "../../shell/utils/presentation.ts";
import type { ManagedPet } from "../types/ManagedPet.ts";

export type PetListSession = {
  ready: boolean;
  source: SessionSource;
  accountId: string | null;
};

export type PetListState =
  | { status: "loading" | "login-required" }
  | { status: "ready"; pets: ManagedPet[] }
  | { status: "error"; message: string };

export function petListScreen(session: PetListSession) {
  if (!session.ready) return "loading";
  if (!session.accountId) return "login-required";
  if (session.source === "demo") return "demo";
  return session.source === "http" ? "server" : "login-required";
}

export async function loadPetManagementList(
  getSession: () => PetListSession | null,
  listMine: () => Promise<ManagedPet[]>,
  publish: (state: PetListState) => void,
) {
  const started = getSession();
  if (!started || petListScreen(started) !== "server") return;
  const accountId = started.accountId;

  publish({ status: "loading" });
  let result: PetListState;
  try {
    result = { status: "ready", pets: await listMine() };
  } catch (cause) {
    result = cause instanceof ApplicationError && cause.code === "UNAUTHORIZED"
      ? { status: "login-required" }
      : { status: "error", message: messageOf(cause) };
  }

  const current = getSession();
  if (current && petListScreen(current) === "server" && current.accountId === accountId) {
    publish(result);
  }
}
