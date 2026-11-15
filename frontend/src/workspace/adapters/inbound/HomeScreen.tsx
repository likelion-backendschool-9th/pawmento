"use client";
import Link from "next/link";
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/shared/ui/Icon";
import { QuestionAnswer } from "@/shared/ui/QuestionAnswer";
import { today, formatDate } from "@/shared/date";
import type { Pet } from "../../domain/models";
import { question } from "./presentation";
import { useTodayQuestion } from "./useTodayQuestion";
import { RecordCard, PetSummary, EmptyState } from "./WorkspaceUi";
import { Calendar } from "./ArchiveScreen";
import styles from "./HomeScreen.module.css";
export function HomeScreen({ pet }: { pet: Pet }) {
  const app = useApp();
  const date = today();
  const prompt = useTodayQuestion(pet.id, date);
  const records = app.state.records
    .filter((item) => item.petId === pet.id)
    .sort(
      (a, b) =>
        b.recordedDate.localeCompare(a.recordedDate) ||
        b.createdAt.localeCompare(a.createdAt),
    );
  const diary = records.find(
    (item) => item.type === "DIARY" && item.recordedDate === date,
  );
  const displayQuestion =
    diary?.question ??
    (prompt.text
      ? question(pet.name, prompt.text)
      : "오늘의 질문을 준비하고 있어요…");
  const viewer = pet.accessRole === "VIEWER";
  return (
    <div className={styles.dashboard}>
      <header className={styles.heading}>
        <div>
          <p>우리의 이야기가 쌓이는 공간</p>
          <h1 aria-label={`${pet.name}와 함께하는 오늘`}>
            {pet.name}의 오늘<span>✳</span>
          </h1>
          <span>평범한 하루도 기록하면, 오래 간직할 이야기가 돼요.</span>
        </div>
        <div className={styles.today}>
          <Icon name="calendar" size={17} />
          {formatDate(date)}
        </div>
      </header>
      <div className={styles.stats}>
        <div>
          <span>함께 남긴 기록</span>
          <strong>
            {records.length}
            <small>개의 이야기</small>
          </strong>
          <Icon name="book" size={19} />
        </div>
        <div>
          <span>사진과 미디어</span>
          <strong>
            {records.reduce(
              (count, record) =>
                count +
                (record.photoUrl ? 1 : 0) +
                (record.attachments?.length ?? 0),
              0,
            )}
            <small>개의 순간</small>
          </strong>
          <Icon name="camera" size={19} />
        </div>
        <div>
          <span>오늘의 질문 다이어리</span>
          <strong>
            {diary ? "작성 완료" : "아직 빈 페이지"}
            <small>
              {diary ? "질문과 답변을 간직했어요" : "작은 기억을 남겨보세요"}
            </small>
          </strong>
          <Icon name={diary ? "check" : "edit"} size={19} />
        </div>
      </div>
      <div className={styles.columns}>
        <div className={styles.primary}>
          <section className={styles.question}>
            <div className={styles.questionMeta}>
              <span>
                <Icon name="edit" size={17} />
                오늘의 질문
              </span>
              <small>
                {formatDate(date, { month: "numeric", day: "numeric" })}
              </small>
            </div>
            <h2>{displayQuestion}</h2>
            <p>
              질문과 나의 답변을 한 페이지에 담아, 그날의 마음까지 함께
              기억해요.
            </p>
            {prompt.error && (
              <p role="alert" className="error">
                {prompt.error}
                <button className="text-link" onClick={prompt.retry}>
                  다시 시도
                </button>
              </p>
            )}
            <Link
              className="button primary"
              href={
                diary
                  ? `/pets/${pet.id}/diary/${diary.id}`
                  : viewer
                    ? `/pets/${pet.id}/timeline`
                    : `/pets/${pet.id}/diary/new`
              }
            >
              <Icon name={diary ? "book" : "edit"} size={16} />
              {diary
                ? "저장한 질문과 답변 보기"
                : viewer
                  ? "공유된 기록 보기"
                  : "오늘의 답변 남기기"}
              <Icon name="arrow" size={16} />
            </Link>
            <span className={styles.questionNumber}>Q.</span>
          </section>
          {diary && (
            <section className={styles.savedPair}>
              <div className={styles.sectionHeading}>
                <h2>오늘 간직한 질문과 답변</h2>
                {!viewer && (
                  <Link
                    className="text-link"
                    href={`/pets/${pet.id}/diary/${diary.id}/edit`}
                  >
                    답변 수정
                    <Icon name="edit" size={14} />
                  </Link>
                )}
              </div>
              <QuestionAnswer question={diary.question} answer={diary.body} />
            </section>
          )}
          <section>
            <div className={styles.sectionHeading}>
              <div>
                <h2>최근 기록</h2>
                <p>다시 꺼내보고 싶은, 우리만의 순간들.</p>
              </div>
              <Link className="text-link" href={`/pets/${pet.id}/timeline`}>
                전체 보기
                <Icon name="arrow" size={15} />
              </Link>
            </div>
            {records.length ? (
              <div className={styles.recentGrid}>
                {records.slice(0, 3).map((record) => (
                  <RecordCard key={record.id} record={record} />
                ))}
              </div>
            ) : (
              <EmptyState
                title="우리의 첫 페이지"
                body="사진 한 장, 짧은 문장으로 첫 기록을 남겨보세요."
                href={`/pets/${pet.id}/records/new`}
                action="첫 기록 남기기"
              />
            )}
          </section>
          <div className={styles.quickLinks}>
            {[
              [
                "camera",
                "미디어 앨범",
                "사진·영상·목소리를 한곳에서",
                `/pets/${pet.id}/media`,
              ],
              [
                "users",
                "가족 공동 기록",
                "소중한 기억을 함께 남겨요",
                `/pets/${pet.id}/family`,
              ],
              [
                "leaf",
                "기억의 정원",
                "쌓인 기억을 천천히 꺼내봐요",
                `/pets/${pet.id}/garden`,
              ],
            ].map(([icon, title, body, href]) => (
              <Link key={title} href={href}>
                <Icon name={icon} size={23} />
                <strong>{title}</strong>
                <span>{body}</span>
                <Icon name="arrow" size={15} />
              </Link>
            ))}
          </div>
        </div>
        <aside className={styles.aside}>
          <PetSummary pet={pet} count={records.length} />
          <section className={styles.calendar}>
            <div className={styles.asideHeading}>
              <h2>기록 달력</h2>
              <Link
                className="icon-button"
                href={`/pets/${pet.id}/calendar`}
                aria-label="전체 달력 보기"
              >
                <Icon name="arrow" size={16} />
              </Link>
            </div>
            <Calendar pet={pet} compact />
          </section>
          <section className={styles.asideLinks}>
            <h2>나의 기록 공간</h2>
            {[
              ["question", "지난 질문과 답변", `/pets/${pet.id}/questions`],
              [
                "settings",
                "ACTIVE / GARDEN 설정",
                `/pets/${pet.id}/settings/mode`,
              ],
              [
                "plus",
                "질문 없이, 마음 가는 대로",
                `/pets/${pet.id}/records/new`,
              ],
            ].map(([icon, label, href]) => (
              <Link key={label} href={href}>
                <Icon name={icon} size={17} />
                <span>{label}</span>
                <Icon name="chevron" size={13} />
              </Link>
            ))}
          </section>
        </aside>
      </div>
    </div>
  );
}
