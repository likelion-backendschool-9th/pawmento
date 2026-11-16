"use client";
import Link from "next/link";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/shared/ui/Icon";
import { today } from "@/shared/date";
import { RecordCard } from "@/workspace/adapters/inbound/WorkspaceUi";
import { Photo } from "@/shared/ui/Photo";
import g from "./GardenHome.module.css";
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
      await app.refresh();
      app.notify(
        mode === "GARDEN"
          ? "기억의 정원으로 이동해요."
          : "오늘의 이야기로 돌아왔어요.",
      );
      router.push(mode === "GARDEN" ? `/pets/${pet.id}/garden` : "/home");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "변경하지 못했어요.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <Link className="text-link" href="/settings">
        ← 설정으로 돌아가기
      </Link>
      <LifeHeading
        label="PET MODE"
        title={`${pet.name}의 공간 설정`}
        body="기록하는 오늘과 기억을 돌아보는 정원 사이를 자유롭게 오갈 수 있어요."
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
          <p>기록은 삭제되지 않으며 모드만 변경됩니다.</p>
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
  const app = useApp();
  const [randomIndex, setRandomIndex] = useState(0);
  const date = today();
  const records = app.state.records
    .filter((record) => record.petId === pet.id)
    .sort((a, b) => b.recordedDate.localeCompare(a.recordedDate));
  const onThisDay = records.filter(
    (record) =>
      record.recordedDate.slice(5) === date.slice(5) &&
      record.recordedDate.slice(0, 4) < date.slice(0, 4),
  );
  if (pet.mode !== "GARDEN")
    return (
      <>
        <LifeHeading
          label="MEMORY GARDEN"
          title="기억의 정원"
          body="기록을 보존하면서, 함께한 순간을 천천히 돌아보는 공간입니다."
        />
        <LifeEmpty
          title="정원 모드에서 만나요"
          body="일상과 정원은 설정 화면에서 전환할 수 있어요."
          href="/settings"
          action="설정으로"
        />
      </>
    );
  return (
    <>
      <section className={g.hero}>
        <div className={g.heroCopy}>
          <span>A GARDEN OF OUR DAYS</span>
          <h1>{pet.name}의 기억의 정원</h1>
          <p>
            함께한 날들이, 오래도록.
            <br />
            소중한 기억이 자라는 이곳에서
            <br />
            우리의 계절을 천천히 꺼내봐요.
          </p>
        </div>
        <div className={g.portrait}>
          <div>
            <Photo src={pet.photoUrl} alt={`${pet.name}의 프로필 사진`} />
          </div>
          <small>함께 남긴 {records.length}개의 기억</small>
        </div>
      </section>
      <div className={s.shortcutGrid}>
        {[
          ["book", "기록 모아보기", `/pets/${pet.id}/timeline`],
          ["question", "질문과 답변", `/pets/${pet.id}/questions`],
          ["mail", "기억의 우체통", `/pets/${pet.id}/garden/mailbox`],
          ["plus", "새 기억 남기기", `/pets/${pet.id}/records/new`],
        ].map(([icon, title, href]) => (
          <Link href={href} key={title}>
            <Icon name={icon} size={24} />
            <strong>{title}</strong>
            <Icon name="chevron" size={16} />
          </Link>
        ))}
      </div>
      {records.length > 0 && (
        <section className={g.gallery}>
          <div className={g.galleryHeading}>
            <div>
              <span>OUR LITTLE EXHIBITION</span>
              <h2>사진 속에 머문, 우리의 계절</h2>
            </div>
            <Link href={`/pets/${pet.id}/media`}>
              앨범 펼쳐보기 <Icon name="chevron" size={14} />
            </Link>
          </div>
          <div className={g.galleryGrid}>
            {records.slice(0, 3).map((record) => (
              <Link
                className={g.photoPrint}
                key={record.id}
                href={`/pets/${pet.id}/${record.type === "DIARY" ? "diary" : "records"}/${record.id}`}
              >
                <div>
                  <Photo
                    src={record.photoUrl}
                    alt={`${record.recordedDate}의 추억 사진`}
                  />
                </div>
                <time>{record.recordedDate.replaceAll("-", ". ")}</time>
                <p>{record.body || record.question || "함께한 작은 순간"}</p>
              </Link>
            ))}
          </div>
        </section>
      )}
      <section className={g.invitation}>
        <div>
          <Icon name="mail" size={32} />
          <section>
            <h2>어느 날의 기억이, 한 장의 엽서로.</h2>
            <p>우연히 펼친 기록 속 우리 아이를 수채화 엽서로 만나보세요.</p>
          </section>
        </div>
        <Link href={`/pets/${pet.id}/garden/mailbox`}>
          기억의 우체통 열기 <Icon name="arrow" size={15} />
        </Link>
      </section>
      {onThisDay.length > 0 && (
        <section className={s.section}>
          <h2>지난 오늘, 우리가 남긴 순간</h2>
          <div className={s.threeGrid}>
            {onThisDay.slice(0, 3).map((record) => (
              <RecordCard key={record.id} record={record} />
            ))}
          </div>
        </section>
      )}
      <div className={s.twoGrid}>
        <section className={s.panel}>
          <div className={s.panelHeading}>
            <h2>문득 꺼내보는 기억</h2>
            <button
              className="text-link"
              onClick={() =>
                setRandomIndex((index) =>
                  records.length > 1
                    ? (index +
                        1 +
                        Math.floor(Math.random() * (records.length - 1))) %
                      records.length
                    : index,
                )
              }
            >
              <Icon name="shuffle" size={15} />
              다른 기억
            </button>
          </div>
          {records.length ? (
            <RecordCard record={records[randomIndex % records.length]} />
          ) : (
            <LifeEmpty
              title="아직 쌓인 기억이 없어요"
              body="첫 기억부터 천천히 담아보세요."
              href={`/pets/${pet.id}/records/new`}
              action="기억 남기기"
            />
          )}
        </section>
        <section className={s.panel}>
          <h2>기억 속의 질문과 답변</h2>
          {records.find((record) => record.type === "DIARY") ? (
            <QuestionAnswer
              question={
                records.find((record) => record.type === "DIARY")!.question
              }
              answer={records.find((record) => record.type === "DIARY")!.body}
            />
          ) : (
            <p className="muted">
              질문 다이어리를 남기면 그날의 질문과 답변을 함께 꺼내볼 수 있어요.
            </p>
          )}
          <Link className="text-link" href={`/pets/${pet.id}/questions`}>
            지난 질문 기록 보기
            <Icon name="arrow" size={15} />
          </Link>
        </section>
      </div>
    </>
  );
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
        title="질문과 답변의 기록"
        body="질문을 받았던 그날의 문구와 내가 남긴 답변을 함께 보관해요."
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
