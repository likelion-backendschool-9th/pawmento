export const ONBOARDING_STORAGE_KEY = "pawmento:onboarding:v1";
type StoragePort = Pick<Storage, "getItem" | "setItem">;

// 인증·기록 데이터가 아닌 기기별 소개 화면 설정. 저장소가 막혀도 현재 탭에서는 완료 상태를 유지한다.
export function createOnboardingPreference(storage: () => StoragePort | null) {
  let completedInMemory = false;
  return {
    hasCompleted(): boolean {
      if (completedInMemory) return true;
      try { return storage()?.getItem(ONBOARDING_STORAGE_KEY) === "complete"; }
      catch { return false; }
    },
    complete(): void {
      completedInMemory = true;
      try { storage()?.setItem(ONBOARDING_STORAGE_KEY, "complete"); }
      catch { /* 저장 제한 환경에서는 현재 탭의 메모리 상태로 계속 진행한다. */ }
    },
  };
}
