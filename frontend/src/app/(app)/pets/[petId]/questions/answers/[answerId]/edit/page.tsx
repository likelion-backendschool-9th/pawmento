"use client";
import {
  usePet,
  PetWriteAccess,
} from "@/workspace/adapters/inbound/PetContext";
import { useJournalItem } from "@/workspace/adapters/inbound/JournalContext";
import { JournalForm } from "@/workspace/adapters/inbound/JournalScreens";
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
