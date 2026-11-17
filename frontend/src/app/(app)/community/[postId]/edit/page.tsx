"use client";
import { useParams } from "next/navigation";
import { CommunityScreen } from "@/life/adapters/inbound/CommunityScreen";
export default function Page() {
  const { postId } = useParams<{ postId: string }>();
  return <CommunityScreen segments={[postId, "edit"]} />;
}
