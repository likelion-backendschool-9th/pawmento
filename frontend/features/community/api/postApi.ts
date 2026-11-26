import type { ApiClient } from "../../../lib/http/apiClient.ts";
import type {
  PostCategory,
  PostDetail,
  PostInput,
  PostPage,
  PostSummary,
} from "../types/post.ts";

const PAGE_SIZE = 20;

type PostSummaryDto = {
  postId: number;
  category: string;
  categoryName: string;
  title: string;
  preview: string;
  authorId: number;
  createdAt: string;
};

type PostDto = {
  postId: number;
  category: string;
  categoryName: string;
  title: string;
  body: string;
  authorId: number;
  createdAt: string;
  updatedAt: string;
};

type PostPageDto = {
  posts: PostSummaryDto[];
  nextCursor: number | null;
};

export function createPostApi(http: ApiClient) {
  return {
    async categories(): Promise<PostCategory[]> {
      const categories = await http.get<PostCategory[]>("/posts/categories");
      return categories.map(({ code, name }) => ({ code, name }));
    },
    async list(input: { category: string; cursor?: string }): Promise<PostPage> {
      const query = new URLSearchParams({
        category: input.category,
        size: String(PAGE_SIZE),
      });
      if (input.cursor) query.set("cursor", input.cursor);
      const page = await http.get<PostPageDto>(`/posts?${query}`);
      return {
        posts: page.posts.map(toSummary),
        nextCursor: page.nextCursor === null ? null : toId(page.nextCursor, "커서"),
      };
    },
    async get(postId: string): Promise<PostDetail> {
      return toDetail(await http.get<PostDto>(`/posts/${encodeURIComponent(postId)}`));
    },
    async create(input: PostInput): Promise<string> {
      const created = await http.post<{ postId: number }>("/posts", toRequest(input));
      return toId(created.postId, "글 id");
    },
    async update(postId: string, input: PostInput): Promise<PostDetail> {
      return toDetail(
        await http.put<PostDto>(`/posts/${encodeURIComponent(postId)}`, toRequest(input)),
      );
    },
    async remove(postId: string): Promise<void> {
      await http.delete(`/posts/${encodeURIComponent(postId)}`);
    },
  };
}

export type PostApi = ReturnType<typeof createPostApi>;

// 서버의 Long id를 JSON 숫자로 받는다. JSON.parse는 2^53을 넘는 정수를 반올림하므로,
// 안전한 정수가 아니면 다른 글을 가리키는 id로 다음 요청을 만들지 않고 멈춘다(#257 리뷰).
function toId(value: number, label: string): string {
  if (!Number.isSafeInteger(value) || value < 1) {
    throw Error(`서버가 보낸 ${label}를 읽을 수 없어요.`);
  }
  return String(value);
}

function toRequest(input: PostInput) {
  return { category: input.category, title: input.title, body: input.body };
}

function toSummary(dto: PostSummaryDto): PostSummary {
  return {
    id: toId(dto.postId, "글 id"),
    category: dto.category,
    categoryName: dto.categoryName,
    title: dto.title,
    preview: dto.preview,
    authorId: toId(dto.authorId, "작성자 id"),
    createdAt: dto.createdAt,
  };
}

function toDetail(dto: PostDto): PostDetail {
  return {
    id: toId(dto.postId, "글 id"),
    category: dto.category,
    categoryName: dto.categoryName,
    title: dto.title,
    body: dto.body,
    authorId: toId(dto.authorId, "작성자 id"),
    createdAt: dto.createdAt,
    updatedAt: dto.updatedAt,
  };
}
