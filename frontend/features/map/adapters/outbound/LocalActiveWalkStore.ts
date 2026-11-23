import { isDemoWalkId } from "../../domain/Walk.ts";
import type { ActiveWalk, ActiveWalkStore } from "../../application/walkPorts.ts";

type StorageLike = Pick<Storage, "getItem" | "setItem" | "removeItem">;

// 계정(owner)과 반려동물을 함께 키로 쓴다. 같은 반려동물을 여러 계정이 쓰더라도 진행 중인 경로가 섞이지 않는다.
const key = (owner: string, petId: string) =>
  `pawmento.activeWalk.v2.${encodeURIComponent(owner)}.${encodeURIComponent(petId)}`;

const isActiveWalk = (value: unknown): value is ActiveWalk => {
  const active = value as ActiveWalk | null;
  return (
    !!active &&
    typeof active.walk?.walkId === "string" &&
    typeof active.walk?.petId === "string" &&
    typeof active.walk?.startedAt === "string" &&
    Array.isArray(active.points) &&
    Number.isInteger(active.sentCount) &&
    active.sentCount >= 0 &&
    active.sentCount <= active.points.length
  );
};

// 진행 중인 산책을 새로고침해도 이어 가도록 남긴다.
// - 서버 산책은 이 브라우저(localStorage)에 남긴다. 저장소를 못 쓰면(비공개 창 등) 조용히 건너뛴다.
// - 데모 산책은 데모 저장소(메모리)와 수명을 맞춰 메모리에만 둔다. 새로고침하면 데모 산책도 함께 사라지므로 복원 대상이 아니다.
export class LocalActiveWalkStore implements ActiveWalkStore {
  private readonly storage: StorageLike | null;
  private readonly memory = new Map<string, ActiveWalk>();

  constructor(storage?: StorageLike | null) {
    this.storage =
      storage !== undefined
        ? storage
        : typeof localStorage !== "undefined"
          ? localStorage
          : null;
  }

  load(owner: string, petId: string): ActiveWalk | null {
    const remembered = this.memory.get(key(owner, petId));
    if (remembered) return remembered;
    try {
      const raw = this.storage?.getItem(key(owner, petId));
      if (!raw) return null;
      const parsed: unknown = JSON.parse(raw);
      return isActiveWalk(parsed) &&
        parsed.walk.petId === petId &&
        !isDemoWalkId(parsed.walk.walkId)
        ? parsed
        : null;
    } catch {
      return null;
    }
  }

  save(owner: string, active: ActiveWalk): void {
    const id = key(owner, active.walk.petId);
    if (isDemoWalkId(active.walk.walkId)) {
      this.memory.set(id, active);
      return;
    }
    try {
      this.storage?.setItem(id, JSON.stringify(active));
    } catch {
      /* 저장하지 못해도 산책은 계속된다 */
    }
  }

  clear(owner: string, petId: string): void {
    this.memory.delete(key(owner, petId));
    try {
      this.storage?.removeItem(key(owner, petId));
    } catch {
      /* ignore */
    }
  }
}
