"use client";
import { redirect, useParams } from "next/navigation";
export default function Page() {
  const p = useParams<{ petId: string; recordId: string }>();
  redirect(`/pets/${p.petId}/diary/${p.recordId}/edit`);
}
