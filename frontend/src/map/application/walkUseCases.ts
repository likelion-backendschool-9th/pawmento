import { WalkRecorder } from "./WalkRecorder.ts";
import type { ActiveWalkStore, WalkRepository } from "./walkPorts.ts";
import type { Walk, WalkOutcome } from "../domain/Walk.ts";

// owner는 로그인한 계정 id다. 진행 중인 산책을 계정별로 따로 남기려고 쓴다.
export function createWalkUseCases(
  repository: WalkRepository,
  store: ActiveWalkStore,
) {
  return {
    // 산책을 시작하고 이어 갈 수 있게 남겨 둔다
    async start(
      owner: string,
      petId: string,
    ): Promise<{ walk: Walk; recorder: WalkRecorder }> {
      const walk = await repository.start(petId);
      store.save(owner, { walk, points: [], sentCount: 0 });
      return { walk, recorder: new WalkRecorder(repository, walk.walkId) };
    },

    // 새로고침 전에 걷던 산책이 있으면 이어 간다
    resume(
      owner: string,
      petId: string,
    ): { walk: Walk; recorder: WalkRecorder } | null {
      const active = store.load(owner, petId);
      if (!active) return null;
      return {
        walk: active.walk,
        recorder: new WalkRecorder(repository, active.walk.walkId, active),
      };
    },

    // 지금까지 모은 점과 보낸 개수를 남겨 둔다
    remember(owner: string, walk: Walk, recorder: WalkRecorder): void {
      store.save(owner, {
        walk,
        points: [...recorder.points],
        sentCount: recorder.sentCount,
      });
    },

    forget(owner: string, petId: string): void {
      store.clear(owner, petId);
    },

    // 남은 좌표를 모두 보낸 뒤 산책을 끝낸다.
    // - 좌표를 못 보내면(연결 문제) 끝내지 않고 오류를 던진다. 산책은 닫히지 않은 채 남는다.
    // - 서버가 이미 닫은 산책이면(WALK_CLOSED) 더 보낼 수 없으니 종료 응답만 받는다. 서버는 중단된 산책에 INTERRUPTED를 돌려준다.
    // - flush를 false로 주면 연결이 계속 불안정해도 남은 좌표를 포기하고 끝낼 수 있다.
    // 돌려주는 complete가 false면 서버에 저장하지 못한 좌표가 남았다는 뜻이다.
    async end(
      owner: string,
      walk: Walk,
      recorder: WalkRecorder,
      options: { flush: boolean } = { flush: true },
    ): Promise<WalkOutcome> {
      if (options.flush) {
        try {
          await recorder.flush();
        } catch (cause) {
          if (!recorder.closed) throw cause;
        }
      }
      const result = await repository.end(walk.walkId);
      store.clear(owner, walk.petId);
      return { ...result, complete: recorder.pendingCount === 0 };
    },
  };
}
