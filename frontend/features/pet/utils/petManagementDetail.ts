import { ApplicationError } from "../../../lib/application/ApplicationError.ts";
import type { SessionSource } from "../../member/types/account.ts";
import { messageOf } from "../../shell/utils/presentation.ts";
import type { ManagedPet } from "../types/ManagedPet.ts";

export type PetDetailSession = {
  ready: boolean;
  source: SessionSource;
  accountId: string | null;
  petId: string;
};

export type PetDetailState =
  | { status: "loading" }
  | { status: "login-required" }
  | { status: "ready"; pet: ManagedPet }
  | { status: "error"; message: string; retry?: true };

export function petDetailScreen(session: PetDetailSession) {
  if (!session.ready) return "loading";
  return session.source === "http" && session.accountId ? "server" : "login-required";
}

export async function loadPetManagementDetail(
  getSession: () => PetDetailSession | null,
  get: (petId: string) => Promise<ManagedPet>,
  publish: (state: PetDetailState) => void,
) {
  const started = getSession();
  if (!started || petDetailScreen(started) !== "server") return;
  const { accountId, petId } = started;

  publish({ status: "loading" });
  let result: PetDetailState;
  try {
    result = { status: "ready", pet: await get(petId) };
  } catch (cause) {
    result = detailError(cause);
  }

  const current = getSession();
  if (
    current &&
    petDetailScreen(current) === "server" &&
    current.accountId === accountId &&
    current.petId === petId
  ) {
    publish(result);
  }
}

function detailError(cause: unknown): PetDetailState {
  const code = cause instanceof ApplicationError ? cause.code : undefined;
  switch (code) {
    case "UNAUTHORIZED":
      return { status: "login-required" };
    case "FORBIDDEN":
      return { status: "error", message: "이 반려동물의 프로필을 볼 권한이 없어요." };
    case "NOT_FOUND":
      return { status: "error", message: "반려동물을 찾을 수 없어요." };
    default:
      return { status: "error", message: messageOf(cause), retry: true };
  }
}
