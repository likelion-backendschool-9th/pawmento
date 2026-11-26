import test from "node:test";
import assert from "node:assert/strict";
import { createDemoOnboarding } from "../mocks/handlers/demoOnboarding.ts";
import { DemoAuthRepository } from "../mocks/handlers/DemoAuthRepository.ts";
import { createLifeUseCases } from "../mocks/handlers/lifeUseCases.ts";
import { initialLifeState } from "../mocks/handlers/BrowserLifeRepository.ts";
import { createWorkspaceUseCases } from "../mocks/handlers/workspaceUseCases.ts";
import { SharedWorkspaceRepository } from "../mocks/handlers/SharedWorkspaceRepository.ts";
import { emptyWorkspace, type Workspace } from "../features/shell/types/workspace.ts";
import { createMemberSession } from "../features/member/session.ts";
import { createApiClient } from "../lib/http/apiClient.ts";

function setup() {
  let snapshot = initialLifeState();
  const lifeRepository = { load: async () => structuredClone(snapshot), save: async (next: typeof snapshot) => { snapshot = structuredClone(next); } };
  const records = new Map<string, Workspace>();
  const repository = { load: async (id: string) => structuredClone(records.get(id) ?? emptyWorkspace()), save: async (id: string, next: Workspace) => { records.set(id, structuredClone(next)); } };
  let id = 0;
  const runtime = { id: () => `onboarding-${++id}`, now: () => "2026-11-26T00:00:00Z" };
  const life = createLifeUseCases(lifeRepository, { generate: async () => { throw Error("생성하지 않음"); } }, runtime);
  const workspace = createWorkspaceUseCases(new SharedWorkspaceRepository(repository, lifeRepository), runtime);
  return { workspace, preview: createDemoOnboarding(workspace, life), records };
}

test("#233-S9 첫 시작 체험은 HTTP 없이 새 데모 세션을 만들고 기본 데모는 유지한다", async () => {
  const http = createApiClient({ fetch: async () => { throw Error("체험에서 HTTP를 호출하면 안 됨"); } });
  const auth = createMemberSession(http, new DemoAuthRepository());
  const first = await auth.startDemo({ fresh: true, nickname: "처음 기록하는 나" });
  const second = await auth.startDemo({ fresh: true });
  assert.notEqual(first.id, second.id);
  assert.equal(first.nickname, "처음 기록하는 나");
  assert.equal(first.demo, true);
  assert.equal(auth.sessionSource(), "demo");
  assert.equal((await auth.current())?.id, second.id);
  assert.equal((await auth.startDemo()).id, "demo");
});

test("#233-S9 체험 코드가 맞아야 함께하는 아이가 편집 권한으로 연결되고 홈 선택에 남는다", async () => {
  const { preview, workspace, records } = setup();
  const account = { id: "preview-member", nickname: "초대받은 나", email: "demo@local.test", demo: true as const };
  await assert.rejects(preview.join(account, "WRONG123"));
  await assert.rejects(preview.join({ ...account, demo: undefined }, preview.invitation.code));
  assert.equal(records.size, 0);
  const pet = await preview.join(account, ` ${preview.invitation.code.toLowerCase()} `);
  const state = await workspace.load(account.id);
  assert.equal(state.selectedPetId, pet.id);
  assert.equal(state.pets[0].accessRole, "EDITOR");
  assert.notEqual(state.pets[0].ownerId, account.id);
  assert.equal(state.diaries.length, 1);
  assert.equal(records.get(account.id)?.pets.length, 0);
  await preview.join(account, preview.invitation.code);
  assert.equal((await workspace.load(account.id)).pets.length, 1);
  await assert.rejects(workspace.savePet(account.id, { name: "바꿀 수 없음", species: "DOG", breed: "", birthday: "", photoUrl: "" }, pet.id));
});
