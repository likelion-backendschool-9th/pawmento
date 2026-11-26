"use client";
import { PostList } from "@/features/community/components/PostList";
import type { useLife } from "@/features/shell/hooks/useLife";
import { LifeEmpty } from "@/features/shell/components/LifeUi";
export function ActivityView({ life }: { life: ReturnType<typeof useLife> }) {
  return <PostList life={life} activity />;
}

// 가입·로그인한 회원의 내 활동. 내가 쓴 글·댓글·북마크를 모아 줄 API가 없어
// 데모 목록 대신 준비 중 안내와 커뮤니티 링크만 보인다(#104-S10).
export function ServerActivityNotice() {
  return (
    <LifeEmpty
      title="내 커뮤니티 활동은 준비 중이에요"
      body="내가 쓴 글과 댓글, 북마크를 모아 보는 기능을 준비하고 있어요."
      href="/community"
      action="커뮤니티로"
    />
  );
}
