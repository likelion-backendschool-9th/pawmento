"use client";
import { PetDescription } from "@/features/pet/components/PetDescription";
import { MediaAttachments, MediaPlayer } from "@/components/MediaAttachments";
import type { MediaAttachment } from "../../../types/MediaAttachment";
import { QuestionAnswer } from "@/components/QuestionAnswer";
import { useCurrentQuestion } from "../../question/hooks/useCurrentQuestion";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { useRef } from "react";
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/components/Icon";
import { Photo } from "@/components/Photo";
import { LocationField } from "@/features/map/components/LocationField";
import type { PickedLocation } from "@/features/map/types/PickedLocation";
import { today } from "@/lib/date";
import { formatDate } from "@/lib/date";
import type { JournalItem } from "../types/JournalItem";
import type { Pet } from "../../pet/types/Pet";
import styles from "../../shell/components/Workspace.module.css";
import { MemoryDateField } from "./MemoryDateField";
import { isCalendarDate } from "../utils/dateSelection";
import editorStyles from "./JournalEditor.module.css";
import { PageHeading } from "../../shell/components/WorkspaceUi";
import { useUnsavedChanges } from "../../shell/hooks/useUnsavedChanges";
import { messageOf } from "../../shell/utils/presentation";

export function JournalForm({
  pet,
  record,
  questionAnswer,
}: {
  pet: Pet;
  record?: JournalItem;
  questionAnswer: boolean;
}) {
  const app = useApp();
  const router = useRouter();
  const search = useSearchParams();
  const requestedPrompt = app.state.questionPrompts.find(
    (p) => p.petId === pet.id && p.id === search.get("promptId"),
  );
  const prompt = useCurrentQuestion(
    pet.id,
    questionAnswer && !record && !requestedPrompt,
  );
  const promptId =
    record?.type === "QUESTION_ANSWER"
      ? record.promptId
      : (requestedPrompt?.id ?? prompt.id);
  const questionText =
    record?.question ??
    requestedPrompt?.questionSnapshot ??
    prompt.text;
  const [title, setTitle] = useState(
    record?.kind === "DIARY" ? (record.title ?? "") : "",
  );
  const [body, setBody] = useState(record?.body ?? "");
  const [date, setDate] = useState(
    record?.recordedDate ??
      (questionAnswer ? today() : (search.get("date") ?? today())),
  );
  const [photo, setPhoto] = useState(record?.photoUrl ?? "");
  const [attachments, setAttachments] = useState<MediaAttachment[]>(
    record?.attachments ?? [],
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);
  // 지도에서 고른 위치. 서버 일기와 연결되면 저장할 때 함께 보낸다(지금 데모 저장소에는 위치가 없다)
  const [location, setLocation] = useState<PickedLocation | null>(null);
  const dirty =
    (!questionAnswer &&
      title !== (record?.kind === "DIARY" ? (record.title ?? "") : "")) ||
    JSON.stringify(attachments) !== JSON.stringify(record?.attachments ?? []) ||
    body !== (record?.body ?? "") ||
    photo !== (record?.photoUrl ?? "") ||
    date !==
      (record?.recordedDate ??
        (questionAnswer ? today() : (search.get("date") ?? today())));
  useUnsavedChanges(dirty && !saved);
  async function save(event: React.FormEvent) {
    event.preventDefault();
    if (busy || (questionAnswer && !questionText)) return;
    // 날짜 칸은 버튼이라 브라우저의 required가 막지 못한다. ?date=엉터리처럼 들어온 값은 저장 전에 알린다
    if (!questionAnswer && !isCalendarDate(date)) {
      setError("기억하고 싶은 날짜를 선택해 주세요.");
      return;
    }
    if (record?.legacy && record.attachments?.some((a) => a.type === "VIDEO")) {
      setError("영상이 포함된 이전 데모 기록은 읽기만 가능해요.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const content = {
        petId: pet.id,
        body,
        photoUrl: photo,
        attachments,
        authorName: app.account!.nickname,
      };
      const item = questionAnswer
        ? await app.dependencies.workspace.questions.save(
            app.account!.id,
            { ...content, promptId },
            record?.id,
          )
        : await app.dependencies.workspace.diary.save(
            app.account!.id,
            { ...content, title, recordedDate: date },
            record?.id,
          );
      setSaved(true);
      await app.refresh();
      app.notify(record ? "이야기를 수정했어요." : "소중한 오늘을 기록했어요.");
      if (!record) {
        setTitle("");
        setBody("");
        setPhoto("");
        setAttachments([]);
        setDate(questionAnswer ? today() : (search.get("date") ?? today()));
      }
      router.push(
        `/pets/${pet.id}/${questionAnswer ? "questions/answers" : "diary"}/${item.id}`,
      );
    } catch (cause) {
      setError(messageOf(cause));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <Link
        className="text-link"
        href={
          record
            ? `/pets/${pet.id}/${questionAnswer ? "questions/answers" : "diary"}/${record.id}`
            : `/pets/${pet.id}/${questionAnswer ? "questions" : "diary"}`
        }
      >
        ←{" "}
        {record
          ? "기록으로 돌아가기"
          : questionAnswer
            ? "오늘의 질문"
            : "다이어리"}
      </Link>
      <PageHeading
        eyebrow={questionAnswer ? "오늘의 작은 기억" : undefined}
        title={
          record
            ? "우리의 이야기 다듬기"
            : questionAnswer
              ? "오늘의 질문에 답해요"
              : "다이어리 쓰기"
        }
        body="완벽한 문장보다 지금의 마음이면 충분해요."
      />
      <form
        onSubmit={save}
        className={`${styles.editor} ${styles.diaryEditor} ${editorStyles.form}`}
      >
        {questionAnswer && (
          <header className={styles.editorQuestion}>
            <p>
              <Icon name="calendar" size={20} />
              오늘의 질문
              <span /> {formatDate(date)}
            </p>
            <h2>{questionText || "오늘의 질문을 준비하고 있어요…"}</h2>
          </header>
        )}
        <section className={styles.editorFields}>
          {questionAnswer && <span className="badge"><Icon name="book" size={14} />질문 답변</span>}

          {!questionAnswer && (
            <div className={editorStyles.titleField}>
            <label htmlFor="diary-title">
              <span>제목 <span className={editorStyles.required}>(필수)</span></span>
              <input
                id="diary-title"
                required
                aria-describedby="diary-title-count"
                maxLength={200}
                value={title}
                placeholder="오늘의 기억에 이름을 붙여 주세요"
                onChange={(event) => {
                  setSaved(false);
                  setTitle(event.target.value);
                }}
              />
            </label>
            <small id="diary-title-count">{[...title].length} / 100자</small>
            </div>
          )}
          {questionAnswer ? (
            <label>
              답변 등록일
              <input type="date" required value={date} readOnly max="9999-12-31" />
              <small>답변을 저장한 시각을 등록일로 기록해요. 수정해도 최초 등록일은 유지돼요.</small>
            </label>
          ) : (
            <MemoryDateField value={date} mode={pet.mode} onChange={(next) => { setSaved(false); setError(""); setDate(next); }} />
          )}
          {!questionAnswer && (
            <LocationField
              mode={pet.mode}
              value={location}
              onChange={setLocation}
            />
          )}
          <label htmlFor="memory-body">
            {questionAnswer ? "오늘의 답변" : "우리의 이야기"}
          </label>
          <textarea
            id="memory-body"
            value={body}
            onChange={(event) => {
              setSaved(false);
              setBody(event.target.value);
            }}
            rows={8}
            maxLength={3000}
            required={questionAnswer}
            placeholder={`${pet.name}의 하루와 지금의 마음을 들려주세요.`}
          />
          {questionAnswer && prompt.error && (
            <p className="error" role="alert">
              {prompt.error}
              <button
                type="button"
                className="text-link"
                onClick={prompt.retry}
              >
                다시 시도
              </button>
            </p>
          )}
          <span className={styles.characterCount}>
            {body.length.toLocaleString()} / 3,000
          </span>
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          <MediaAttachments
            photo={photo}
            onPhotoChange={(value) => { setSaved(false); setPhoto(value); }}
            value={attachments}
            onChange={(value) => {
              setSaved(false);
              setAttachments(value);
            }}
          />
          <div className={styles.editorActions}>
            <Link
              className="button secondary"
              href={
                record
                  ? `/pets/${pet.id}/${questionAnswer ? "questions/answers" : "diary"}/${record.id}`
                  : `/pets/${pet.id}/${questionAnswer ? "questions" : "diary"}`
              }
            >
              취소
            </Link>
            <button
              className="button primary"
              disabled={busy || (questionAnswer && !questionText)}
            >
              <Icon name="check" size={17} />
              {busy
                ? "저장하는 중…"
                : record
                  ? "수정한 이야기 저장"
                  : "이 순간 저장하기"}
            </button>
          </div>

          <p className={styles.localNote}>
            데모 기록과 사진은 현재 브라우저에 저장돼요.
          </p>
        </section>
      </form>
    </>
  );
}

export function JournalDetail({
  pet,
  record,
}: {
  pet: Pet;
  record: JournalItem;
}) {
  const app = useApp();
  const router = useRouter();
  const dialog = useRef<HTMLDialogElement>(null);
  const viewer = useRef<HTMLDialogElement>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const url = `/pets/${pet.id}/${record.type === "QUESTION_ANSWER" ? "questions/answers" : "diary"}/${record.id}`;
  async function remove() {
    setBusy(true);
    setError("");
    try {
      const domain =
        record.type === "QUESTION_ANSWER"
          ? app.dependencies.workspace.questions
          : app.dependencies.workspace.diary;
      await domain.remove(app.account!.id, pet.id, record.id);
      await app.refresh();
      dialog.current?.close();
      app.notify("기록을 삭제했어요.");
      router.push(
        `/pets/${pet.id}/${record.type === "QUESTION_ANSWER" ? "questions" : "diary"}`,
      );
    } catch (cause) {
      setError(messageOf(cause));
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className={styles.detailWrap}>
      <Link
        className="text-link"
        href={`/pets/${pet.id}/${record.type === "QUESTION_ANSWER" ? "questions" : "diary"}`}
      >
        ← {record.type === "QUESTION_ANSWER" ? "오늘의 질문" : "다이어리"}
      </Link>
      <header className={styles.detailHeading}>
        <span className="badge">
          <Icon
            name={record.type === "QUESTION_ANSWER" ? "book" : "camera"}
            size={14}
          />
          {record.type === "QUESTION_ANSWER" ? "질문과 답변" : "다이어리"}
        </span>
        <h1>
          {record.kind === "DIARY"
            ? record.title || "제목 없는 일기"
            : "질문과 함께 남긴 답변"}
        </h1>
        <p>
          {formatDate(record.recordedDate, {
            year: "numeric",
            month: "long",
            day: "numeric",
          })}
          {record.authorName ? ` · ${record.authorName}` : ""}
        </p>
        <p><PetDescription name={pet.name} suffix="와 함께한">소중한 하루의 기록이에요.</PetDescription></p>
      </header>
      <article className={styles.detail}>
        {record.photoUrl && (
          <button
            className={styles.detailPhoto}
            aria-label="사진 크게 보기"
            onClick={() => viewer.current?.showModal()}
          >
            <Photo src={record.photoUrl} alt={`${pet.name}와 함께한 순간`} />
            <span>
              <Icon name="camera" size={15} />
              사진 크게 보기
            </span>
          </button>
        )}
        <div className={styles.detailBody}>
          {record.type === "QUESTION_ANSWER" ? (
            <QuestionAnswer question={record.question} answer={record.body} />
          ) : (
            <p>{record.body || "사진 한 장에 담아둔, 우리의 하루."}</p>
          )}
          {record.attachments?.map((file) => (
            <MediaPlayer file={file} key={file.id} />
          ))}
          <div className={styles.detailMeta}>
            <span>
              작성자{" "}
              {record.authorName ??
                (record.authorId === app.account!.id
                  ? app.account!.nickname
                  : "가족 기록")}
            </span>
            <span>
              {record.type === "QUESTION_ANSWER" ? "답변 등록일" : "기억한 날"}{" "}
              {record.recordedDate}
              {record.legacy && record.type === "QUESTION_ANSWER"
                ? " · 이전 데모의 선택 날짜"
                : ""}
            </span>
            <span>
              작성{" "}
              {new Date(record.createdAt).toLocaleString("ko-KR", {
                timeZone: "Asia/Seoul",
              })}
            </span>
            {record.updatedAt !== record.createdAt && (
              <span>
                수정{" "}
                {new Date(record.updatedAt).toLocaleString("ko-KR", {
                  timeZone: "Asia/Seoul",
                })}
              </span>
            )}
          </div>
        </div>
      </article>
      {pet.accessRole !== "VIEWER" && record.authorId === app.account?.id && (
        <div className={styles.detailActions}>
          <button
            className="text-link danger"
            onClick={() => dialog.current?.showModal()}
          >
            기록 삭제
          </button>
          {!(
            record.legacy &&
            (record.type === "VIDEO" ||
              record.attachments?.some((a) => a.type === "VIDEO"))
          ) && (
            <Link className="button secondary" href={`${url}/edit`}>
              <Icon name="edit" size={17} />
              이야기 수정하기
            </Link>
          )}
        </div>
      )}
      <dialog ref={dialog} className="dialog">
        <h2>이 기록을 삭제할까요?</h2>
        <p>
          사진과 이야기가 브라우저 저장소에서 삭제돼요.
          <br />
          삭제한 기록은 되돌릴 수 없어요.
        </p>
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <div className="dialog-actions">
          <button
            autoFocus
            className="button secondary"
            disabled={busy}
            onClick={() => dialog.current?.close()}
          >
            간직할게요
          </button>
          <button
            className="button destructive"
            disabled={busy}
            onClick={() => void remove()}
          >
            {busy ? "삭제하는 중…" : "삭제하기"}
          </button>
        </div>
      </dialog>
      <dialog ref={viewer} className="dialog photo-viewer">
        <button
          className="icon-button"
          autoFocus
          aria-label="사진 닫기"
          onClick={() => viewer.current?.close()}
        >
          <Icon name="close" />
        </button>
        <Photo src={record.photoUrl} alt={`${pet.name}의 사진 크게 보기`} />
      </dialog>
    </div>
  );
}
