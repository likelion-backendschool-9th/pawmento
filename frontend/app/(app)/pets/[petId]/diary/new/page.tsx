"use client";
import { useSearchParams } from "next/navigation";
import { today } from "@/lib/date";
import {
  usePet,
  PetWriteAccess,
} from "@/features/pet/hooks/PetContext";
import { JournalForm } from "@/features/diary/components/JournalScreens";
export default function Page() {
  const pet = usePet();
  const search = useSearchParams();
  return (
    <PetWriteAccess>
      <JournalForm
        key={pet.id + (search.get("date") ?? today())}
        pet={pet}
        questionAnswer={false}
      />
    </PetWriteAccess>
  );
}
