"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/components/Icon";
import { QuestionHeading } from "./QuestionHeading";
import { MediaAttachments } from "@/components/MediaAttachments";
import type { MediaAttachment } from "@/types/MediaAttachment";
import type { Pet } from "@/features/pet/types/Pet";
import { useUnsavedChanges } from "@/features/shell/hooks/useUnsavedChanges";
import { useCurrentQuestion } from "../hooks/useCurrentQuestion";
import { questionNumber } from "../utils/questionNumber";
import s from "./TodayQuestion.module.css";

export function TodayQuestion({ pet }: { pet: Pet }) {
  const app = useApp();
  const router = useRouter();
  const writable = pet.accessRole !== "VIEWER";
  const assigned = useCurrentQuestion(pet.id, writable);
  const pending = app.state.questionPrompts.find(p => p.petId === pet.id && p.mode === pet.mode && !p.completedAt);
  const prompt = writable ? assigned : { id: pending?.id ?? "", text: pending?.questionSnapshot ?? "", error: "", retry: () => {} };
  const number = questionNumber(pet.id, prompt.id, app.state.questionPrompts)
    ?? app.state.questionPrompts.filter(p => p.petId === pet.id).length + 1;
  const [body, setBody] = useState("");
  const [photo, setPhoto] = useState("");
  const [attachments, setAttachments] = useState<MediaAttachment[]>([]);
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState("");
  useUnsavedChanges(!saved && Boolean(body || photo || attachments.length));

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!writable || busy || !prompt.id || !app.account) return;
    setBusy(true);
    setError("");
    try {
      await app.dependencies.workspace.questions.save(app.account.id, {
        petId: pet.id, promptId: prompt.id, body, photoUrl: photo, attachments,
        authorName: app.account.nickname,
      });
      setSaved(true);
      setBody("");
      setPhoto("");
      setAttachments([]);
      await app.refresh();
      app.notify("질문에 답변을 남겼어요.");
      router.push(`/pets/${pet.id}/questions/prompts/${prompt.id}`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "답변을 저장하지 못했어요.");
    } finally { setBusy(false); }
  }

  return (
    <>
      <QuestionHeading title="오늘의 질문" body={`${pet.name}의 이야기를, 나의 시선으로 남겨요.`}>
        <Link className="button secondary" href={`/pets/${pet.id}/questions/history`}>
          <Icon name="book" size={15} /> 이전 질문
        </Link>
      </QuestionHeading>
      {prompt.error ? (
        <div className={s.status} role="alert">
          <p>{prompt.error}</p>
          <button className="button secondary" onClick={prompt.retry}>다시 불러오기</button>
        </div>
      ) : !prompt.id ? (
        <div className={s.status} role="status">
          {writable ? "질문을 준비하고 있어요…" : "아직 이 모드에 배정된 질문이 없어요. 이전 질문은 함께 읽을 수 있어요."}
        </div>
      ) : (
        <form className={s.card} onSubmit={submit}>
          <div className={s.question}>
            <div className={s.meta}>
              <span className={s.number}>#{number}</span>
              <span>{pet.mode === "GARDEN" ? "기억을 꺼내는 질문" : "오늘을 담는 질문"}</span>
            </div>
            <h2>{prompt.text}</h2>
            <p>답변을 남길 때까지 이 질문은 그대로 기다려요.</p>
          </div>
          {writable ? (
            <div className={s.compose}>
              <label htmlFor="today-question-answer">나의 답변</label>
              <textarea id="today-question-answer" rows={4} maxLength={3000} required
                value={body} disabled={busy}
                onChange={event => { setBody(event.target.value); setSaved(false); }}
                placeholder="문득 떠오르는 작은 순간부터 들려주세요." />
              <div className={s.count}>{body.length.toLocaleString()} / 3,000</div>
              <div className={s.media}>
                <MediaAttachments photo={photo} onPhotoChange={value => { setPhoto(value); setSaved(false); }}
                  value={attachments} onChange={value => { setAttachments(value); setSaved(false); }} />
              </div>
              {error && <p role="alert" className="error">{error}</p>}
              <button className={`button primary full ${s.save}`} disabled={busy || !body.trim()}>
                <Icon name="check" size={16} />{busy ? "저장하는 중…" : "답변 남기기"}
              </button>
              <p className={s.note}>답변을 등록한 날로 기록돼요. 저장한 답변은 이전 질문에서 다시 볼 수 있어요.</p>
            </div>
          ) : <p className={s.note}>이 공간은 열람 권한으로 함께 보고 있어요.</p>}
        </form>
      )}
    </>
  );
}
