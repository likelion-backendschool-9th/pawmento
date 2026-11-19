"use client";
import { useEffect, useState } from "react";
import { useApp } from "@/app/AppProvider";
import { messageOf } from "./presentation";
export function useCurrentQuestion(petId: string, enabled = true) {
  const { dependencies, account, state } = useApp();
  const mode = state.pets.find((p) => p.id === petId)?.mode;
  const [result, setResult] = useState<{
    key: string;
    id: string;
    text: string;
    error: string;
  } | null>(null);
  const [attempt, setAttempt] = useState(0);
  const key = `${account?.id}:${petId}:${mode}`;
  useEffect(() => {
    let active = true;
    if (!account || !enabled) return;
    dependencies.workspace.questions
      .current(account.id, petId)
      .then((prompt) => {
        if (active)
          setResult({
            key,
            id: prompt.id,
            text: prompt.questionSnapshot,
            error: "",
          });
      })
      .catch((cause) => {
        if (active)
          setResult({ key, id: "", text: "", error: messageOf(cause) });
      });
    return () => {
      active = false;
    };
  }, [dependencies, account, petId, mode, key, attempt, enabled]);
  return {
    id: result?.key === key ? result.id : "",
    text: result?.key === key ? result.text : "",
    error: result?.key === key ? result.error : "",
    retry: () => setAttempt((v) => v + 1),
  };
}
