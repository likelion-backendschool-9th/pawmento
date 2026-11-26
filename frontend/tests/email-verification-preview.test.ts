import test from "node:test";
import assert from "node:assert/strict";
import { DemoEmailVerification } from "../mocks/handlers/DemoEmailVerification.ts";

test("#233-S9 이메일 체험은 코드 확인·대상 이메일 일치·재전송·만료를 구분한다", async () => {
  let now = 0;
  const demo = new DemoEmailVerification(() => now);
  await assert.rejects(demo.send("not-email"));
  await assert.rejects(demo.verify("test@example.test", "123456"));
  const first = await demo.send(" Test@Example.test ");
  assert.equal(demo.isVerified("test@example.test"), false);
  await assert.rejects(demo.verify("other@example.test", first.demoCode));
  await assert.rejects(demo.verify("test@example.test", "000000"));
  await demo.verify("test@example.test", first.demoCode);
  assert.equal(demo.isVerified("TEST@example.test"), true);
  await assert.rejects(demo.send("test@example.test"));
  now = first.resendAt;
  const second = await demo.send("test@example.test");
  assert.equal(demo.isVerified("test@example.test"), false);
  now = second.expiresAt;
  await assert.rejects(demo.verify("test@example.test", second.demoCode));
  assert.equal(demo.isVerified("test@example.test"), false);
  await demo.send("test@example.test");
  await demo.verify("test@example.test", second.demoCode);
  demo.reset();
  assert.equal(demo.isVerified("test@example.test"), false);
});

test("#233-S9 이메일 인증 체험 상태는 다른 회원가입 폼으로 공유되지 않는다", async () => {
  const first = new DemoEmailVerification();
  const second = new DemoEmailVerification();
  const sent = await first.send("test@example.test");
  await first.verify("test@example.test", sent.demoCode);
  assert.equal(second.isVerified("test@example.test"), false);
});
