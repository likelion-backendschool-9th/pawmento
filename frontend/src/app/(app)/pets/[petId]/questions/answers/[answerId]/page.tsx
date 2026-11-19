"use client";
import { usePet } from "@/workspace/adapters/inbound/PetContext";
import { useJournalItem } from "@/workspace/adapters/inbound/JournalContext";
import { JournalDetail } from "@/workspace/adapters/inbound/JournalScreens";
export default function Page() {
  const pet = usePet();
  const record = useJournalItem();
  return <JournalDetail key={record.id} pet={pet} record={record} />;
}
