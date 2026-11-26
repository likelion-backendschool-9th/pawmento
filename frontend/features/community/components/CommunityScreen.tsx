"use client";
import { ActivityView } from "@/features/community/components/ActivityView";
import { PostDetail } from "@/features/community/components/PostDetail";
import { PostEditor } from "@/features/community/components/PostEditor";
import { PostList } from "@/features/community/components/PostList";
import { useLife } from "@/features/shell/hooks/useLife";
import { LifeEmpty, LifeStatus } from "@/features/shell/components/LifeUi";
export function CommunityScreen({
  segments = [],
  activity = false,
}: {
  segments?: string[];
  activity?: boolean;
}) {
  const life = useLife();
  if (!life.state) return <LifeStatus error={life.error} loading />;
  if (segments[0] === "new") return <PostEditor />;
  if (segments[0]) {
    const post = life.state.posts.find((item) => item.id === segments[0]);
    return !post || post.hidden ? (
      <LifeEmpty
        title="게시글을 찾을 수 없어요"
        body="삭제되었거나 운영 처리된 글이에요."
        href="/community"
        action="목록으로"
      />
    ) : segments[1] === "edit" ? (
      <PostEditor key={post.id} post={post} />
    ) : (
      <PostDetail post={post} />
    );
  }
  return activity ? (
    <ActivityView life={life} />
  ) : (
    <PostList life={life} />
  );
}
