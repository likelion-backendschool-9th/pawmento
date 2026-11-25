// 서버 글 API(/api/v1/posts)의 화면 타입. 데모 화면의 Post는 community.ts에 있다.

export type PostCategory = {
  code: string;
  name: string;
};

export type PostSummary = {
  id: string;
  category: string;
  categoryName: string;
  title: string;
  preview: string;
  authorId: string;
  createdAt: string;
};

export type PostDetail = {
  id: string;
  category: string;
  categoryName: string;
  title: string;
  body: string;
  authorId: string;
  createdAt: string;
  updatedAt: string;
};

export type PostPage = {
  posts: PostSummary[];
  nextCursor: string | null;
};

export type PostInput = {
  category: string;
  title: string;
  body: string;
};
