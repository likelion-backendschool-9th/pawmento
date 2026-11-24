"use client";
import { useParams } from "next/navigation";
import { usePet } from "@/features/pet/hooks/PetContext";
import { MailboxScreen } from "@/features/life/adapters/inbound/MailboxScreen";
export default function Page() {
  const pet = usePet();
  const { cardId } = useParams<{ cardId: string }>();
  return <MailboxScreen key={cardId} pet={pet} segments={[cardId]} />;
}
