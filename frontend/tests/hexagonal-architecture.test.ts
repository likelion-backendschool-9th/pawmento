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

function coreFolders(): string[] {
  const folders: string[] = [];
  for (const feature of readdirSync(resolve("features"), { withFileTypes: true })) {
    if (!feature.isDirectory()) continue;
    for (const layer of ["domain", "application"]) {
      const folder = `features/${feature.name}/${layer}`;
      const path = resolve(folder);
      if (existsSync(path) && statSync(path).isDirectory()) folders.push(folder);
    }
  }
  folders.push("lib/application");
  return folders;
}

test("#52-S9 코어는 프레임워크·브라우저·어댑터와 HTTP에 의존하지 않는다", () => {
  const folders = coreFolders();
  for (const folder of folders) {
    for (const path of files(resolve(folder))) {
      const name = relative(resolve("."), path);
      const file = ts.createSourceFile(path, readFileSync(path, "utf8"), ts.ScriptTarget.Latest, true);
      function visit(node: ts.Node) {
        if (ts.isImportDeclaration(node) && ts.isStringLiteral(node.moduleSpecifier)) {
          assert.doesNotMatch(node.moduleSpecifier.text, /react|next|axios|adapters|\/http\//, name);
        }
        if (ts.isIdentifier(node)) {
          assert.ok(!["window", "document", "localStorage", "fetch"].includes(node.text), `${name}: ${node.text}`);
        }
        ts.forEachChild(node, visit);
      }
      visit(file);
    }
  }
});

test("#52-S9 마이페이지 화면은 구체 outbound 어댑터와 HTTP 예외를 모른다", () => {
  const path = resolve("features/workspace/adapters/inbound/AccountScreen.tsx");
  const file = ts.createSourceFile(path, readFileSync(path, "utf8"), ts.ScriptTarget.Latest, true);
  for (const node of file.statements) {
    if (ts.isImportDeclaration(node) && ts.isStringLiteral(node.moduleSpecifier)) {
      assert.doesNotMatch(node.moduleSpecifier.text, /adapters\/outbound|lib\/http/);
    }
  }
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

test("#182 회원·반려동물 API는 react·next·mocks를 가져오지 않는다", () => {
  for (const folder of ["features/member/api", "features/pet/api", "features/map/api"]) {
    for (const path of files(resolve(folder))) {
      const name = relative(resolve("."), path);
      for (const specifier of moduleSpecifiers(path)) {
        assert.doesNotMatch(specifier, /react|next|mocks/, `${name} → ${specifier}`);
      }
    }
  }
});

test("#182 app·features·components·lib에서는 composition.ts만 mocks를 가져온다", () => {
  for (const root of ["app", "features", "components", "lib"]) {
    for (const path of files(resolve(root))) {
      const name = relative(resolve("."), path);
      if (name === "app/composition.ts") continue;
      for (const specifier of moduleSpecifiers(path)) {
        assert.doesNotMatch(
          specifier,
          /(^|\/)mocks(\/|$)/,
          `${name} → ${specifier}`,
        );
      }
    }
  }
});

function moduleSpecifiers(path: string): string[] {
  const file = ts.createSourceFile(path, readFileSync(path, "utf8"), ts.ScriptTarget.Latest, true);
  const found: string[] = [];
  for (const node of file.statements) {
    if (
      (ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) &&
      node.moduleSpecifier &&
      ts.isStringLiteral(node.moduleSpecifier)
    ) {
      found.push(node.moduleSpecifier.text);
    }
  }
  return found;
}

test("#19-S9 지도 화면은 구체 outbound 어댑터와 HTTP 클라이언트를 모른다", () => {
  for (const folder of ["features/map/components", "features/map/hooks"]) {
    for (const path of files(resolve(folder))) {
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
  }
});
