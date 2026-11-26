// 브라우저 데모 세션의 카테고리. 서버 세션은 GET /api/v1/posts/categories를 쓴다(#104-S3).
// #168 결정 뒤 데모를 정리할 때 함께 지운다.
import type { Category } from "../types/community.ts";
export const categories: Record<Category, string> = {
  LIFE: "반려 생활",
  CARE: "건강·돌봄",
  FUNERAL: "장례 정보",
  MEMORIAL: "추모 방식",
  MEMORY: "기억 나누기",
};
