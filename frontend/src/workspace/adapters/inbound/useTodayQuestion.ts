"use client";
import { useEffect, useState } from "react";
import { useApp } from "@/app/AppProvider";
import { messageOf } from "./presentation";
export function useTodayQuestion(petId: string, date: string) {
  const { dependencies } = useApp();
  const [result, setResult] = useState<{
    key: string;
    text: string;
    id: string;
    error: string;
  } | null>(null);
  const [attempt, setAttempt] = useState(0);
  const key = petId + date;
  useEffect(() => {
    let active = true;
    dependencies
      .getTodayQuestion(petId, date)
      .then((question) => {
        if (active)
          setResult({ key, id: question.id, text: question.text, error: "" });
      })
      .catch((cause) => {
        if (active)
          setResult({ key, id: "", text: "", error: messageOf(cause) });
      });
    return () => {
      active = false;
    };
  }, [dependencies, petId, date, key, attempt]);
  return {
    id: result?.key === key ? result.id : "",
    text: result?.key === key ? result.text : "",
    error: result?.key === key ? result.error : "",
    retry: () => setAttempt((value) => value + 1),
  };
}
