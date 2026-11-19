"use client";
import { journalItems } from "@/workspace/domain/models";

import Link from "next/link";
import { useState } from "react";
import { useApp } from "@/app/AppProvider";
import { today } from "@/shared/date";
import { Icon } from "@/shared/ui/Icon";
import { Photo } from "@/shared/ui/Photo";
import { QuestionAnswer } from "@/shared/ui/QuestionAnswer";
import type { Pet } from "../../domain/models";
import { JournalCard } from "./WorkspaceUi";
import { useCurrentQuestion } from "./useCurrentQuestion";
import { question } from "./presentation";
import { LifeEmpty, lifeStyles as s } from "@/life/adapters/inbound/LifeUi";
import g from "./HomeScreen.module.css";

export function HomeScreen({ pet }: { pet: Pet }) {
  const app = useApp();
  const [randomIndex, setRandomIndex] = useState(0);
  const date = today();
  const prompt = useCurrentQuestion(pet.id);
  const records = journalItems(app.state)
    .filter(
      (record) => record.petId === pet.id && record.type !== "QUESTION_ANSWER",
    )
    .sort((a, b) => b.recordedDate.localeCompare(a.recordedDate));
  const answer = journalItems(app.state).find(
    (record) =>
      record.petId === pet.id &&
      record.type === "QUESTION_ANSWER" &&
      record.promptId === prompt.id &&
      record.authorId === app.account?.id,
  );
  const displayQuestion =
    answer?.question ??
    (prompt.text
      ? question(pet.name, prompt.text)
      : "오늘의 질문을 준비하고 있어요…");
  const garden = pet.mode === "GARDEN";
  const photos = records.filter((record) => record.photoUrl);
  const onThisDay = records.filter(
    (record) =>
      record.recordedDate.slice(5) === date.slice(5) &&
      record.recordedDate.slice(0, 4) < date.slice(0, 4),
  );
  return (
    <>
      <section className={g.hero}>
        <div className={g.heroCopy}>
          <span>OUR DAYS, TOGETHER</span>
          <h1>{pet.name}의 하루</h1>
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
      {photos.length > 0 && (
        <section className={g.gallery}>
          <div className={g.galleryHeading}>
            <div>
              <span>OUR LITTLE EXHIBITION</span>
              <h2>사진 속에 머문, 우리의 계절</h2>
            </div>
            <Link href={`/pets/${pet.id}/diary`}>
              일기 펼쳐보기 <Icon name="chevron" size={14} />
            </Link>
          </div>
          <div className={g.galleryGrid}>
            {photos.slice(0, 3).map((record) => (
              <Link
                className={g.photoPrint}
                key={record.id}
                href={`/pets/${pet.id}/${record.type === "QUESTION_ANSWER" ? "questions/answers" : "diary"}/${record.id}`}
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
      {garden && (
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
      )}
      {onThisDay.length > 0 && (
        <section className={s.section}>
          <h2>지난 오늘, 우리가 남긴 순간</h2>
          <div className={s.threeGrid}>
            {onThisDay.slice(0, 3).map((record) => (
              <JournalCard key={record.id} record={record} />
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
            <JournalCard record={records[randomIndex % records.length]} />
          ) : (
            <LifeEmpty
              title="아직 쌓인 기억이 없어요"
              body="첫 기억부터 천천히 담아보세요."
              href={`/pets/${pet.id}/diary/new`}
              action="기억 남기기"
            />
          )}
        </section>
        <section className={s.panel}>
          <h2>오늘의 질문</h2>
          {prompt.error ? (
            <p className="error" role="alert">
              {prompt.error}
              <button className="text-link" onClick={prompt.retry}>
                다시 시도
              </button>
            </p>
          ) : (
            <QuestionAnswer
              question={displayQuestion}
              answer={answer?.body ?? "아직 답변을 남기지 않았어요."}
            />
          )}
          <div className={g.questionActions}>
            <Link
              className="button primary"
              href={
                answer
                  ? `/pets/${pet.id}/questions/answers/${answer.id}`
                  : pet.accessRole === "VIEWER"
                    ? `/pets/${pet.id}/questions`
                    : `/pets/${pet.id}/questions/answers/new`
              }
            >
              {answer
                ? "오늘의 답변 보기"
                : pet.accessRole === "VIEWER"
                  ? "질문 기록 보기"
                  : "오늘의 질문 답하기"}
            </Link>
            <Link className="text-link" href={`/pets/${pet.id}/questions`}>
              지난 질문과 답변 <Icon name="arrow" size={15} />
            </Link>
          </div>
        </section>
      </div>
    </>
  );
}
