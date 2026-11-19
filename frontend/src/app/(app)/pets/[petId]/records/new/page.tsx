"use client";
import { redirect, useParams, useSearchParams } from "next/navigation";
export default function Page() {
  const p = useParams<{ petId: string }>();
  const date = useSearchParams().get("date");
  redirect(
    `/pets/${p.petId}/diary/new${date ? `?date=${encodeURIComponent(date)}` : ""}`,
  );
}
