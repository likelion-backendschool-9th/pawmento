"use client";
import { questionHistory } from "@/features/question/utils/questionHistory";
import { registrationDate } from "@/lib/date";

import Link from "next/link";
import { useState } from "react";
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/components/Icon";
import { myAnswer } from "../utils/myAnswer";
import q from "./QuestionHistory.module.css";
import type { Pet } from "@/features/pet/types/Pet";
import {
  LifeHeading,
  LifeEmpty,
  lifeStyles as s,
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
      <LifeHeading
        label="QUESTION JOURNAL"
        title="오늘의 질문"
        body="오늘의 질문에 답하고, 지난 질문과 답변을 다시 읽어보세요. 다이어리와 별도로 보관해요."
      >
        {pet.accessRole !== "VIEWER" && (
          <Link
            className="button primary"
            href={`/pets/${pet.id}/questions/answers/new`}
          >
            현재 질문 답하기
          </Link>
        )}
      </LifeHeading>
      {app.account?.id === "demo" && pet.accessRole !== "VIEWER" && (
        <div className="demo-banner">
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
        </div>
      )}
      {demoError && (
        <p className="error" role="alert">
          {demoError}
        </p>
      )}
      {history.length > 0 && <h2>지난 질문과 답변</h2>}
      {history.length ? (
        <div className={s.twoGrid}>
          {history.map(({ prompt, answers }) => {
            const mine = myAnswer(answers, app.account?.id);
            const href = `/pets/${pet.id}/questions/prompts/${prompt.id}`;
            return (
              <article key={prompt.id} className={q.card}>
                <div className={q.eyebrow}>
                  <span>지난 질문</span>
                  <span>{prompt.mode === "GARDEN" ? "기억" : "일상"}</span>
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
          href={`/pets/${pet.id}/questions/answers/new`}
          action="답변 남기기"
        />
      )}
    </>
  );
}
