"use client";
import { useParams } from "next/navigation";
import { FamilyScreen } from "@/life/adapters/inbound/FamilyScreen";
export default function Page() {
  const { inviteId } = useParams<{ inviteId: string }>();
  return <FamilyScreen inviteId={inviteId} />;
}
