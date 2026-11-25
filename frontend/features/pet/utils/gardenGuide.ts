// UI-only tutorial preference. No member IDs, records or credentials are stored.
export const GARDEN_GUIDE_KEY = "pawmento.ui.garden-guide.v1";
type GuideStorage = Pick<Storage, "getItem" | "setItem">;
export function createGardenGuide(getStorage: () => GuideStorage) {
  let dismissed = false;
  return {
    shouldShow() {
      if (dismissed) return false;
      try {
        return getStorage().getItem(GARDEN_GUIDE_KEY) !== "seen";
      } catch {
        return true;
      }
    },
    finish() {
      dismissed = true;
      try {
        getStorage().setItem(GARDEN_GUIDE_KEY, "seen");
      } catch {
        /* Still remember the dismissal for this tab. */
      }
    },
  };
}
