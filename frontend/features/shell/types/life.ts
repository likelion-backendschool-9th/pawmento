import type { Notice, DemoMember } from "../../admin/types/admin.ts";
import type { Post, Comment, Report } from "../../community/types/community.ts";
import type { Family } from "../../family/types/family.ts";
import type { Postcard } from "../../postcard/types/Postcard.ts";
import type { PoolQuestion } from "../../question/types/PoolQuestion.ts";
export type Actor = { id: string; name: string; isAdmin: boolean };
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
