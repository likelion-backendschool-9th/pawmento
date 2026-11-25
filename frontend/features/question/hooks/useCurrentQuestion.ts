"use client";
import { currentQuestionText } from "../utils/currentQuestionText";
import { useEffect, useState } from "react";
import { useApp } from "@/app/AppProvider";
import { messageOf } from "../../shell/utils/presentation";
export function useCurrentQuestion(petId: string, enabled = true) {
  const { dependencies, account, state } = useApp();
  const pet = state.pets.find((p) => p.id === petId);
  const mode = pet?.mode;
  const name = pet?.name ?? "";
  const [result, setResult] = useState<{
    key: string;
    id: string;
    text: string;
    error: string;
  } | null>(null);
  const [attempt, setAttempt] = useState(0);
  const key = `${account?.id}:${petId}:${mode}:${name}`;
  useEffect(() => {
    let active = true;
    if (!account || !enabled) return;
    dependencies.workspace.questions
      .current(account.id, petId)
      .then(async (prompt) => {
        if (!active) return;
        let text = prompt.questionSnapshot;
        if (!prompt.completedAt) {
          try {
            const pool = (await dependencies.life.load()).questions;
            const template = pool.find(item => item.id === prompt.questionId && item.mode === mode)?.text;
            text = currentQuestionText(name, text, template);
          } catch { /* The assigned snapshot remains readable if optional display data is unavailable. */ }
        }
        if (active)
          setResult({
            key,
            id: prompt.id,
            text,
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
  }, [dependencies, account, petId, mode, name, key, attempt, enabled]);
  return {
    id: result?.key === key ? result.id : "",
    text: result?.key === key ? result.text : "",
    error: result?.key === key ? result.error : "",
    retry: () => setAttempt((v) => v + 1),
  };
}
