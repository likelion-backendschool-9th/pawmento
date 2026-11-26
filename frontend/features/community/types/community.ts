// 브라우저 데모 세션의 글 타입. 서버 글은 post.ts를 쓴다. #168 결정 뒤 데모를 정리할 때 함께 지운다.
export type Category = "LIFE" | "CARE" | "FUNERAL" | "MEMORIAL" | "MEMORY";
export type Post = {
  id: string;
  authorId: string;
  author: string;
  category: Category;
  title: string;
  body: string;
  photoUrl: string;
  createdAt: string;
  updatedAt: string;
  likes: string[];
  bookmarks: string[];
  hidden: boolean;
};
export type Comment = {
  id: string;
  postId: string;
  authorId: string;
  author: string;
  body: string;
  createdAt: string;
};
export type Report = {
  id: string;
  postId: string;
  reporterId: string;
  reason: string;
  status: "PENDING" | "RESOLVED" | "DISMISSED";
  createdAt: string;
};
