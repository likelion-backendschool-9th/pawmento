"use client";
import { MailboxScreen } from "@/features/life/adapters/inbound/MailboxScreen";
import { usePet } from "@/features/workspace/adapters/inbound/PetContext";
export default function Page() {
  const pet = usePet();
  return <MailboxScreen key={pet.id + pet.mode} pet={pet} />;
}
