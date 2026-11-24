"use client";
import { useParams } from "next/navigation";
import { FamilyScreen } from "@/features/family/components/FamilyScreen";
export default function Page() {
  const { inviteId } = useParams<{ inviteId: string }>();
  return <FamilyScreen inviteId={inviteId} />;
}
