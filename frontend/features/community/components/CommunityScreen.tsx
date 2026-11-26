"use client";
import {
  ActivityView,
  ServerActivityNotice,
} from "@/features/community/components/ActivityView";
import {
  PostDetail,
  ServerPostDetail,
} from "@/features/community/components/PostDetail";
import {
  PostEditor,
  ServerPostEditor,
} from "@/features/community/components/PostEditor";
import { PostList, ServerPostList } from "@/features/community/components/PostList";
import { useCommunitySource } from "@/features/community/hooks/usePosts";
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
  const source = useCommunitySource();
  // 가입·로그인한 회원은 서버 글을 쓴다. 서버에 없는 기능은 그리지 않는다(#104-S10).
  if (source.kind === "server") {
    if (activity) return <ServerActivityNotice />;
    if (segments[0] === "new") return <ServerPostEditor posts={source.posts} />;
    if (segments[1] === "edit")
      return (
        <ServerPostEditor
          key={segments[0]}
          posts={source.posts}
          postId={segments[0]}
        />
      );
    if (segments[0])
      return <ServerPostDetail key={segments[0]} posts={source.posts} postId={segments[0]} />;
    return <ServerPostList posts={source.posts} />;
  }
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
