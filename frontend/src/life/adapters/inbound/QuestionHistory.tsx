"use client";
import { journalItems } from "@/workspace/domain/models";

import Link from "next/link";
import { useState } from "react";
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/shared/ui/Icon";
import { QuestionAnswer } from "@/shared/ui/QuestionAnswer";
import type { Pet } from "@/workspace/domain/models";
import { LifeHeading, LifeEmpty, lifeStyles as s } from "./LifeUi";
export function QuestionHistory({ pet }: { pet: Pet }) {
  const app = useApp();
  const [adding, setAdding] = useState(false);
  const [demoError, setDemoError] = useState("");
  const records = journalItems(app.state)
    .filter(
      (record) => record.petId === pet.id && record.type === "QUESTION_ANSWER",
    )
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
      {records.length > 0 && <h2>지난 질문과 답변</h2>}
      {records.length ? (
        <div className={s.twoGrid}>
          {records.map((record) => (
            <article key={record.id} className={s.panel}>
              <Link
                href={`/pets/${pet.id}/questions/answers/${record.id}`}
                className="text-link"
              >
                <time className={s.meta}>{record.recordedDate}</time>
                {record.questionId?.startsWith("demo-question-") && (
                  <span className="badge">데모 기록</span>
                )}
                <QuestionAnswer
                  question={record.question ?? ""}
                  answer={record.body}
                />
              </Link>
              {record.type === "QUESTION_ANSWER" &&
                pet.accessRole !== "VIEWER" &&
                !app.state.questionAnswers.some(
                  (a) =>
                    a.promptId === record.promptId &&
                    a.authorId === app.account?.id,
                ) &&
                app.state.questionPrompts.some(
                  (p) => p.id === record.promptId && p.mode === pet.mode,
                ) && (
                  <Link
                    className="text-link"
                    href={`/pets/${pet.id}/questions/answers/new?promptId=${encodeURIComponent(record.promptId)}`}
                  >
                    이 질문에 나도 답하기
                  </Link>
                )}
            </article>
          ))}
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
