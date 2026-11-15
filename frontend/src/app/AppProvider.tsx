"use client";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from "react";
import type { Account } from "../auth/domain/account";
import type { Workspace } from "../workspace/domain/models";
import { createDependencies, type Dependencies } from "./composition";
import { today } from "../shared/date";
type Context = {
  dependencies: Dependencies;
  account: Account | null;
  state: Workspace;
  loading: boolean;
  error: string;
  refresh: (account?: Account) => Promise<void>;
  authenticate: (account: Account) => Promise<void>;
  startDemo: () => Promise<void>;
  signOut: () => Promise<void>;
  notify: (message: string) => void;
};
const AppContext = createContext<Context | null>(null);
export function AppProvider({ children }: { children: React.ReactNode }) {
  const [dependencies] = useState(createDependencies);
  const [account, setAccount] = useState<Account | null>(null);
  const [state, setState] = useState<Workspace>({
    pets: [],
    records: [],
    selectedPetId: "",
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [message, notify] = useState("");
  const refresh = useCallback(
    async (current = account ?? undefined) => {
      if (!current) return;
      setLoading(true);
      setError("");
      try {
        setState(await dependencies.workspace.load(current.id));
      } catch (cause) {
        setError(
          cause instanceof Error ? cause.message : "기록을 불러오지 못했어요.",
        );
        throw cause;
      } finally {
        setLoading(false);
      }
    },
    [account, dependencies],
  );
  const authenticate = async (current: Account) => {
    const member = await dependencies.life.registerMember({
      id: current.id,
      name: current.nickname,
      isAdmin: current.role === "ADMIN",
    });
    if (member.status === "SUSPENDED") {
      await dependencies.auth.signOut();
      throw Error("데모 활동이 제한된 계정이에요.");
    }
    setAccount(current);
    await refresh(current);
  };
  const startDemo = async () => {
    const current = await dependencies.auth.startDemo();
    const existing = await dependencies.workspace.load(current.id);
    if (!existing.pets.length) {
      const pet = await dependencies.workspace.savePet(current.id, {
        name: "보리",
        species: "DOG",
        breed: "웰시코기",
        birthday: "2021-04-12",
        photoUrl: "reference:portrait",
      });
      for (const sample of [
        {
          days: 1,
          body: "공을 쫓아 신나게 달리던 순간! 보리가 정말 행복해 보였어요.",
          photoUrl: "reference:running",
        },
        {
          days: 3,
          body: "보리가 낮잠을 푹 잔 오후. 좋아하는 인형을 꼭 안고 곤히 잠들었어요.",
          photoUrl: "reference:sleeping",
        },
        {
          days: 5,
          body: "노을 산책, 바람을 신나게 맡는 보리. 함께 걷는 길마다 소중한 기억이 쌓여요.",
          photoUrl: "reference:sunset",
        },
      ]) {
        const date = new Date(`${today()}T12:00:00+09:00`);
        date.setUTCDate(date.getUTCDate() - sample.days);
        await dependencies.workspace.saveMemory(current.id, {
          petId: pet.id,
          type: "PHOTO",
          recordedDate: date.toISOString().slice(0, 10),
          question: "",
          body: sample.body,
          photoUrl: sample.photoUrl,
        });
      }
    }
    await authenticate(current);
  };
  const signOut = async () => {
    await dependencies.auth.signOut();
    setAccount(null);
    setState({ pets: [], records: [], selectedPetId: "" });
    notify("로그아웃했어요.");
  };
  useEffect(() => {
    if (!message) return;
    const timer = setTimeout(() => notify(""), 6000);
    return () => clearTimeout(timer);
  }, [message]);
  return (
    <AppContext.Provider
      value={{
        dependencies,
        account,
        state,
        loading,
        error,
        refresh,
        authenticate,
        startDemo,
        signOut,
        notify,
      }}
    >
      {children}
      {message && (
        <div className="toast" role="status">
          {message}
          <button aria-label="알림 닫기" onClick={() => notify("")}>
            ×
          </button>
        </div>
      )}
    </AppContext.Provider>
  );
}
export function useApp() {
  const context = useContext(AppContext);
  if (!context) throw Error("AppProvider is required");
  return context;
}
