"use client";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from "react";
import type { Account } from "../features/auth/domain/account";
import type { SessionSource } from "../features/auth/application/auth";
import { emptyWorkspace } from "../features/workspace/domain/models";
import type { Workspace } from "../features/workspace/domain/models";
import {
  createDependencies,
  demoQuestionInputs,
  type Dependencies,
} from "./composition";
import { today } from "../lib/date";
type Context = {
  dependencies: Dependencies;
  account: Account | null;
  sessionSource: SessionSource;
  sessionReady: boolean;
  state: Workspace;
  loading: boolean;
  error: string;
  refresh: (account?: Account) => Promise<void>;
  authenticate: (account: Account) => Promise<void>;
  startDemo: () => Promise<void>;
  addDemoQuestions: (petId: string) => Promise<void>;
  signOut: () => Promise<void>;
  notify: (message: string) => void;
};
const AppContext = createContext<Context | null>(null);
export function AppProvider({ children }: { children: React.ReactNode }) {
  const [dependencies] = useState(createDependencies);
  const [account, setAccount] = useState<Account | null>(null);
  const [sessionSource, setSessionSource] = useState<SessionSource>(null);
  const [sessionReady, setSessionReady] = useState(false);
  const [state, setState] = useState<Workspace>(emptyWorkspace);
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
      setSessionSource(null);
      throw Error("데모 활동이 제한된 계정이에요.");
    }
    setAccount(current);
    setSessionSource(dependencies.auth.sessionSource());
    await refresh(current);
  };
  const startDemo = async () => {
    const current = await dependencies.auth.startDemo();
    setAccount(current);
    setSessionSource(dependencies.auth.sessionSource());
    setState(emptyWorkspace());
    const existing = await dependencies.workspace.load(current.id);
    if (existing.pets.length) {
      // 기존 데모로 다시 진입할 때 샘플 추가/재저장으로 기록 열람을 막지 않는다.
      await authenticate(current);
      return;
    }
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
        await dependencies.workspace.diary.save(current.id, {
          petId: pet.id,
          recordedDate: date.toISOString().slice(0, 10),
          body: sample.body,
          photoUrl: sample.photoUrl,
        });
      }
    }
    const seeded = await dependencies.workspace.load(current.id);
    const demoPet =
      seeded.pets.find((pet) => pet.id === seeded.selectedPetId) ??
      seeded.pets[0];
    if (demoPet) {
      await dependencies.workspace.seedDemoAnswers(
        current.id,
        demoQuestionInputs(demoPet, seeded.questionAnswers, today()),
      );
    }
    await authenticate(current);
  };
  const addDemoQuestions = async (petId: string) => {
    if (account?.id !== "demo")
      throw Error("데모 계정에서만 샘플을 추가할 수 있어요.");
    const current = await dependencies.workspace.load(account.id);
    const pet = current.pets.find((item) => item.id === petId);
    if (!pet || pet.accessRole === "VIEWER")
      throw Error("기록을 추가할 수 없어요.");
    const inputs = demoQuestionInputs(pet, current.questionAnswers, today());
    await dependencies.workspace.seedDemoAnswers(account.id, inputs);
    await refresh();
    notify(
      inputs.length
        ? "데모 질문과 답변을 추가했어요."
        : "이미 데모 질문과 답변이 있어요.",
    );
  };
  const signOut = async () => {
    await dependencies.auth.signOut();
    setAccount(null);
    setSessionSource(null);
    setState(emptyWorkspace());
    notify("로그아웃했어요.");
  };
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const current = await dependencies.auth.current();
        if (!cancelled && current) await authenticate(current);
      } catch {
        /* 부트스트랩 실패 시 비로그인으로 둔다 */
      } finally {
        if (!cancelled) setSessionReady(true);
      }
    })();
    return () => {
      cancelled = true;
    };
    // 최초 쿠키 세션만 복구한다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dependencies]);
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
        sessionSource,
        sessionReady,
        state,
        loading,
        error,
        refresh,
        authenticate,
        startDemo,
        addDemoQuestions,
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
