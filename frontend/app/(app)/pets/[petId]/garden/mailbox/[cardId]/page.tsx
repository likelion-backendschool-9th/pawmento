"use client";
import { useParams } from "next/navigation";
import { usePet } from "@/features/pet/hooks/PetContext";
import { MailboxScreen } from "@/features/postcard/components/MailboxScreen";
export default function Page() {
  const pet = usePet();
  const { cardId } = useParams<{ cardId: string }>();
  return <MailboxScreen key={cardId} pet={pet} segments={[cardId]} />;
}
