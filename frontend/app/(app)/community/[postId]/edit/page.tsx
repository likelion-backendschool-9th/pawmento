"use client";
import { useParams } from "next/navigation";
import { CommunityScreen } from "@/features/community/components/CommunityScreen";
export default function Page() {
  const { postId } = useParams<{ postId: string }>();
  return <CommunityScreen segments={[postId, "edit"]} />;
}
