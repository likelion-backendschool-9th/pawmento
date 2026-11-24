"use client";
import { createContext, useContext } from "react";
import { notFound, redirect, useParams, usePathname } from "next/navigation";
import { useApp } from "@/app/AppProvider";
import { journalItems } from "../../workspace/domain/models";
import type { JournalItem } from "../../workspace/domain/models";
import { usePet } from "../../pet/hooks/PetContext";
const Context = createContext<JournalItem | null>(null);
export function JournalBoundary({ children }: { children: React.ReactNode }) {
  const app = useApp(),
    pet = usePet();
  const p = useParams<{ diaryId?: string; answerId?: string }>();
  const path = usePathname();
  const id = p.diaryId ?? p.answerId;
  const item = journalItems(app.state).find(
    (d) =>
      d.petId === pet.id &&
      d.id === id &&
      (p.answerId ? d.kind === "QUESTION_ANSWER" : d.kind === "DIARY"),
  );
  if (!item) {
    const legacy = app.state.questionAnswers.find(
      (a) => a.petId === pet.id && a.id === p.diaryId,
    );
    if (legacy)
      redirect(
        `/pets/${pet.id}/questions/answers/${legacy.id}${path.endsWith("/edit") ? "/edit" : ""}`,
      );
    notFound();
  }
  return <Context.Provider value={item}>{children}</Context.Provider>;
}
export function useJournalItem() {
  const item = useContext(Context);
  if (!item) throw Error("JournalBoundary is required");
  return item;
}
