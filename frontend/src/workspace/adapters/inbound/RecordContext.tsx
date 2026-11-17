"use client";
import { createContext, useContext } from "react";
import { notFound, useParams } from "next/navigation";
import { useApp } from "@/app/AppProvider";
import type { Memory } from "../../domain/models";
import { usePet } from "./PetContext";
const Context = createContext<Memory | null>(null);
export function RecordBoundary({ children }: { children: React.ReactNode }) {
  const app = useApp();
  const pet = usePet();
  const { recordId } = useParams<{ recordId: string }>();
  const record = app.state.records.find(
    (item) => item.petId === pet.id && item.id === recordId,
  );
  if (!record) notFound();
  return <Context.Provider value={record}>{children}</Context.Provider>;
}
export function useRecord() {
  const record = useContext(Context);
  if (!record) throw Error("RecordBoundary is required");
  return record;
}
