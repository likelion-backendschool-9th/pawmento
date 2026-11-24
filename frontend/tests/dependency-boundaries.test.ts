import test from "node:test";
import assert from "node:assert/strict";
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { resolve, relative } from "node:path";
import ts from "typescript";
import type { ApiClient } from "../lib/http/apiClient.ts";
import { createPetApi } from "../features/pet/api/petApi.ts";
import { ApplicationError } from "../lib/application/ApplicationError.ts";

function files(folder: string): string[] {
  return readdirSync(folder, { withFileTypes: true }).flatMap((item) => {
    const path = resolve(folder, item.name);
    return item.isDirectory() ? files(path) : /\.tsx?$/.test(path) ? [path] : [];
  });
}

function moduleSpecifiers(path: string): string[] {
  const file = ts.createSourceFile(path, readFileSync(path, "utf8"), ts.ScriptTarget.Latest, true);
  const found: string[] = [];
  function visit(node: ts.Node) {
    if (
      (ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) &&
      node.moduleSpecifier &&
      ts.isStringLiteral(node.moduleSpecifier)
    ) {
      found.push(node.moduleSpecifier.text);
    }
    // import("…") 는 문장이 아니라 호출이라 따로 모은다.
    if (ts.isCallExpression(node) && node.expression.kind === ts.SyntaxKind.ImportKeyword) {
      const argument = node.arguments[0];
      if (argument && ts.isStringLiteral(argument)) found.push(argument.text);
    }
    ts.forEachChild(node, visit);
  }
  visit(file);
  return found;
}

function featureLayerFiles(layer: string): string[] {
  return readdirSync(resolve("features"), { withFileTypes: true }).flatMap((feature) => {
    if (!feature.isDirectory()) return [];
    const folder = resolve("features", feature.name, layer);
    return existsSync(folder) && statSync(folder).isDirectory() ? files(folder) : [];
  });
}

function featureRootTypeScriptFiles(): string[] {
  return readdirSync(resolve("features"), { withFileTypes: true }).flatMap((feature) => {
    if (!feature.isDirectory()) return [];
    const folder = resolve("features", feature.name);
    return readdirSync(folder, { withFileTypes: true }).flatMap((item) =>
      item.isFile() && /\.ts$/.test(item.name) ? [resolve(folder, item.name)] : [],
    );
  });
}

const browserGlobals = ["window", "document", "localStorage", "sessionStorage", "fetch"];

