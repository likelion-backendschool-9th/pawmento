import test from "node:test";
import assert from "node:assert/strict";
import { createId } from "../src/shared/id.ts";

const uuid =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
test("#12-S1 HTTPS 및 localhost에서 UUID v4를 생성한다", () => {
  assert.match(createId(), uuid);
});
test("#12-S1 LAN HTTP처럼 randomUUID가 없어도 중복 없는 UUID v4를 생성한다", () => {
  const original = Object.getOwnPropertyDescriptor(globalThis, "crypto")!;
  const getRandomValues = globalThis.crypto.getRandomValues.bind(
    globalThis.crypto,
  );
  Object.defineProperty(globalThis, "crypto", {
    configurable: true,
    value: { getRandomValues },
  });
  try {
    const ids = Array.from({ length: 1000 }, () => createId());
    ids.forEach((id) => assert.match(id, uuid));
    assert.equal(new Set(ids).size, ids.length);
  } finally {
    Object.defineProperty(globalThis, "crypto", original);
  }
});
