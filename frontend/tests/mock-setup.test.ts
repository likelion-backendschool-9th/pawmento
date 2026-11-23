import test from "node:test";
import assert from "node:assert/strict";
import { isDevelopmentMockEnabled } from "../mocks/setup.ts";

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

test("#182 개발용 목업은 명시적으로 켠 경우에만 켜지고 운영 빌드에서는 거부된다", () => {
  withEnv(
    { NODE_ENV: "development", NEXT_PUBLIC_PAWMENTO_MOCK: undefined },
    () => {
      assert.equal(isDevelopmentMockEnabled(), false);
    },
  );
  withEnv({ NODE_ENV: "test", NEXT_PUBLIC_PAWMENTO_MOCK: "1" }, () => {
    assert.equal(isDevelopmentMockEnabled(), true);
  });
  withEnv(
    { NODE_ENV: "development", NEXT_PUBLIC_PAWMENTO_MOCK: "true" },
    () => {
      assert.equal(isDevelopmentMockEnabled(), false);
    },
  );
  withEnv(
    { NODE_ENV: "production", NEXT_PUBLIC_PAWMENTO_MOCK: undefined },
    () => {
      assert.equal(isDevelopmentMockEnabled(), false);
      assert.equal(isDevelopmentMockEnabled(false), false);
    },
  );
  withEnv({ NODE_ENV: "production", NEXT_PUBLIC_PAWMENTO_MOCK: "1" }, () => {
    assert.throws(
      () => isDevelopmentMockEnabled(),
      /운영 빌드에서는 개발용 목업을 켤 수 없습니다/,
    );
  });
  withEnv(
    { NODE_ENV: "production", NEXT_PUBLIC_PAWMENTO_MOCK: undefined },
    () => {
      assert.throws(
        () => isDevelopmentMockEnabled(true),
        /운영 빌드에서는 개발용 목업을 켤 수 없습니다/,
      );
    },
  );
});
