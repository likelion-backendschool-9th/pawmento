import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import test from "node:test";
import { createPostApi } from "../features/community/api/postApi.ts";
import { selectCommunitySource } from "../features/community/source.ts";
import { appendPosts, pickCategory } from "../features/community/utils/postList.ts";
import type { Account } from "../features/member/types/account.ts";
import { ApiError, createApiClient } from "../lib/http/apiClient.ts";

type Call = { url: string; method?: string; body?: unknown };

function server(respond: (call: Call) => Response) {
  const calls: Call[] = [];
  const http = createApiClient({
    async fetch(url, options) {
      const call = {
        url: String(url),
        method: options?.method,
        body: options?.body ? JSON.parse(String(options.body)) : undefined,
      };
      calls.push(call);
      return respond(call);
    },
  });
  return { api: createPostApi(http), calls };
}

function summaryDto(postId: number) {
  return {
    postId,
    category: "LIFE",
    categoryName: "반려 생활",
    title: `글 ${postId}`,
    preview: `미리보기 ${postId}`,
    authorId: 3,
    createdAt: "2026-11-25T03:00:00Z",
  };
}

const detailDto = {
  postId: 7,
  category: "MEMORIAL",
  categoryName: "추모 방식",
  title: "고친 제목",
  body: "고친 본문",
  authorId: 3,
  createdAt: "2026-11-25T03:00:00Z",
  updatedAt: "2026-11-25T04:00:00Z",
};

const detail = {
  id: "7",
  category: "MEMORIAL",
  categoryName: "추모 방식",
  title: "고친 제목",
  body: "고친 본문",
  authorId: "3",
  createdAt: "2026-11-25T03:00:00Z",
  updatedAt: "2026-11-25T04:00:00Z",
};

test("#104-S1 목록은 카테고리와 20개씩 요청하고, 서버의 숫자 id와 커서를 문자열로 바꾼다", async () => {
  const ids = Array.from({ length: 20 }, (_, index) => 50 - index);
  const { api, calls } = server(() =>
    Response.json({ posts: ids.map(summaryDto), nextCursor: 31 }),
  );

  const page = await api.list({ category: "LIFE" });

  assert.equal(calls[0].url, "/api/v1/posts?category=LIFE&size=20");
  assert.equal(calls[0].method, "GET");
  assert.equal(page.posts.length, 20);
  assert.deepEqual(page.posts[0], {
    id: "50",
    category: "LIFE",
    categoryName: "반려 생활",
    title: "글 50",
    preview: "미리보기 50",
    authorId: "3",
    createdAt: "2026-11-25T03:00:00Z",
  });
  assert.equal(page.nextCursor, "31");
});

test("#104-S1 다음 페이지는 커서를 붙여 요청하고, 마지막 페이지의 커서는 null이다", async () => {
  const { api, calls } = server(() =>
    Response.json({ posts: [summaryDto(5)], nextCursor: null }),
  );

  const page = await api.list({ category: "LIFE", cursor: "31" });

  assert.equal(calls[0].url, "/api/v1/posts?category=LIFE&size=20&cursor=31");
  assert.equal(page.nextCursor, null);
});

test("#104-S1 안전한 정수를 넘는 id나 커서는 반올림된 값으로 바꾸지 않고 오류를 낸다", async () => {
  for (const body of [
    '{"posts":[{"postId":9007199254740993,"category":"LIFE","categoryName":"반려 생활","title":"t","preview":"p","authorId":3,"createdAt":"2026-11-25T03:00:00Z"}],"nextCursor":null}',
    '{"posts":[],"nextCursor":9007199254740993}',
  ]) {
    const { api } = server(() => new Response(body, { headers: { "Content-Type": "application/json" } }));
    await assert.rejects(api.list({ category: "LIFE" }), /읽을 수 없어요/);
  }
});

test("#104-S5 작성은 카테고리·제목·본문을 보내고 새 글 id를 문자열로 돌려준다", async () => {
  const { api, calls } = server(() => Response.json({ postId: 7 }, { status: 201 }));

  const id = await api.create({ category: "CARE", title: "노견 관절", body: "3개월 먹였어요" });

  assert.equal(id, "7");
  assert.deepEqual(calls, [
    {
      url: "/api/v1/posts",
      method: "POST",
      body: { category: "CARE", title: "노견 관절", body: "3개월 먹였어요" },
    },
  ]);
});

test("#104-S5 상세는 본문 전체와 수정 시각을 가진 글로 바꾼다", async () => {
  const { api, calls } = server(() => Response.json(detailDto));

  assert.deepEqual(await api.get("7"), detail);
  assert.equal(calls[0].url, "/api/v1/posts/7");
  assert.equal(calls[0].method, "GET");
});

test("#104-S5 수정은 PUT으로 세 값을 보내고 서버가 돌려준 수정된 글을 돌려준다", async () => {
  const { api, calls } = server(() => Response.json(detailDto));

  const updated = await api.update("7", {
    category: "MEMORIAL",
    title: "고친 제목",
    body: "고친 본문",
  });

  assert.deepEqual(updated, detail);
  assert.deepEqual(calls, [
    {
      url: "/api/v1/posts/7",
      method: "PUT",
      body: { category: "MEMORIAL", title: "고친 제목", body: "고친 본문" },
    },
  ]);
});

