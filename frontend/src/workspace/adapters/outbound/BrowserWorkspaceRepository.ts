import type { WorkspaceRepository } from "../../application/ports.ts";
import { emptyWorkspace } from "../../domain/models.ts";
import type { Workspace } from "../../domain/models.ts";
import {
  migrateLegacyWorkspace,
  readWorkspaceSnapshot,
} from "./workspaceStorage.ts";

export class BrowserWorkspaceRepository implements WorkspaceRepository {
  async load(accountId: string): Promise<Workspace> {
    try {
      const current = localStorage.getItem(
        `pawmento.workspace.v2.${accountId}`,
      );
      if (current !== null) return readWorkspaceSnapshot(JSON.parse(current));
      const legacy = localStorage.getItem(`pawmento.workspace.v1.${accountId}`);
      if (legacy === null) return emptyWorkspace();
      const migrated = readWorkspaceSnapshot({
        schemaVersion: 2,
        state: migrateLegacyWorkspace(JSON.parse(legacy)),
      });
      // 한 스냅샷으로 기록해 중간 이관 상태를 만들지 않는다. v1은 복구용으로 보존한다.
      await this.save(accountId, migrated);
      return migrated;
    } catch {
      throw Error(
        "브라우저의 기록을 읽거나 이관할 수 없어요. 기존 데이터는 유지됩니다. 저장소 설정을 확인해 주세요.",
      );
    }
  }
  async save(accountId: string, state: Workspace): Promise<void> {
    try {
      localStorage.setItem(
        `pawmento.workspace.v2.${accountId}`,
        JSON.stringify({ schemaVersion: 2, state }),
      );
    } catch {
      throw Error(
        "브라우저 저장 공간이 부족하거나 차단되어 있어요. 기존 기록은 유지됩니다.",
      );
    }
  }
}
