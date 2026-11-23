export const unsavedChangesMessage =
  "작성 중인 내용이 있어요. 저장하지 않고 나갈까요?";

/** Each mounted editor owns its registration; disposing one must not clear another. */
export function createUnsavedChangesGuard(confirmLeave: () => boolean) {
  const editors = new Set<symbol>();
  return {
    register() {
      const editor = Symbol("unsaved editor");
      editors.add(editor);
      return () => {
        editors.delete(editor);
      };
    },
    isDirty: () => editors.size > 0,
    confirmNavigation: () => editors.size === 0 || confirmLeave(),
  };
}
