"use client";
import { MailboxScreen } from "@/life/adapters/inbound/MailboxScreen";
import { usePet } from "@/workspace/adapters/inbound/PetContext";
export default function Page() {
  const pet = usePet();
  return <MailboxScreen key={pet.id + pet.mode} pet={pet} segments={["new"]} />;
}
