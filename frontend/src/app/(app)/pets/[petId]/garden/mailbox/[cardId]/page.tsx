"use client";
import { useParams } from "next/navigation";
import { usePet } from "@/workspace/adapters/inbound/PetContext";
import { MailboxScreen } from "@/life/adapters/inbound/MailboxScreen";
export default function Page() {
  const pet = usePet();
  const { cardId } = useParams<{ cardId: string }>();
  return <MailboxScreen key={cardId} pet={pet} segments={[cardId]} />;
}
