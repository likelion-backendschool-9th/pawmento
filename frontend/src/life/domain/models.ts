export type Actor = { id: string; name: string; isAdmin: boolean };
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
export type Postcard = {
  id: string;
  petId: string;
  ownerId: string;
  title: string;
  text: string;
  imageUrl: string;
  sourceRecordIds: string[];
  demoArtwork?: {
    scene: "PARK" | "BEACH" | "NAP";
    prompt: string;
    referencePhotoUrl: string;
  };
  status: "PROCESSING" | "COMPLETED" | "FAILED";
  error: string;
  saved: boolean;
  createdAt: string;
};
export type FamilyRole = "EDITOR" | "VIEWER";
export type FamilyMember = {
  id: string;
  name: string;
  role: FamilyRole;
  actorId?: string;
};
export type FamilyInvite = {
  id: string;
  role: FamilyRole;
  expiresAt: string;
  used: boolean;
};
export type Family = {
  petId: string;
  ownerId: string;
  members: FamilyMember[];
  invites: FamilyInvite[];
};
export type Notice = {
  id: string;
  title: string;
  body: string;
  published: boolean;
  createdAt: string;
};
export type PoolQuestion = { id: string; text: string; enabled: boolean };
export type DemoMember = {
  id: string;
  name: string;
  status: "ACTIVE" | "SUSPENDED";
};
export type LifeState = {
  posts: Post[];
  comments: Comment[];
  reports: Report[];
  postcards: Postcard[];
  families: Family[];
  notices: Notice[];
  questions: PoolQuestion[];
  members: DemoMember[];
};
export const categories: Record<Category, string> = {
  LIFE: "반려 생활",
  CARE: "건강·돌봄",
  FUNERAL: "장례 정보",
  MEMORIAL: "추모 방식",
  MEMORY: "기억 나누기",
};
export function requireText(value: string, label: string, max = 3000) {
  if (!value.trim() || value.length > max)
    throw Error(`${label}은(는) 1~${max.toLocaleString()}자로 입력해 주세요.`);
}
export function requireOwner(ownerId: string, actor: Actor) {
  if (ownerId !== actor.id) throw Error("작성자만 변경할 수 있어요.");
}
export function requireAdmin(actor: Actor) {
  if (!actor.isAdmin) throw Error("관리자 데모 권한이 필요해요.");
}