// (^|/)lib/http(/|$) 또는 /http/
const httpClientImportPatterns = [/(^|\/)lib\/http(\/|$)/, /\/http\//];

function usedBrowserGlobal(path: string): string | undefined {
  const file = ts.createSourceFile(path, readFileSync(path, "utf8"), ts.ScriptTarget.Latest, true);
  let found: string | undefined;
  function visit(node: ts.Node) {
    if (found) return;
    if (ts.isIdentifier(node) && browserGlobals.includes(node.text)) {
      found = node.text;
      return;
    }
    ts.forEachChild(node, visit);
  }
  visit(file);
  return found;
}

function httpClientSpecifier(path: string): string | undefined {
  for (const specifier of moduleSpecifiers(path)) {
    if (httpClientImportPatterns.some((pattern) => pattern.test(specifier))) return specifier;
  }
  return undefined;
}

// 예외 목록의 파일이 없거나, 대상이 아니거나, 더 이상 걸리지 않으면 실패한다.
function assertCurrentExceptions(
  exceptions: Record<string, string>,
  targets: string[],
  violationIn: (path: string) => string | undefined,
): void {
  const names = new Set(targets.map((path) => relative(resolve("."), path)));
  for (const [name, reason] of Object.entries(exceptions)) {
    const path = resolve(name);
    assert.ok(existsSync(path), `${name}은 예외에 있지만 파일이 없다`);
    assert.ok(names.has(name), `${name}은 예외에 있지만 검사 대상이 아니다`);
    assert.ok(violationIn(path), `${name}은 예외에 있지만 더 이상 걸리지 않는다 (${reason})`);
  }
}

test("#52-S9 기능의 api·types·utils와 lib는 React·Next·mocks를 가져오지 않는다", () => {
  const targets = [
    ...featureLayerFiles("api"),
    ...featureLayerFiles("types"),
    ...featureLayerFiles("utils"),
    ...featureRootTypeScriptFiles(),
    ...files(resolve("lib")),
  ];
  const count = targets.length;
  assert.ok(count > 0);
  for (const path of targets) {
    const name = relative(resolve("."), path);
    for (const specifier of moduleSpecifiers(path)) {
      assert.doesNotMatch(specifier, /^(react|react-dom|next)(\/|$)/, `${name} → ${specifier}`);
      assert.doesNotMatch(specifier, /(^|\/)mocks(\/|$)/, `${name} → ${specifier}`);
    }
  }
});

// 파일 경로가 키다. 파일이 없거나 더 이상 HTTP 클라이언트를 가져오지 않으면 실패한다.
const httpClientImportExceptions: Record<string, string> = {
  // ApiClient 타입을 받아 HTTP 세션을 만든다.
  "features/member/session.ts": "ApiClient 타입을 받아 HTTP 세션을 만든다",
};

test("#52-S9 기능의 types·utils와 기능 폴더 바로 아래 파일은 HTTP 클라이언트를 가져오지 않는다", () => {
  const targets = [
    ...featureLayerFiles("types"),
    ...featureLayerFiles("utils"),
    ...featureRootTypeScriptFiles(),
  ];
  assert.ok(targets.length > 0);
  assertCurrentExceptions(httpClientImportExceptions, targets, httpClientSpecifier);
  for (const path of targets) {
    const name = relative(resolve("."), path);
    if (name in httpClientImportExceptions) continue;
    for (const specifier of moduleSpecifiers(path)) {
      for (const pattern of httpClientImportPatterns) {
        assert.doesNotMatch(specifier, pattern, `${name} → ${specifier}`);
      }
    }
  }
});

// 파일 경로가 키다. 파일이 없거나 더 이상 브라우저 전역·fetch를 쓰지 않으면 실패한다.
const browserGlobalExceptions: Record<string, string> = {
  // 네이버 지도 SDK를 window에서 읽음.
  "features/map/utils/naverMaps.ts": "네이버 지도 SDK를 window에서 읽음",
  // document로 내려받기.
  "features/postcard/utils/downloadPostcard.ts": "document로 내려받기",
  // #167 서버 복원 확인 뒤 #200에서 제거할 localStorage 저장.
  "features/map/LocalActiveWalkStore.ts": "#167 서버 복원 확인 뒤 #200에서 제거할 localStorage 저장",
};

test("#52-S9 기능의 types·utils·바로 아래 파일과 lib/date·lib/application은 브라우저 전역과 fetch를 쓰지 않는다", () => {
  const targets = [
    ...featureLayerFiles("types"),
    ...featureLayerFiles("utils"),
    ...featureRootTypeScriptFiles(),
    resolve("lib/date.ts"),
    ...files(resolve("lib/application")),
  ];
  assert.ok(targets.length > 0);
  assertCurrentExceptions(browserGlobalExceptions, targets, usedBrowserGlobal);
  for (const path of targets) {
    const name = relative(resolve("."), path);
    if (name in browserGlobalExceptions) continue;
    const globalName = usedBrowserGlobal(path);
    assert.ok(!globalName, `${name}: ${globalName}`);
  }
});

test("#182 공통 components는 features와 mocks를 가져오지 않는다", () => {
  const targets = files(resolve("components"));
  const count = targets.length;
  assert.ok(count > 0);
  for (const path of targets) {
    const name = relative(resolve("."), path);
    for (const specifier of moduleSpecifiers(path)) {
      assert.doesNotMatch(specifier, /(^|\/)features\//, `${name} → ${specifier}`);
      assert.doesNotMatch(specifier, /(^|\/)mocks(\/|$)/, `${name} → ${specifier}`);
    }
  }
});

test("#182 app·features·components·lib에서는 composition.ts만 mocks를 가져온다", () => {
  const targets = ["app", "features", "components", "lib"].flatMap((root) => files(resolve(root)));
  const count = targets.length;
  assert.ok(count > 0);
  for (const path of targets) {
    const name = relative(resolve("."), path);
    if (name === "app/composition.ts") continue;
    for (const specifier of moduleSpecifiers(path)) {
      assert.doesNotMatch(specifier, /(^|\/)mocks(\/|$)/, `${name} → ${specifier}`);
    }
  }
});

test("#52-S9 화면과 훅은 HTTP 클라이언트를 직접 가져오지 않는다", () => {
  const targets = [
    ...featureLayerFiles("components"),
    ...featureLayerFiles("hooks"),
    ...files(resolve("components")),
  ];
  const count = targets.length;
  assert.ok(count > 0);
  for (const path of targets) {
    const name = relative(resolve("."), path);
    for (const specifier of moduleSpecifiers(path)) {
      assert.doesNotMatch(specifier, /lib\/http/, `${name} → ${specifier}`);
    }
  }
});

test("#19-S9 지도 화면은 구체 outbound 어댑터와 HTTP 클라이언트를 모른다", () => {
  const targets = ["features/map/components", "features/map/hooks"].flatMap((folder) => files(resolve(folder)));
  const count = targets.length;
  assert.ok(count > 0);
  for (const path of targets) {
    const file = ts.createSourceFile(path, readFileSync(path, "utf8"), ts.ScriptTarget.Latest, true);
    for (const node of file.statements) {
      if (ts.isImportDeclaration(node) && ts.isStringLiteral(node.moduleSpecifier)) {
        assert.doesNotMatch(
          node.moduleSpecifier.text,
          /features\/map\/api|(^|\/)api\/|lib\/http/,
          `${relative(resolve("."), path)} → ${node.moduleSpecifier.text}`,
        );
      }
    }
  }
});

test("#182 기능 폴더에 domain·application·adapters 계층 폴더가 다시 생기지 않는다", () => {
  const banned = new Set(["domain", "application", "adapters"]);
  const found: string[] = [];
  function walk(folder: string) {
    for (const item of readdirSync(folder, { withFileTypes: true })) {
      if (!item.isDirectory()) continue;
      const path = resolve(folder, item.name);
      if (banned.has(item.name)) found.push(relative(resolve("."), path));
      walk(path);
    }
  }
  for (const feature of readdirSync(resolve("features"), { withFileTypes: true })) {
    if (feature.isDirectory()) walk(resolve("features", feature.name));
  }
  assert.deepEqual(found, []);
});

test("#52-S6 반려동물 목록 API는 조회 결과를 아이 요약으로 돌려준다", async () => {
  const http: ApiClient = {
    async get<T>(path: string) {
      assert.equal(path, "/pets");
      return [{ petId: 1, name: "콩이" }] as T;
    },
    post: async () => {
      throw Error("unused");
    },
    patch: async () => {
      throw Error("unused");
    },
    postEmpty: async () => {
      throw Error("unused");
    },
  };
  const pets = createPetApi(http);
  assert.deepEqual(await pets.listMine(), [{ id: "1", name: "콩이" }]);
});

test("#52-S4 UI 오류는 HTTP 상태 없이 애플리케이션 코드로 처리할 수 있다", () => {
  const error = new ApplicationError("CONFLICT", "월 한도를 초과했어요.");
  assert.equal(error.code, "CONFLICT");
  assert.equal("status" in error, false);
});
