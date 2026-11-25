"use client";
import Link from "next/link";
import { notFound } from "next/navigation";
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/components/Icon";
import { Photo } from "@/components/Photo";
import { MediaPlayer } from "@/components/MediaAttachments";
import { registrationDate } from "@/lib/date";
import type { Pet } from "../../pet/types/Pet";
import { myAnswer } from "../utils/myAnswer";
import s from "./QuestionHistory.module.css";

export function QuestionPromptDetail({
  pet,
  promptId,
}: {
  pet: Pet;
  promptId: string;
}) {
  const app = useApp();
  const prompt = app.state.questionPrompts.find(
    (p) => p.id === promptId && p.petId === pet.id,
  );
  if (!prompt) notFound();
  const answers = app.state.questionAnswers
    .filter((a) => a.petId === pet.id && a.promptId === prompt.id)
    .sort(
      (a, b) =>
        b.registeredAt.localeCompare(a.registeredAt) ||
        b.id.localeCompare(a.id),
    );
  const mine = myAnswer(answers, app.account?.id);
  return (
    <>
      <Link className="text-link" href={`/pets/${pet.id}/questions`}>
        ← 오늘의 질문
      </Link>
      <header className={s.questionHeading}>
        <span className={s.badge}>
          {prompt.mode === "GARDEN"
            ? "기억을 나누는 질문"
            : "함께한 오늘의 질문"}
        </span>
        <h1>{prompt.questionSnapshot}</h1>
        <p>같은 순간, 저마다의 시선으로 남긴 이야기</p>
      </header>
      <div className={s.familyHeading}>
        <h2>
          우리 가족의 답변 <span>{answers.length}</span>
        </h2>
        {app.sessionSource === "demo" && <small>가족 답변 데모</small>}
      </div>
      {!mine && pet.accessRole !== "VIEWER" && prompt.mode === pet.mode && (
        <Link
          className="button primary full"
          href={`/pets/${pet.id}/questions/answers/new?promptId=${encodeURIComponent(prompt.id)}`}
        >
          나도 답변 남기기
        </Link>
      )}
      {!answers.length && (
        <p className={s.unanswered}>
          아직 답변이 없어요. 첫 이야기를 남겨 주세요.
        </p>
      )}
      <div className={s.answers}>
        {answers.map((answer) => (
          <article
            key={answer.id}
            className={s.card}
            aria-label={`${answer.authorName ?? "가족"}의 답변`}
          >
            <div className={s.author}>
              <span
                className={s.avatar}
                data-mine={answer.authorId === app.account?.id}
              >
                {(answer.authorName ?? "가족").slice(0, 1)}
              </span>
              <div>
                <strong>
                  {answer.authorName ?? "가족"}
                  {answer.authorId === app.account?.id && (
                    <span className={s.me}>나</span>
                  )}
                </strong>
                <time>
                  {answer.legacyRecordedDate ??
                    registrationDate(answer.registeredAt)}
                </time>
              </div>
            </div>
            {answer.photoUrl && (
              <div className={s.photo}>
                <Photo
                  src={answer.photoUrl}
                  alt={`${answer.authorName ?? "가족"}의 기억 사진`}
                />
              </div>
            )}
            <p className={s.answerBody}>{answer.body}</p>
            {answer.attachments?.map((file) => (
              <MediaPlayer key={file.id} file={file} />
            ))}
            <Link
              className={s.detailLink}
              href={`/pets/${pet.id}/questions/answers/${answer.id}`}
            >
              {answer.authorId === app.account?.id
                ? "내 답변 보기 · 수정"
                : "답변 자세히 보기"}
              <Icon name="chevron" size={14} />
            </Link>
          </article>
        ))}
      </div>
      {app.sessionSource === "demo" && (
        <p className={s.note}>
          엄마·아빠는 화면 체험을 위한 예시 작성자예요. 실제 가족 계정을
          생성하거나 초대하지 않아요.
        </p>
      )}
    </>
  );
}
