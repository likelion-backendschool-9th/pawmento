import test from "node:test";
import assert from "node:assert/strict";
import { BrowserWorkspaceRepository } from "../src/workspace/adapters/outbound/BrowserWorkspaceRepository.ts";
import { emptyWorkspace } from "../src/workspace/domain/models.ts";
test("#32-S29 v1을 일기·질문 답변으로 이관하며 ID·스냅샷·작성자·기존 영상을 보존한다", async () => {
  const values = new Map<string, string>();
  let fail = false;
  Object.defineProperty(globalThis, "localStorage", {
    configurable: true,
    value: {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => {
        if (fail) throw Error("quota");
        values.set(key, value);
      },
    },
  });
  try {
    const pet = {
      id: "pet",
      name: "보리",
      species: "DOG",
      breed: "코기",
      birthday: "",
      photoUrl: "",
      mode: "ACTIVE",
    };
    const base = {
      petId: "pet",
      recordedDate: "2026-11-01",
      createdAt: "2026-11-03T00:00:00Z",
      updatedAt: "2026-11-04T00:00:00Z",
      body: "내용",
      question: "원래 질문",
      photoUrl: "",
    };
    const raw = JSON.stringify({
      pets: [pet],
      selectedPetId: "pet",
      records: [
        { ...base, id: "a", type: "DIARY", authorId: "alice", questionId: "q" },
        {
          ...base,
          id: "b",
          type: "VIDEO",
          attachments: [
            {
              id: "f",
              type: "VIDEO",
              url: "data:video/mp4;base64,AA",
              name: "old.mp4",
              mimeType: "video/mp4",
            },
          ],
        },
      ],
    });
    values.set("pawmento.workspace.v1.a", raw);
    const repo = new BrowserWorkspaceRepository();
    fail = true;
    const fallback = await repo.load("a");
    assert.equal(fallback.diaries[0].id, "b");
    assert.equal(fallback.questionAnswers[0].id, "a");
    await assert.rejects(repo.save("a", fallback));
    assert.equal(values.has("pawmento.workspace.v2.a"), false);
    assert.equal(values.get("pawmento.workspace.v1.a"), raw);
    fail = false;
    const state = await repo.load("a");
    assert.equal(state.diaries[0].id, "b");
    assert.equal(state.diaries[0].attachments?.[0].type, "VIDEO");
    assert.equal(state.questionAnswers[0].id, "a");
    assert.equal(state.questionAnswers[0].authorId, "alice");
    assert.equal(state.questionAnswers[0].registeredAt, base.createdAt);
    assert.equal(
      state.questionAnswers[0].legacyRecordedDate,
      base.recordedDate,
    );
    assert.equal(state.questionPrompts[0].mode, "LEGACY");
    assert.equal(state.questionPrompts[0].questionSnapshot, "원래 질문");
    assert.equal(state.diaries[0].authorId, undefined);
    assert.deepEqual(await repo.load("a"), JSON.parse(JSON.stringify(state)));
    assert.equal(values.get("pawmento.workspace.v1.a"), raw);
    values.set("pawmento.workspace.v2.a", "bad");
    await assert.rejects(repo.load("a"));
    assert.equal(values.get("pawmento.workspace.v2.a"), "bad");
    await repo.save("a", emptyWorkspace());
    assert.deepEqual(await repo.load("a"), emptyWorkspace());
  } finally {
    Reflect.deleteProperty(globalThis, "localStorage");
  }
});

