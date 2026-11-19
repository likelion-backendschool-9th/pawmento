import test from "node:test";
import assert from "node:assert/strict";
import { createLifeUseCases } from "../src/life/application/useCases.ts";
import { initialLifeState } from "../src/life/adapters/outbound/BrowserLifeRepository.ts";
import { SharedWorkspaceRepository } from "../src/life/adapters/outbound/SharedWorkspaceRepository.ts";
import { createWorkspaceUseCases } from "../src/workspace/application/useCases.ts";
import type { Actor, LifeState } from "../src/life/domain/models.ts";
import type { Workspace, JournalItem } from "../src/workspace/domain/models.ts";
const alice: Actor = { id: "alice", name: "앨리스", isAdmin: false };
const bob: Actor = { id: "bob", name: "밥", isAdmin: false };
const admin: Actor = { id: "demo", name: "관리자", isAdmin: true };
function setup() {
  let state = initialLifeState();
  let counter = 0;
  let now = "2026-11-15T00:00:00.000Z";
  let failGenerator = false;
  const repository = {
    load: async () => structuredClone(state),
    save: async (value: LifeState) => {
      state = structuredClone(value);
    },
  };
  const runtime = { id: () => `id-${++counter}`, now: () => now };
  const uc = createLifeUseCases(
    repository,
    {
      generate: async (records) => {
        if (failGenerator) throw Error("generation failed");
        return {
          text: records.map((record) => record.body).join("\n"),
          imageUrl: records[0].photoUrl,
        };
      },
    },
    runtime,
  );
  return {
    uc,
    repository,
    runtime,
    setTime: (value: string) => {
      now = value;
    },
    fail: () => {
      failGenerator = true;
    },
    recover: () => {
      failGenerator = false;
    },
  };
}
const source: JournalItem = {
  id: "memory-1",
  petId: "pet-a",
  kind: "QUESTION_ANSWER",
  type: "QUESTION_ANSWER",
  authorId: "alice",
  promptId: "prompt",
  registeredAt: "2026-11-15T00:00:00.000Z",
  questionSnapshot: "오늘은 어땠어?",
  question: "오늘은 어땠어?",
  questionId: "q-original",
  body: "함께 걸어서 행복했어",
  photoUrl: "reference:running",
  recordedDate: "2026-11-15",
  createdAt: "2026-11-15T00:00:00.000Z",
  updatedAt: "2026-11-15T00:00:00.000Z",
};
test("#12-S8 게시글·댓글 소유자 제한과 좋아요·신고 중복 방지", async () => {
  const { uc } = setup();
  const post = await uc.savePost(alice, {
    category: "LIFE",
    title: "첫 산책",
    body: "즐거운 산책이었어요.",
    photoUrl: "",
  });
  await assert.rejects(
    uc.savePost(
      bob,
      { category: "LIFE", title: "타인 수정", body: "안됨", photoUrl: "" },
      post.id,
    ),
    /작성자/,
  );
  await assert.rejects(uc.deletePost(bob, post.id), /작성자/);
  const comment = await uc.saveComment(bob, post.id, "좋은 하루였네요.");
  await assert.rejects(uc.deleteComment(alice, comment.id), /작성자/);
  await uc.saveComment(bob, post.id, "댓글 수정", comment.id);
  await uc.togglePost(bob, post.id, "likes");
  await uc.togglePost(bob, post.id, "likes");
  assert.equal(
    (await uc.load()).posts.find((item) => item.id === post.id)!.likes.length,
    0,
  );
  await uc.togglePost(bob, post.id, "bookmarks");
  assert.equal(
    (await uc.load()).posts.find((item) => item.id === post.id)!.bookmarks[0],
    bob.id,
  );
  await uc.reportPost(bob, post.id, "부적절한 내용");
  await assert.rejects(uc.reportPost(bob, post.id, "중복 신고"), /이미 신고/);
  const report = (await uc.load()).reports[0];
  await assert.rejects(
    uc.resolveReport(alice, report.id, "RESOLVED"),
    /관리자/,
  );
  await uc.resolveReport(admin, report.id, "RESOLVED");
  assert.equal(
    (await uc.load()).posts.find((item) => item.id === post.id)!.hidden,
    true,
  );
});
test("#12-S8 본인 게시글 삭제 시 관련 댓글 정리", async () => {
  const { uc } = setup();
  const post = await uc.savePost(alice, {
    category: "MEMORY",
    title: "기억",
    body: "소중한 기억",
    photoUrl: "",
  });
  await uc.saveComment(bob, post.id, "함께 기억해요");
  await uc.deletePost(alice, post.id);
  assert.ok(!(await uc.load()).posts.some((item) => item.id === post.id));
  assert.ok(
    !(await uc.load()).comments.some((item) => item.postId === post.id),
  );
});
test("#12-S9 엽서 동의·원본 참조·실패 상태·재시도·보관", async () => {
  const env = setup();
  await assert.rejects(
    env.uc.requestPostcard(alice, "pet-a", "보리", [source], false),
    /안내/,
  );
  await assert.rejects(
    env.uc.requestPostcard(alice, "different-pet", "보리", [source], true),
    /이 친구/,
  );
  env.fail();
  const failed = await env.uc.requestPostcard(
    alice,
    "pet-a",
    "보리",
    [source],
    true,
  );
  assert.equal(failed.status, "FAILED");
  env.recover();
  const completed = await env.uc.requestPostcard(
    alice,
    "pet-a",
    "보리",
    [source],
    true,
    failed.id,
  );
  assert.equal(completed.id, failed.id);
  assert.equal(completed.status, "COMPLETED");
  assert.deepEqual(completed.sourceEntries, [
    { kind: source.kind, id: source.id },
  ]);
  assert.equal(source.question, "오늘은 어땠어?");
  await assert.rejects(env.uc.togglePostcard(bob, completed.id), /작성자/);
  await env.uc.togglePostcard(alice, completed.id);
  assert.equal((await env.uc.load()).postcards[0].saved, true);
  await env.uc.deletePostcard(alice, completed.id);
  assert.equal((await env.uc.load()).postcards.length, 0);
});
test("#12-S10 초대 만료·중복 수락·역할 변경·공유 해제", async () => {
  const env = setup();
  const expired = await env.uc.inviteFamily(alice, "pet-a", "EDITOR");
  env.setTime("2026-11-24T00:00:00.000Z");
  await assert.rejects(env.uc.acceptInvite(bob, expired.id, "밥"), /만료/);
  const invitation = await env.uc.inviteFamily(alice, "pet-a", "VIEWER");
  const family = await env.uc.acceptInvite(bob, invitation.id, "밥");
  assert.equal(family.members[0].role, "VIEWER");
  await assert.rejects(
    env.uc.acceptInvite(bob, invitation.id, "중복"),
    /이미 사용/,
  );
  await assert.rejects(
    env.uc.changeFamilyRole(bob, "pet-a", family.members[0].id, "EDITOR"),
    /작성자/,
  );
  await env.uc.changeFamilyRole(alice, "pet-a", family.members[0].id, "EDITOR");
  assert.equal((await env.uc.load()).families[0].members[0].role, "EDITOR");
  await env.uc.removeFamilyMember(alice, "pet-a", family.members[0].id);
  assert.equal((await env.uc.load()).families[0].members.length, 0);
});
test("#12-S10 같은 브라우저의 공유 기록·열람자/편집자·공유 해제 검증", async () => {
  const env = setup();
  const stores = new Map<string, Workspace>();
  const ownPet = {
    id: "pet-a",
    name: "보리",
    species: "DOG" as const,
    breed: "",
    birthday: "",
    photoUrl: "",
    mode: "ACTIVE" as const,
  };
  stores.set("alice", {
    pets: [ownPet],
    diaries: [],
    questionAnswers: [
      source as Extract<JournalItem, { kind: "QUESTION_ANSWER" }>,
    ],
    questionPrompts: [
      {
        id: "prompt",
        petId: ownPet.id,
        mode: "ACTIVE",
        questionId: "q-original",
        questionSnapshot: "오늘은 어땠어?",
        createdAt: source.createdAt,
      },
    ],
    selectedPetId: ownPet.id,
  });
  const base = {
    load: async (id: string) =>
      structuredClone(
        stores.get(id) ?? {
          pets: [],
          diaries: [],
          questionAnswers: [],
          questionPrompts: [],
          selectedPetId: "",
        },
      ),
    save: async (id: string, value: Workspace) => {
      stores.set(id, structuredClone(value));
    },
  };
  const invite = await env.uc.inviteFamily(alice, ownPet.id, "VIEWER");
  const family = await env.uc.acceptInvite(bob, invite.id, "밥");
  const repository = new SharedWorkspaceRepository(base, env.repository);
  const uc = createWorkspaceUseCases(repository, env.runtime);
  const shared = await uc.load("bob");
  assert.equal(shared.pets[0].accessRole, "VIEWER");
  assert.equal(shared.questionAnswers[0].questionSnapshot, source.question);
  await assert.rejects(
    uc.questions.save("bob", { ...source, body: "열람자 수정" }, source.id),
    /열람자/,
  );
  await env.uc.changeFamilyRole(
    alice,
    ownPet.id,
    family.members[0].id,
    "EDITOR",
  );
  await assert.rejects(
    uc.questions.save(
      "bob",
      { ...source, body: "가족의 답변 수정" },
      source.id,
    ),
    /작성자/,
  );
  assert.equal(stores.get("alice")!.questionAnswers[0].body, source.body);
  assert.equal(
    stores.get("alice")!.questionAnswers[0].questionSnapshot,
    source.question,
  );
  await assert.rejects(uc.changeMode("bob", ownPet.id, "GARDEN"), /소유자/);
  await uc.clear("bob");
  assert.equal(stores.get("alice")!.questionAnswers.length, 1);
  await env.uc.removeFamilyMember(alice, ownPet.id, family.members[0].id);
  assert.equal((await uc.load("bob")).pets.length, 0);
});
test("#12-S12 운영 질문·공지·회원은 관리자만 변경", async () => {
  const { uc } = setup();
  await assert.rejects(uc.saveQuestion(alice, "새 질문"), /관리자/);
  await uc.saveQuestion(admin, "새 질문");
  const q = (await uc.load()).questions.at(-1)!;
  await uc.toggleQuestion(admin, q.id);
  assert.equal((await uc.load()).questions.at(-1)!.enabled, false);
  await uc.saveNotice(admin, "공지 제목", "안내 본문");
  const notice = (await uc.load()).notices[0];
  await uc.saveNotice(admin, "수정한 제목", "수정한 본문", notice.id);
  assert.equal((await uc.load()).notices[0].title, "수정한 제목");
  await uc.toggleNotice(admin, notice.id);
  assert.equal((await uc.load()).notices[0].published, false);
  await uc.registerMember(alice);
  await uc.toggleMember(admin, alice.id);
  assert.equal((await uc.registerMember(alice)).status, "SUSPENDED");
  await assert.rejects(uc.toggleMember(admin, admin.id), /자신/);
});

