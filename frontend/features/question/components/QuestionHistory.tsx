"use client";
import { QuestionHeading } from "./QuestionHeading";
import { questionHistory } from "@/features/question/utils/questionHistory";
import { registrationDate } from "@/lib/date";

import Link from "next/link";
import { RecordActionLink } from "@/components/RecordAction";
import { useState } from "react";
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/components/Icon";
import { ArchiveNavigation } from "@/components/ArchiveNavigation";
import { questionNumber } from "../utils/questionNumber";
import { myAnswer } from "../utils/myAnswer";
import q from "./QuestionHistory.module.css";
import type { Pet } from "@/features/pet/types/Pet";
import {
  LifeEmpty,
} from "@/features/shell/components/LifeUi";
export function QuestionHistory({ pet }: { pet: Pet }) {
  const app = useApp();
  const [adding, setAdding] = useState(false);
  const [demoError, setDemoError] = useState("");
  const history = questionHistory(
    pet.id,
    app.state.questionPrompts,
    app.state.questionAnswers,
  );
  return (
    <>
      <QuestionHeading
        title="이전 질문"
        body="한 질문에 담긴 나와 가족의 이야기를 다시 만나요."
      >
        {pet.accessRole !== "VIEWER" && (
<RecordActionLink href={`/pets/${pet.id}/questions`}>오늘의 질문</RecordActionLink>
        )}
      </QuestionHeading>
      <ArchiveNavigation petId={pet.id} active="questions" />
      {app.account?.id === "demo" && pet.accessRole !== "VIEWER" && (
        <details className={q.demoTools}>
          <summary>데모 질문 기록 관리</summary>
          <Icon name="question" size={18} />
          <div>
            <p>
              목록에는 내 답변만 보여요. 질문을 열어 가족의 이야기도 함께
              읽어보세요. 기존 답변은 그대로 유지돼요.
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
        </details>
      )}
      {demoError && (
        <p className="error" role="alert">
          {demoError}
        </p>
      )}
      {history.length ? (
        <div className={q.historyList}>
          {history.map(({ prompt, answers }) => {
            const mine = myAnswer(answers, app.account?.id);
            const href = `/pets/${pet.id}/questions/prompts/${prompt.id}`;
            return (
              <article key={prompt.id} className={q.card}>
                <div className={q.eyebrow}>
                  <span className={q.number}>#{questionNumber(pet.id, prompt.id, app.state.questionPrompts)}</span>
                  <span>{prompt.mode === "GARDEN" ? "기억" : prompt.mode === "ACTIVE" ? "일상" : "이전 질문"}</span>
                </div>
                <Link href={href} className={q.preview}>
                  <h3>{prompt.questionSnapshot}</h3>
                  {mine ? (
                    <div className={q.myAnswer}>
                      <span className={q.badge}>나의 답변</span>
                      <p>{mine.body}</p>
                      <time>
                        {mine.legacyRecordedDate ??
                          registrationDate(mine.registeredAt)}
                      </time>
                    </div>
                  ) : (
                    <p className={q.unanswered}>
                      아직 내 답변을 남기지 않았어요.
                    </p>
                  )}
                </Link>
                <Link href={href} className={q.footer}>
                  <span>
                    <Icon name="users" size={16} /> 가족 답변 {answers.length}개
                  </span>
                  <span>
                    질문 열기 <Icon name="chevron" size={16} />
                  </span>
                </Link>
              </article>
            );
          })}
        </div>
      ) : (
        <LifeEmpty
          title="첫 질문을 기다리는 공간"
          body="오늘의 질문에 답하면 이곳에 질문과 답변이 함께 쌓여요."
          href={`/pets/${pet.id}/questions`}
          action="답변 남기기"
          actionIcon="edit"
        />
      )}
    </>
  );
}
