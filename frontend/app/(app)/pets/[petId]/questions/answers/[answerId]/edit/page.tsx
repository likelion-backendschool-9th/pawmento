"use client";
import {
  usePet,
  PetWriteAccess,
} from "@/features/pet/hooks/PetContext";
import { useJournalItem } from "@/features/diary/hooks/JournalContext";
import { JournalForm } from "@/features/diary/components/JournalScreens";
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
