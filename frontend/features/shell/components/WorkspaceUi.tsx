"use client";
import Link from "next/link";
import { QuestionAnswer } from "@/components/QuestionAnswer";
import { Icon } from "@/components/Icon";
import { Photo } from "@/components/Photo";
import { formatDate } from "@/lib/date";
import type { JournalItem } from "../../diary/types/JournalItem";
import type { Pet } from "../../pet/types/Pet";
import styles from "./Workspace.module.css";
import { speciesName } from "../utils/presentation";

export function PetSummary({ pet, count }: { pet: Pet; count: number }) {
  return (
    <section className={styles.petSummary}>
      <Link
        className={styles.petEdit}
        href={`/pets/${pet.id}`}
        aria-label={`${pet.name} 프로필 수정`}
      >
        <Icon name="edit" size={17} />
      </Link>
      <Photo
        key={pet.photoUrl}
        src={pet.photoUrl}
        alt={`${pet.name} 프로필`}
        className={styles.petAvatar}
      />
      <h2>
        <strong>{pet.name}</strong>
        <span>
          {pet.accessRole === "VIEWER"
            ? "공유 · 열람"
            : pet.accessRole === "EDITOR"
              ? "공유 · 편집"
              : pet.mode === "GARDEN"
                ? "기억의 정원"
                : "함께하는 중"}
        </span>
      </h2>
      <p>
        {speciesName[pet.species]}
        {pet.breed ? ` · ${pet.breed}` : ""}
      </p>
      <div className={styles.petStats}>
        <div>
          <strong>{count}</strong>
          <span>쌓인 이야기</span>
        </div>
        <div>
          <strong>
            {pet.birthday
              ? formatDate(pet.birthday, {
                  month: "numeric",
                  day: "numeric",
                }).replace(/\.$/, "")
              : "—"}
          </strong>
          <span>생일</span>
        </div>
      </div>
    </section>
  );
}

export function JournalCard({ record }: { record: JournalItem }) {
  return (
    <Link
      className={styles.recordCard}
      href={`/pets/${record.petId}/${record.type === "QUESTION_ANSWER" ? "questions/answers" : "diary"}/${record.id}`}
    >
      <div className={styles.recordPhoto}>
        <Photo
          src={record.photoUrl}
          alt={record.body ? record.body.slice(0, 45) : "함께한 순간의 사진"}
        />
        <span className={styles.recordBadge}>
          <Icon
            name={
              record.type === "QUESTION_ANSWER"
                ? "book"
                : record.photoUrl
                  ? "camera"
                  : "edit"
            }
            size={12}
          />
          {record.type === "QUESTION_ANSWER"
            ? "질문 일기"
            : record.photoUrl
              ? "사진 기록"
              : "자유 기록"}
        </span>
      </div>
      <div className={styles.recordInfo}>
        <time dateTime={record.recordedDate}>
          {formatDate(record.recordedDate, {
            month: "long",
            day: "numeric",
            weekday: "short",
          })}
        </time>
        {record.type === "QUESTION_ANSWER" ? (
          <QuestionAnswer
            question={record.question}
            answer={record.body}
            compact
          />
        ) : (
          <p>{record.body || "사진으로 남긴 우리의 순간"}</p>
        )}
        <span>
          기억 펼쳐보기 <Icon name="arrow" size={13} />
        </span>
      </div>
    </Link>
  );
}

export function EmptyState({
  title,
  body,
  href,
  action,
}: {
  title: string;
  body: string;
  href?: string;
  action?: string;
}) {
  return (
    <section className={styles.empty}>
      <span>
        <Icon name="leaf" size={30} />
      </span>
      <h2>{title}</h2>
      <p>{body}</p>
      {href && (
        <Link href={href} className="button primary">
          {action}
          <Icon name="arrow" size={16} />
        </Link>
      )}
    </section>
  );
}

export function PageHeading({
  eyebrow,
  title,
  body,
  children,
}: {
  eyebrow: string;
  title: string;
  body: string;
  children?: React.ReactNode;
}) {
  return (
    <div className={styles.pageHeading}>
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        <p>{body}</p>
      </div>
      {children}
    </div>
  );
}
