"use client";
import {
  usePet,
  PetWriteAccess,
} from "@/features/workspace/adapters/inbound/PetContext";
import { useJournalItem } from "@/features/workspace/adapters/inbound/JournalContext";
import { JournalForm } from "@/features/workspace/adapters/inbound/JournalScreens";
export default function Page() {
  const pet = usePet();
  const record = useJournalItem();
  return (
    <PetWriteAccess>
      <JournalForm
        key={record.id}
        pet={pet}
        record={record}
        questionAnswer={record.type === "QUESTION_ANSWER"}
      />
    </PetWriteAccess>
  );
}
