import type { WorkspaceRepository } from "../../src/workspace/application/ports.ts";
import { emptyWorkspace } from "../../src/workspace/domain/models.ts";
import type { Workspace } from "../../src/workspace/domain/models.ts";
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
      try {
        await this.save(accountId, migrated);
      } catch {
        // v2 복사본을 저장할 공간이 없어도 검증한 v1 기록은 읽을 수 있다.
        // 변경은 일반 save를 통하므로 영속화 실패를 성공으로 처리하지 않는다.
        return migrated;
      }
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
