"use client";
import Link from "next/link";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/components/Icon";
import type { Pet } from "@/features/workspace/domain/models";
import { LifeHeading, DemoNote, lifeStyles as s } from "./LifeUi";
export function ModeScreen({ pet }: { pet: Pet }) {
  const app = useApp();
  const router = useRouter();
  const [mode, setMode] = useState<Pet["mode"]>(pet.mode);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function save() {
    setBusy(true);
    setError("");
    try {
      await app.dependencies.workspace.changeMode(
        app.account!.id,
        pet.id,
        mode,
      );
      await app.dependencies.workspace.selectPet(app.account!.id, pet.id);
      await app.refresh();
      app.notify(
        mode === "GARDEN"
          ? "기억의 정원으로 이동해요."
          : "오늘의 이야기로 돌아왔어요.",
      );
      router.push("/home");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "변경하지 못했어요.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <Link className="text-link" href="/pets">
        ← 반려동물 관리로 돌아가기
      </Link>
      <LifeHeading
        label="PET MODE"
        title={`${pet.name}의 공간 설정`}
        body={`${pet.name}의 모드만 변경해요. 다른 반려동물의 설정과 기록은 그대로 유지돼요.`}
      />
      <div className={s.modeGrid}>
        {[
          {
            key: "ACTIVE",
            icon: "sun",
            title: "함께하는 오늘",
            body: "오늘의 질문에 답하고, 함께하는 일상을 꾸준히 남기는 공간이에요.",
          },
          {
            key: "GARDEN",
            icon: "leaf",
            title: "기억의 정원",
            body: "차곡차곡 쌓인 사진과 이야기를 천천히 꺼내보는 조용한 공간이에요.",
          },
        ].map((item) => (
          <button
            key={item.key}
            className={mode === item.key ? s.modeSelected : ""}
            onClick={() => {
              setMode(item.key as Pet["mode"]);
              setConfirming(false);
            }}
            aria-pressed={mode === item.key}
          >
            <Icon name={item.icon} size={35} />
            <h2>{item.title}</h2>
            <p>{item.body}</p>
            {pet.mode === item.key && <span className="badge">현재 모드</span>}
          </button>
        ))}
      </div>
      <DemoNote>
        모드를 바꿔도 모든 질문·답변·사진·기억은 그대로 유지됩니다. 언제든
        되돌릴 수 있고, 정원에서도 새 기록을 남길 수 있어요.
      </DemoNote>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {confirming ? (
        <div className={s.panel}>
          <h2>
            {mode === "GARDEN"
              ? "기억의 정원으로 이동할까요?"
              : "함께하는 오늘로 돌아갈까요?"}
          </h2>
          <p>{pet.name}의 모드만 변경합니다. 모든 기록은 그대로 유지돼요.</p>
          <div className={s.inlineActions}>
            <button
              className="button secondary"
              onClick={() => setConfirming(false)}
            >
              취소
            </button>
            <button
              className="button primary"
              disabled={busy}
              onClick={() => void save()}
            >
              {busy ? "변경 중…" : "확인하고 모드 변경"}
            </button>
          </div>
        </div>
      ) : (
        <button
          className="button primary"
          disabled={mode === pet.mode}
          onClick={() => setConfirming(true)}
        >
          선택한 모드 적용
        </button>
      )}
    </>
  );
}
