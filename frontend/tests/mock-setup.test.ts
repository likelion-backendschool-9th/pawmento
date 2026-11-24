import test from "node:test";
import assert from "node:assert/strict";
import { assertDevelopmentMockAllowed } from "../mocks/setup.ts";

function withEnv(
  values: Record<string, string | undefined>,
  run: () => void,
) {
  const previous = new Map<string, string | undefined>();
  for (const [key, value] of Object.entries(values)) {
    previous.set(key, process.env[key]);
    if (value === undefined) delete process.env[key];
    else process.env[key] = value;
  }
  try {
    run();
  } finally {
    for (const [key, value] of previous) {
      if (value === undefined) delete process.env[key];
      else process.env[key] = value;
    }
  }
}

test("#182 운영 빌드에서는 개발용 목업을 켤 수 없다", () => {
  for (const nodeEnv of ["development", "test"]) {
    for (const flag of [undefined, "1"]) {
      withEnv({ NODE_ENV: nodeEnv, NEXT_PUBLIC_PAWMENTO_MOCK: flag }, () => {
        assert.doesNotThrow(() => assertDevelopmentMockAllowed());
      });
    }
  }
  withEnv({ NODE_ENV: "production", NEXT_PUBLIC_PAWMENTO_MOCK: "1" }, () => {
    assert.throws(
      () => assertDevelopmentMockAllowed(),
      /운영 빌드에서는 개발용 목업을 켤 수 없습니다/,
    );
  });
  withEnv(
    { NODE_ENV: "production", NEXT_PUBLIC_PAWMENTO_MOCK: undefined },
    () => {
      assert.doesNotThrow(() => assertDevelopmentMockAllowed());
    },
  );
});
