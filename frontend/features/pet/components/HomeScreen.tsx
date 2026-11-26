"use client";
import { journalItems } from "@/features/diary/utils/journalItems";

import Link from "next/link";
import { RecordActionLink } from "@/components/RecordAction";
import { useState } from "react";
import { useApp } from "@/app/AppProvider";
import { today } from "@/lib/date";
import { Icon } from "@/components/Icon";
import { Photo } from "@/components/Photo";
import { QuestionAnswer } from "@/components/QuestionAnswer";
import type { Pet } from "../types/Pet";
import { JournalCard } from "../../shell/components/WorkspaceUi";
import { useCurrentQuestion } from "../../question/hooks/useCurrentQuestion";
import { LifeEmpty, lifeStyles as s } from "@/features/shell/components/LifeUi";
import g from "./HomeScreen.module.css";

import { petAge } from "../utils/petAge";
import { GardenSky } from "./GardenSky";

export function HomeScreen({ pet }: { pet: Pet }) {
  const app = useApp();
  if (pet.mode === "GARDEN")
    return <GardenSky key={`${app.account?.id}:${pet.id}`} pet={pet} />;
  return <DailyHome pet={pet} />;
}

function DailyHome({ pet }: { pet: Pet }) {
  const app = useApp();
  const [randomIndex, setRandomIndex] = useState(0);
  const date = today();
  const age = petAge(pet.birthday, date);
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
    (prompt.text || "오늘의 질문을 준비하고 있어요…");
  const photos = records.filter((record) => record.photoUrl);
  const onThisDay = records.filter(
    (record) =>
      record.recordedDate.slice(5) === date.slice(5) &&
      record.recordedDate.slice(0, 4) < date.slice(0, 4),
  );
  return (
    <>
      <div>
        <section className={g.welcomeCard}>
          <div className={g.welcomeIdentity}>
            <div className={g.welcomePortrait}>
              <Photo src={pet.photoUrl} alt={`${pet.name}의 프로필 사진`} />
            </div>
            <div className={g.welcomeHeading}>
              <h1 className={g.welcomeName} title={pet.name}>{pet.name}</h1>
              <div className={g.welcomeFacts} aria-label="나이와 생일">
                {age ? <>
                  <span aria-label={`나이 ${age}`}>{age}</span>
                  <span aria-hidden="true">/</span>
                  <time dateTime={pet.birthday} aria-label={`생일 ${pet.birthday}`}>{pet.birthday.replaceAll("-", ".")}</time>
                </> : <span>나이·생일 미등록</span>}
              </div>
            </div>
          </div>
          <div className={g.welcomeCount}>
            <Icon name="paw" size={15} />
            <span>함께 남긴 <strong>{records.length}</strong>개의 기억</span>
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
                  <p>
                    {record.kind === "DIARY"
                      ? record.title || "제목 없는 일기"
                      : record.question}
                  </p>
                </Link>
              ))}
            </div>
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
                actionIcon="edit"
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
              <RecordActionLink full
                href={
                  answer
                    ? `/pets/${pet.id}/questions/answers/${answer.id}`
                    : pet.accessRole === "VIEWER"
                      ? `/pets/${pet.id}/questions`
                      : `/pets/${pet.id}/questions`
                }
              >
                {answer
                  ? "오늘의 답변 보기"
                  : pet.accessRole === "VIEWER"
                    ? "질문 기록 보기"
                    : "오늘의 질문 답하기"}
              </RecordActionLink>
              <Link className="text-link" href={`/pets/${pet.id}/questions/history`}>
                지난 질문과 답변 <Icon name="arrow" size={15} />
              </Link>
            </div>
          </section>
        </div>
      </div>
    </>
  );
}
