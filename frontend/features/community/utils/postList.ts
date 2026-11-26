// 서버 글 목록 화면의 계산. 카테고리 고르기와 "더 보기" 이어 붙이기.
import type { PostCategory, PostSummary } from "../types/post.ts";

// 주소의 카테고리가 서버 카테고리에 없거나 비어 있으면 첫 카테고리를 쓴다(#104-S12).
export function pickCategory(
  categories: PostCategory[],
  requested: string | null | undefined,
): string | undefined {
  return (
    categories.find((category) => category.code === requested)?.code ??
    categories[0]?.code
  );
}

// 다음 페이지를 뒤에 붙인다. 그사이 새 글이 올라와 경계의 글이 다시 오면 한 번만 둔다(#104-S13).
export function appendPosts(
  current: PostSummary[],
  next: PostSummary[],
): PostSummary[] {
  const seen = new Set(current.map((post) => post.id));
  return [...current, ...next.filter((post) => !seen.has(post.id))];
}
