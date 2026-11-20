import type { ActiveWalk, ActiveWalkStore } from "../../application/walkPorts.ts";

type StorageLike = Pick<Storage, "getItem" | "setItem" | "removeItem">;

const key = (petId: string) => `pawmento.activeWalk.v1.${petId}`;

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

// 진행 중인 산책을 새로고침해도 이어 가도록 이 브라우저에 남긴다. 저장소를 못 쓰면(비공개 창 등) 조용히 건너뛴다.
export class LocalActiveWalkStore implements ActiveWalkStore {
  private readonly storage: StorageLike | null;

  constructor(storage?: StorageLike | null) {
    this.storage =
      storage !== undefined
        ? storage
        : typeof localStorage !== "undefined"
          ? localStorage
          : null;
  }

  load(petId: string): ActiveWalk | null {
    try {
      const raw = this.storage?.getItem(key(petId));
      if (!raw) return null;
      const parsed: unknown = JSON.parse(raw);
      return isActiveWalk(parsed) && parsed.walk.petId === petId ? parsed : null;
    } catch {
      return null;
    }
  }

  save(active: ActiveWalk): void {
    try {
      this.storage?.setItem(key(active.walk.petId), JSON.stringify(active));
    } catch {
      /* 저장하지 못해도 산책은 계속된다 */
    }
  }

  clear(petId: string): void {
    try {
      this.storage?.removeItem(key(petId));
    } catch {
      /* ignore */
    }
  }
}
