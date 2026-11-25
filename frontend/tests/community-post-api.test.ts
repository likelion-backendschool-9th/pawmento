import assert from "node:assert/strict";
import test from "node:test";
import { createPostApi } from "../features/community/api/postApi.ts";
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
