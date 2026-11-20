import { WalkRecorder } from "./WalkRecorder.ts";
import type { ActiveWalk, ActiveWalkStore, WalkRepository } from "./walkPorts.ts";
import type { Walk, WalkEnd } from "../domain/Walk.ts";

export function createWalkUseCases(
  repository: WalkRepository,
  store: ActiveWalkStore,
) {
  return {
    // 산책을 시작하고 이어 갈 수 있게 남겨 둔다
    async start(petId: string): Promise<{ walk: Walk; recorder: WalkRecorder }> {
      const walk = await repository.start(petId);
      store.save({ walk, points: [], sentCount: 0 });
      return { walk, recorder: new WalkRecorder(repository, walk.walkId) };
    },

    // 새로고침 전에 걷던 산책이 있으면 이어 간다
    resume(petId: string): { walk: Walk; recorder: WalkRecorder } | null {
      const active = store.load(petId);
      if (!active) return null;
      return {
        walk: active.walk,
        recorder: new WalkRecorder(repository, active.walk.walkId, active),
      };
    },

    // 지금까지 모은 점과 보낸 개수를 남겨 둔다
    remember(walk: Walk, recorder: WalkRecorder): void {
      const active: ActiveWalk = {
        walk,
        points: [...recorder.points],
        sentCount: recorder.sentCount,
      };
      store.save(active);
    },

    forget(petId: string): void {
      store.clear(petId);
    },

    // 남은 좌표를 모두 보낸 뒤 산책을 끝낸다. 좌표를 못 보내면 끝내지 않고 오류를 던진다(산책은 닫히지 않은 채 남는다).
    // 연결이 계속 불안정해도 보호자가 끝낼 수 있게, flush를 false로 주면 남은 좌표를 포기하고 끝낸다.
    async end(
      walk: Walk,
      recorder: WalkRecorder,
      options: { flush: boolean } = { flush: true },
    ): Promise<WalkEnd> {
      if (options.flush) await recorder.flush();
      const result = await repository.end(walk.walkId);
      store.clear(walk.petId);
      return result;
    },
  };
}