test("#12-S9 하루 생성 한도는 한국 날짜의 자정에 갱신", async () => {
  const env = setup();
  env.setTime("2026-11-15T14:59:00.000Z");
  for (let i = 0; i < 5; i++)
    await env.uc.requestPostcard(alice, "pet-a", "보리", [source], true);
  await assert.rejects(
    env.uc.requestPostcard(alice, "pet-a", "보리", [source], true),
    /하루 5장/,
  );
  env.setTime("2026-11-15T15:00:00.000Z");
  assert.equal(
    (await env.uc.requestPostcard(alice, "pet-a", "보리", [source], true))
      .status,
    "COMPLETED",
  );
});
test("#12-S9 새로고침으로 중단된 PROCESSING 데모를 같은 엽서 ID로 재시도", async () => {
  const env = setup();
  const stuck = createLifeUseCases(
    env.repository,
    { generate: async () => new Promise(() => undefined) },
    env.runtime,
  );
  void stuck.requestPostcard(alice, "pet-a", "보리", [source], true);
  await new Promise((resolve) => setImmediate(resolve));
  const pending = (await env.repository.load()).postcards[0];
  assert.equal(pending.status, "PROCESSING");
  const recovered = await env.uc.requestPostcard(
    alice,
    "pet-a",
    "보리",
    [source],
    true,
    pending.id,
  );
  assert.equal(recovered.id, pending.id);
  assert.equal(recovered.status, "COMPLETED");
  assert.equal((await env.uc.load()).postcards.length, 1);
});
