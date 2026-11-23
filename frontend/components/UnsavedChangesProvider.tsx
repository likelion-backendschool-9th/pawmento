"use client";
import { createContext, useContext, useEffect, useState } from "react";
import {
  createUnsavedChangesGuard,
  unsavedChangesMessage,
} from "../lib/unsavedChanges";
const Context = createContext<ReturnType<
  typeof createUnsavedChangesGuard
> | null>(null);
export function UnsavedChangesProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const [guard] = useState(() =>
    createUnsavedChangesGuard(() => window.confirm(unsavedChangesMessage)),
  );
  useEffect(() => {
    const unload = (event: BeforeUnloadEvent) => {
      if (guard.isDirty()) event.preventDefault();
    };
    const click = (event: MouseEvent) => {
      const link =
        event.target instanceof Element ? event.target.closest("a") : null;
      if (
        event.defaultPrevented ||
        !link ||
        link.href === window.location.href ||
        link.hasAttribute("download") ||
        (link.target && link.target !== "_self")
      )
        return;
      if (!guard.confirmNavigation()) {
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
  }, [guard]);
  return <Context.Provider value={guard}>{children}</Context.Provider>;
}
function useGuard() {
  const guard = useContext(Context);
  if (!guard) throw Error("UnsavedChangesProvider is required");
  return guard;
}
export function useConfirmNavigation() {
  return useGuard().confirmNavigation;
}
export function useUnsavedChanges(dirty: boolean) {
  const guard = useGuard();
  useEffect(() => {
    if (dirty) return guard.register();
  }, [dirty, guard]);
}
