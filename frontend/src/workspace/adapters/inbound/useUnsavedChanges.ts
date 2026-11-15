"use client";
import { useEffect } from "react";

export function useUnsavedChanges(dirty: boolean) {
  useEffect(() => {
    if (!dirty) return;
    const unload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
    };
    const click = (event: MouseEvent) => {
      const target =
        event.target instanceof Element ? event.target.closest("a") : null;
      if (
        target &&
        target.href !== window.location.href &&
        !target.hasAttribute("download") &&
        !window.confirm("작성 중인 내용이 있어요. 저장하지 않고 나갈까요?")
      ) {
        event.preventDefault();
        event.stopPropagation();
      }
    };
    window.addEventListener("beforeunload", unload);
    document.addEventListener("click", click, true);
    return () => {
      window.removeEventListener("beforeunload", unload);
      document.removeEventListener("click", click, true);
    };
  }, [dirty]);
}
