"use client";
import {
  usePet,
  PetWriteAccess,
} from "@/workspace/adapters/inbound/PetContext";
import { useRecord } from "@/workspace/adapters/inbound/RecordContext";
import { RecordForm } from "@/workspace/adapters/inbound/RecordScreens";
export default function Page() {
  const pet = usePet();
  const record = useRecord();
  return (
    <PetWriteAccess>
      <RecordForm
        key={record.id}
        pet={pet}
        record={record}
        diary={record.type === "DIARY"}
      />
    </PetWriteAccess>
  );
}
