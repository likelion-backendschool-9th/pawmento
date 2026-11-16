import type { Workspace } from "../domain/models.ts";
export interface WorkspaceRepository {
  load(accountId: string): Promise<Workspace>;
  save(accountId: string, workspace: Workspace): Promise<void>;
}
export interface RuntimePort {
  id(): string;
  now(): string;
}
