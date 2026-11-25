import assert from "node:assert/strict";
import test from "node:test";
import { ApplicationError } from "../lib/application/ApplicationError.ts";
import {
  joinScreen,
  submitJoin,
  type JoinSession,
  type JoinState,
} from "../features/family/utils/joinFamily.ts";

const pet = { id: "42", name: "보리", tag: "0001" };
const server = (accountId: string): JoinSession => ({ ready: true, source: "http", accountId });

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (cause: unknown) => void;
  const promise = new Promise<T>((ok, fail) => {
    resolve = ok;
    reject = fail;
  });
  return { promise, resolve, reject };
}

test("#133-S25 참여 화면은 서버 세션이 확인된 회원에게만 입력을 연다", () => {
  assert.equal(joinScreen({ ready: false, source: null, accountId: null }), "loading");
  assert.equal(joinScreen({ ready: true, source: null, accountId: null }), "login-required");
  assert.equal(joinScreen({ ready: true, source: "demo", accountId: "demo" }), "login-required");
  assert.equal(joinScreen(server("7")), "server");
});

test("#133-S25 코드로 참여에 성공하면 제출 중 → 가족이 된 반려동물 순서로 알린다", async () => {
  const states: JoinState[] = [];
  const codes: string[] = [];

  await submitJoin(() => server("7"), async (code) => {
    codes.push(code);
    return pet;
  }, " k7q2m9xa ", (state) => states.push(state));

  assert.deepEqual(codes, [" k7q2m9xa "]);
  assert.deepEqual(states, [{ status: "submitting" }, { status: "joined", pet }]);
});

test("#133-S25 빈 코드는 서버에 보내지 않고 안내한다", async () => {
  const states: JoinState[] = [];

  await submitJoin(() => server("7"), () => assert.fail("예상하지 않은 서버 요청"), "   ", (state) => states.push(state));

  assert.deepEqual(states, [{ status: "error", message: "초대 코드를 입력해 주세요." }]);
});

test("#133-S25 서버 세션이 아니면 요청도 알림도 하지 않는다", async () => {
  for (const session of [null, { ready: true, source: "demo", accountId: "demo" } as JoinSession]) {
    const states: JoinState[] = [];
    await submitJoin(() => session, () => assert.fail("예상하지 않은 서버 요청"), "K7Q2M9XA", (state) => states.push(state));
    assert.deepEqual(states, []);
  }
});

test("#133-S25 로그인이 풀리면 로그인 안내로, 쓸 수 없는 코드는 서버 문구로 알린다", async () => {
  const unauthorized: JoinState[] = [];
  await submitJoin(() => server("7"), async () => {
    throw new ApplicationError("UNAUTHORIZED", "로그인이 필요합니다.");
  }, "K7Q2M9XA", (state) => unauthorized.push(state));
  assert.deepEqual(unauthorized.at(-1), { status: "login-required" });

  const notFound: JoinState[] = [];
  await submitJoin(() => server("7"), async () => {
    throw new ApplicationError("NOT_FOUND", "사용할 수 없는 초대 코드입니다.");
  }, "ZZZZZZZZ", (state) => notFound.push(state));
  assert.deepEqual(notFound.at(-1), { status: "error", message: "사용할 수 없는 초대 코드입니다." });
});

test("#133-S25 요청 뒤 다른 계정·데모로 바뀌거나 화면을 떠나면 늦은 응답을 버린다", async () => {
  const after: (JoinSession | null)[] = [
    server("8"),
    { ready: true, source: "demo", accountId: "demo" },
    null,
  ];
  for (const next of after) {
    for (const outcome of ["success", "failure"] as const) {
      let session: JoinSession | null = server("7");
      const states: JoinState[] = [];
      const response = deferred<typeof pet>();

      const pending = submitJoin(() => session, () => response.promise, "K7Q2M9XA", (state) => states.push(state));
      session = next;
      if (outcome === "success") response.resolve(pet);
      else response.reject(new ApplicationError("NOT_FOUND", "사용할 수 없는 초대 코드입니다."));
      await pending;

      assert.deepEqual(states, [{ status: "submitting" }], `${JSON.stringify(next)} / ${outcome}`);
    }
  }
});
