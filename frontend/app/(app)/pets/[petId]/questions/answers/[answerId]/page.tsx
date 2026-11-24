"use client";
import { usePet } from "@/features/pet/hooks/PetContext";
import { useJournalItem } from "@/features/diary/hooks/JournalContext";
import { JournalDetail } from "@/features/diary/components/JournalScreens";
export default function Page() {
  const pet = usePet();
  const record = useJournalItem();
  return <JournalDetail key={record.id} pet={pet} record={record} />;
}