test("#104-S5 삭제는 DELETE를 보내고 204를 받으면 오류 없이 끝난다", async () => {
  const { api, calls } = server(() => new Response(null, { status: 204 }));

  await api.remove("7");

  assert.deepEqual(calls, [{ url: "/api/v1/posts/7", method: "DELETE", body: undefined }]);
});

test("#104-S5 409를 받으면 서버의 코드와 문구를 가진 ApiError가 난다", async () => {
  const message = "다른 요청에서 글을 먼저 고치거나 지웠습니다. 다시 불러와 주세요.";
  const { api } = server(() =>
    Response.json({ code: "CONFLICT", message }, { status: 409 }),
  );

  await assert.rejects(
    api.update("7", { category: "CARE", title: "제목", body: "본문" }),
    (error) =>
      error instanceof ApiError &&
      error.status === 409 &&
      error.code === "CONFLICT" &&
      error.message === message,
  );
});

function demoLife(seen: string[]) {
  return {
    async load() {
      seen.push("demo.load");
      return { posts: [{ id: "demo-1", title: "데모 글" }] };
    },
  };
}

const httpAccount: Account = { id: "3", email: "a@example.com", nickname: "다윗" };
const demoAccount: Account = { ...httpAccount, id: "demo-member", demo: true };

test("#104-S9 데모 계정이면 브라우저 데모 글을 쓰고 글 API를 부르지 않는다", async () => {
  const seen: string[] = [];
  const { api, calls } = server(() => assert.fail("데모 계정은 글 API를 부르지 않는다"));

  const source = selectCommunitySource({ account: demoAccount, server: api, demo: demoLife(seen) });

  assert.equal(source.kind, "demo");
  if (source.kind !== "demo") return;
  const state = await source.demo.load();
  assert.deepEqual(state.posts.map((post) => post.id), ["demo-1"]);
  assert.deepEqual(seen, ["demo.load"]);
  assert.deepEqual(calls, []);
});

test("#104-S9 가입·로그인한 계정이면 글 API를 쓰고 데모 저장소를 부르지 않으며, 서버 글이 실패해도 데모 글로 대신하지 않는다", async () => {
  const seen: string[] = [];
  const { api, calls } = server(() =>
    Response.json({ posts: [summaryDto(9)], nextCursor: null }),
  );

  const source = selectCommunitySource({ account: httpAccount, server: api, demo: demoLife(seen) });

  assert.equal(source.kind, "server");
  if (source.kind !== "server") return;
  const page = await source.posts.list({ category: "LIFE" });
  assert.deepEqual(page.posts.map((post) => post.id), ["9"]);
  assert.equal(calls.length, 1);
  assert.deepEqual(seen, []);

  // 서버 글이 실패해도 데모 글로 대신하지 않고 오류를 올린다
  const failing = server(() => new Response(null, { status: 500 }));
  const failingSource = selectCommunitySource({ account: httpAccount, server: failing.api, demo: demoLife(seen) });
  assert.equal(failingSource.kind, "server");
  if (failingSource.kind !== "server") return;
  await assert.rejects(failingSource.posts.list({ category: "LIFE" }), ApiError);
  assert.deepEqual(seen, []);
});

test("#104-S9 의존성 조립은 같은 HTTP 클라이언트로 글 API를 만든다", () => {
  const composition = readFileSync(resolve("app/composition.ts"), "utf8");
  assert.match(composition, /posts:\s*createPostApi\(http\)/);
});

const serverCategories = [
  { code: "LIFE", name: "반려 생활" },
  { code: "CARE", name: "건강·돌봄" },
  { code: "FUNERAL", name: "장례 정보" },
  { code: "MEMORIAL", name: "추모 방식" },
  { code: "MEMORY", name: "기억 나누기" },
];

test("#104-S12 주소의 카테고리가 서버에 있으면 그대로, 없거나 비면 첫 카테고리를 고른다", () => {
  assert.equal(pickCategory(serverCategories, "CARE"), "CARE");
  assert.equal(pickCategory(serverCategories, "NOPE"), "LIFE");
  assert.equal(pickCategory(serverCategories, ""), "LIFE");
  assert.equal(pickCategory(serverCategories, null), "LIFE");
  assert.equal(pickCategory([], "CARE"), undefined);
});

function summaries(from: number, to: number) {
  const posts = [];
  for (let postId = from; postId >= to; postId -= 1) {
    posts.push({
      id: String(postId),
      category: "LIFE",
      categoryName: "반려 생활",
      title: `글 ${postId}`,
      preview: `미리보기 ${postId}`,
      authorId: "3",
      createdAt: "2026-11-25T03:00:00Z",
    });
  }
  return posts;
}

test("#104-S13 더 보기로 받은 글을 뒤에 이어 붙이고, 겹친 글은 한 번만 둔다", () => {
  const merged = appendPosts(summaries(25, 6), summaries(6, 1));

  assert.deepEqual(
    merged.map((post) => post.id),
    summaries(25, 1).map((post) => post.id),
  );
});
