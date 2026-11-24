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
