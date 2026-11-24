"use client";
import { questionHistory } from "@/features/question/utils/questionHistory";
import { registrationDate } from "@/lib/application/JournalStore";

import Link from "next/link";
import { useState } from "react";
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/components/Icon";
import { QuestionAnswer } from "@/components/QuestionAnswer";
import type { Pet } from "@/features/workspace/domain/models";
import { LifeHeading, LifeEmpty, lifeStyles as s } from "@/features/shell/components/LifeUi";
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
      {history.length > 0 && <h2>지난 질문과 답변</h2>}
      {history.length ? (
        <div className={s.twoGrid}>
          {history.map(({ prompt, answers }) => {
            const canAnswer =
              pet.accessRole !== "VIEWER" &&
              prompt.mode === pet.mode &&
              !answers.some((a) => a.authorId === app.account?.id);
            return (
              <article key={prompt.id} className={s.panel}>
                <h3>{prompt.questionSnapshot}</h3>
                {prompt.questionId.startsWith("demo-question-") && (
                  <span className="badge">데모 기록</span>
                )}
                {answers.length ? (
                  answers.map((answer) => (
                    <Link
                      key={answer.id}
                      href={`/pets/${pet.id}/questions/answers/${answer.id}`}
                      className="text-link"
                    >
                      <time className={s.meta}>
                        {answer.legacyRecordedDate ??
                          registrationDate(answer.registeredAt)}
                      </time>
                      <span> · {answer.authorName ?? "가족 기록"}</span>
                      <QuestionAnswer
                        question={prompt.questionSnapshot}
                        answer={answer.body}
                      />
                    </Link>
                  ))
                ) : (
                  <p>
                    현재 남아 있는 답변이 없어요.
                    {canAnswer && " 이 질문에 다시 답할 수 있어요."}
                  </p>
                )}
                {canAnswer && (
                  <Link
                    className="text-link"
                    href={`/pets/${pet.id}/questions/answers/new?promptId=${encodeURIComponent(prompt.id)}`}
                  >
                    이 질문에 답변 남기기
                  </Link>
                )}
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