test("#32-S29 과거 엽서 원본 ID는 보존하고 모드 없는 질문은 임의 분류하지 않는다", async () => {
  const { BrowserLifeRepository, initialLifeState } =
    await import("../src/life/adapters/outbound/BrowserLifeRepository.ts");
  const original = initialLifeState();
  const question = original.questions[0];
  const raw = JSON.stringify({
    ...original,
    questions: [{ id: question.id, text: question.text, enabled: true }],
    postcards: [
      {
        id: "old",
        sourceRecordIds: ["answer-1"],
        text: "추억",
        imageUrl: "data:image/png;base64,AA",
      },
    ],
  });
  let stored = raw;
  Object.defineProperty(globalThis, "localStorage", {
    configurable: true,
    value: {
      getItem: () => stored,
      setItem: (_: string, v: string) => {
        stored = v;
      },
    },
  });
  try {
    const repo = new BrowserLifeRepository();
    const state = await repo.load();
    assert.equal(state.questions[0].mode, "LEGACY");
    assert.ok(state.questions.some((q) => q.mode === "ACTIVE"));
    assert.ok(state.questions.some((q) => q.mode === "GARDEN"));
    assert.deepEqual(state.postcards[0].sourceEntries, []);
    assert.deepEqual(state.postcards[0].legacySourceRecordIds, ["answer-1"]);
    assert.equal(state.postcards[0].text, "추억");
    await repo.save(state);
    assert.equal((await repo.load()).questions.length, state.questions.length);
  } finally {
    Reflect.deleteProperty(globalThis, "localStorage");
  }
});

test("#32-S29 5MiB 한도에 v2 복사본을 둘 수 없어도 기존 음성 기록을 조회한다", async () => {
  const { createWorkspaceUseCases } =
    await import("../src/workspace/application/useCases.ts");
  const values = new Map<string, string>();
  let limit = 5 * 1024 * 1024;
  const storage = {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => {
      const next = new Map(values);
      next.set(key, value);
      const bytes = [...next].reduce(
        (sum, [k, v]) => sum + 2 * (k.length + v.length),
        0,
      );
      if (bytes > limit) throw new DOMException("quota", "QuotaExceededError");
      values.set(key, value);
    },
  };
  Object.defineProperty(globalThis, "localStorage", {
    configurable: true,
    value: storage,
  });
  try {
    const pet = {
      id: "pet",
      name: "보리",
      species: "DOG",
      breed: "코기",
      birthday: "",
      photoUrl: "",
      mode: "ACTIVE",
    };
    const raw = JSON.stringify({
      pets: [pet],
      selectedPetId: "pet",
      records: [0, 1].map((i) => ({
        id: `voice-${i}`,
        petId: "pet",
        authorId: "owner",
        type: "VOICE",
        recordedDate: "2026-11-15",
        createdAt: "2026-11-15T00:00:00Z",
        updatedAt: "2026-11-15T00:00:00Z",
        body: "음성 기록",
        question: "",
        photoUrl: "",
        attachments: [
          {
            id: `file-${i}`,
            name: "voice.webm",
            mimeType: "audio/webm",
            type: "VOICE",
            url: "data:audio/webm;base64," + "A".repeat(1024 * 1024),
          },
        ],
      })),
    });
    storage.setItem("pawmento.workspace.v1.owner", raw);
    const repo = new BrowserWorkspaceRepository();
    const uc = createWorkspaceUseCases(repo, {
      id: () => "new",
      now: () => "2026-11-19T00:00:00Z",
    });
    const page = await uc.diary.list("owner", "pet");
    assert.equal(page.total, 2);
    assert.deepEqual(
      page.entries.map((d) => d.id),
      ["voice-1", "voice-0"],
    );
    assert.equal(page.entries[0].attachments?.[0].url.length, 1024 * 1024 + 23);
    assert.equal(values.has("pawmento.workspace.v2.owner"), false);
    assert.equal(values.get("pawmento.workspace.v1.owner"), raw);
    await assert.rejects(
      uc.diary.save(
        "owner",
        {
          petId: "pet",
          body: "수정",
          recordedDate: "2026-11-15",
          photoUrl: "",
        },
        "voice-0",
      ),
      /저장 공간/,
    );
    assert.equal(
      (await uc.diary.list("owner", "pet")).entries.find(
        (d) => d.id === "voice-0",
      )?.body,
      "음성 기록",
    );
    limit = 10 * 1024 * 1024;
    const migrated = await repo.load("owner");
    assert.equal(values.has("pawmento.workspace.v2.owner"), true);
    assert.deepEqual(
      await repo.load("owner"),
      JSON.parse(JSON.stringify(migrated)),
    );
  } finally {
    Reflect.deleteProperty(globalThis, "localStorage");
  }
});
