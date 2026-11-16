"use client";
import Link from "next/link";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/shared/ui/Icon";
import { today } from "@/shared/date";
import { HomeScreen } from "@/workspace/adapters/inbound/HomeScreen";
import { QuestionAnswer } from "@/shared/ui/QuestionAnswer";
import type { Pet } from "@/workspace/domain/models";
import { LifeHeading, LifeEmpty, DemoNote, lifeStyles as s } from "./LifeUi";
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
export function GardenScreen({ pet }: { pet: Pet }) {
  return <HomeScreen pet={pet} />;
}
export function QuestionHistory({ pet }: { pet: Pet }) {
  const app = useApp();
  const [adding, setAdding] = useState(false);
  const [demoError, setDemoError] = useState("");
  const records = app.state.records
    .filter((record) => record.petId === pet.id && record.type === "DIARY")
    .sort((a, b) => b.recordedDate.localeCompare(a.recordedDate));
  return (
    <>
      <LifeHeading
        label="QUESTION JOURNAL"
        title="오늘의 질문"
        body="오늘의 질문에 답하고, 지난 질문과 답변을 다시 읽어보세요. 다이어리와 별도로 보관해요."
      >
        {pet.accessRole !== "VIEWER" && (
          <Link
            className="button primary"
            href={
              records.some((record) => record.recordedDate === today())
                ? `/pets/${pet.id}/diary/${records.find((record) => record.recordedDate === today())!.id}`
                : `/pets/${pet.id}/diary/new`
            }
          >
            {records.some((record) => record.recordedDate === today())
              ? "오늘의 질문과 답변 보기"
              : "오늘의 질문 답하기"}
          </Link>
        )}
      </LifeHeading>
      {app.account?.id === "demo" && pet.accessRole !== "VIEWER" && (
        <div className="demo-banner">
          <Icon name="question" size={18} />
          <div>
            <p>
              질문과 답변 샘플을 읽고, 날짜를 눌러 기록을 열어보세요. 기존
              답변은 그대로 유지돼요.
            </p>
            <button
              className="button secondary"
              disabled={adding}
              onClick={async () => {
                setAdding(true);
                setDemoError("");
                try {
                  await app.addDemoQuestions(pet.id);
                } catch (cause) {
                  setDemoError(
                    cause instanceof Error
                      ? cause.message
                      : "샘플을 추가하지 못했어요.",
                  );
                } finally {
                  setAdding(false);
                }
              }}
            >
              {adding ? "추가 중…" : "데모 질문 기록 추가"}
            </button>
          </div>
        </div>
      )}
      {demoError && (
        <p className="error" role="alert">
          {demoError}
        </p>
      )}
      {records.length > 0 && <h2>지난 질문과 답변</h2>}
      {records.length ? (
        <div className={s.twoGrid}>
          {records.map((record) => (
            <Link
              href={`/pets/${pet.id}/diary/${record.id}`}
              className={s.panel}
              key={record.id}
            >
              <time className={s.meta}>{record.recordedDate}</time>
              {record.questionId?.startsWith("demo-question-") && (
                <span className="badge">데모 기록</span>
              )}
              <QuestionAnswer question={record.question} answer={record.body} />
            </Link>
          ))}
        </div>
      ) : (
        <LifeEmpty
          title="첫 질문을 기다리는 공간"
          body="오늘의 질문에 답하면 이곳에 질문과 답변이 함께 쌓여요."
          href={`/pets/${pet.id}/diary/new`}
          action="답변 남기기"
        />
      )}
    </>
  );
}
