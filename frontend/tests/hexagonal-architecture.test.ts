import test from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { resolve, relative } from "node:path";
import ts from "typescript";
import { createPetUseCases } from "../features/pets/application/useCases.ts";
import { ApplicationError } from "../lib/application/ApplicationError.ts";

function files(folder: string): string[] {
  return readdirSync(folder, { withFileTypes: true }).flatMap((item) => {
    const path = resolve(folder, item.name);
    return item.isDirectory() ? files(path) : /\.tsx?$/.test(path) ? [path] : [];
  });
}

test("#52-S9 코어는 프레임워크·브라우저·어댑터와 HTTP에 의존하지 않는다", () => {
  const folders = [
    "features/auth/domain",
    "features/auth/application",
    "features/workspace/domain",
    "features/workspace/application",
    "features/life/domain",
    "features/life/application",
    "features/pets/domain",
    "features/pets/application",
    "features/diary/domain",
    "features/diary/application",
    "features/question/domain",
    "features/question/application",
    "features/map/domain",
    "features/map/application",
    "lib/application",
  ];
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

test("#52-S6 pet 조회 유스케이스는 HTTP 없이 포트만으로 동작한다", async () => {
  const pets = createPetUseCases({ listMine: async () => [{ id: "1", name: "콩이" }] });
  assert.deepEqual(await pets.listMine(), [{ id: "1", name: "콩이" }]);
});

test("#52-S4 UI 오류는 HTTP 상태 없이 애플리케이션 코드로 처리할 수 있다", () => {
  const error = new ApplicationError("CONFLICT", "월 한도를 초과했어요.");
  assert.equal(error.code, "CONFLICT");
  assert.equal("status" in error, false);
});

test("#19-S9 지도 화면은 구체 outbound 어댑터와 HTTP 클라이언트를 모른다", () => {
  const folder = resolve("features/map/adapters/inbound");
  for (const path of files(folder)) {
    const file = ts.createSourceFile(path, readFileSync(path, "utf8"), ts.ScriptTarget.Latest, true);
    for (const node of file.statements) {
      if (ts.isImportDeclaration(node) && ts.isStringLiteral(node.moduleSpecifier)) {
        assert.doesNotMatch(node.moduleSpecifier.text, /adapters\/outbound|lib\/http/, relative(folder, path));
      }
    }
  }
});
