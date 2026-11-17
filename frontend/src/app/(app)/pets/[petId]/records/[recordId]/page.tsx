"use client";
import { usePet } from "@/workspace/adapters/inbound/PetContext";
import { useRecord } from "@/workspace/adapters/inbound/RecordContext";
import { RecordDetail } from "@/workspace/adapters/inbound/RecordScreens";
export default function Page() {
  const pet = usePet();
  const record = useRecord();
  return <RecordDetail key={record.id} pet={pet} record={record} />;
}
