"use client";
import { PostList } from "@/features/community/components/PostList";
import type { useLife } from "@/features/shell/hooks/useLife";
export function ActivityView({ life }: { life: ReturnType<typeof useLife> }) {
  return <PostList life={life} activity />;
}
