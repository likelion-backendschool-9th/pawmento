import test from "node:test";
import assert from "node:assert/strict";
import { createOnboardingPreference, ONBOARDING_STORAGE_KEY } from "../features/onboarding/utils/onboardingPreference.ts";

test("#233-S9 최초 진입은 읽기만으로 완료되지 않고 완료 후 같은 기기에서 유지된다", () => {
  const data = new Map<string, string>();
  const storage = { getItem: (key: string) => data.get(key) ?? null, setItem: (key: string, value: string) => { data.set(key, value); } };
  const first = createOnboardingPreference(() => storage);
  assert.equal(first.hasCompleted(), false);
  assert.equal(data.size, 0);
  first.complete();
  assert.equal(data.get(ONBOARDING_STORAGE_KEY), "complete");
  assert.equal(createOnboardingPreference(() => storage).hasCompleted(), true);
});
test("#233-S9 저장소 접근이 금지되어도 현재 탭에서 완료·건너뛰기를 유지한다", () => {
  const preference = createOnboardingPreference(() => { throw new Error("SecurityError"); });
  assert.equal(preference.hasCompleted(), false);
  assert.doesNotThrow(() => preference.complete());
  assert.equal(preference.hasCompleted(), true);
});
test("#233-S9 용량 초과로 쓰기만 실패해도 현재 탭의 완료 상태를 유지한다", () => {
  const preference = createOnboardingPreference(() => ({ getItem: () => null, setItem: () => { throw new Error("QuotaExceededError"); } }));
  preference.complete();
  assert.equal(preference.hasCompleted(), true);
});
test("#233-S9 잘못된 이전 값은 완료로 간주하지 않고 유효한 완료 값만 읽는다", () => {
  let value: string | null = "true";
  const preference = createOnboardingPreference(() => ({ getItem: () => value, setItem: (_key, next) => { value = next; } }));
  assert.equal(preference.hasCompleted(), false);
  value = "complete";
  assert.equal(preference.hasCompleted(), true);
  value = null;
  assert.equal(preference.hasCompleted(), false);
});
