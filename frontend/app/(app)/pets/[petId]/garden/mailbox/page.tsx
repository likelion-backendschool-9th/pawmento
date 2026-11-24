"use client";
import { MailboxScreen } from "@/features/postcard/components/MailboxScreen";
import { usePet } from "@/features/pet/hooks/PetContext";
export default function Page() {
  const pet = usePet();
  return <MailboxScreen key={pet.id + pet.mode} pet={pet} />;
}
