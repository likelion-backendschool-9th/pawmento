"use client";
import { MediaAttachments, MediaPlayer } from "@/shared/ui/MediaAttachments";
import type { MediaAttachment } from "../../domain/models";
import { QuestionAnswer } from "@/shared/ui/QuestionAnswer";
import { useTodayQuestion } from "./useTodayQuestion";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { useRef } from "react";
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/shared/ui/Icon";
import { Photo } from "@/shared/ui/Photo";
import { PhotoPicker } from "@/shared/ui/PhotoPicker";
import { today } from "@/shared/date";
import { formatDate } from "@/shared/date";
import type { Memory } from "../../domain/models";
import type { Pet } from "../../domain/models";
import styles from "./Workspace.module.css";
import { PageHeading } from "./WorkspaceUi";
import { useUnsavedChanges } from "./useUnsavedChanges";
import { question, messageOf } from "./presentation";

export function RecordForm({
  pet,
  record,
  diary,
}: {
  pet: Pet;
  record?: Memory;
  diary: boolean;
}) {
  const app = useApp();
  const router = useRouter();
  const search = useSearchParams();
  const prompt = useTodayQuestion(pet.id, record?.recordedDate ?? today());
  const questionText =
    record?.question ?? (prompt.text ? question(pet.name, prompt.text) : "");
  const [body, setBody] = useState(record?.body ?? "");
  const [date, setDate] = useState(
    record?.recordedDate ?? (diary ? today() : (search.get("date") ?? today())),
  );
  const [photo, setPhoto] = useState(record?.photoUrl ?? "");
  const [attachments, setAttachments] = useState<MediaAttachment[]>(
    record?.attachments ?? [],
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);
  const dirty =
    JSON.stringify(attachments) !== JSON.stringify(record?.attachments ?? []) ||
    body !== (record?.body ?? "") ||
    photo !== (record?.photoUrl ?? "") ||
    date !==
      (record?.recordedDate ??
        (diary ? today() : (search.get("date") ?? today())));
  useUnsavedChanges(dirty && !saved);
  async function save(event: React.FormEvent) {
    event.preventDefault();
    if (busy || (diary && !questionText)) return;
    setBusy(true);
    setError("");
    try {
      const memory = await app.dependencies.workspace.saveMemory(
        app.account!.id,
        {
          petId: pet.id,
          type: diary
            ? "DIARY"
            : attachments.some((file) => file.type === "VIDEO")
              ? "VIDEO"
              : attachments.some((file) => file.type === "VOICE")
                ? "VOICE"
                : photo
                  ? "PHOTO"
                  : "TEXT",
          attachments,
          authorName: app.account!.nickname,
          recordedDate: date,
          body,
          photoUrl: photo,
          question: diary ? questionText : "",
          questionId: diary ? (record?.questionId ?? prompt.id) : undefined,
        },
        record?.id,
      );
      setSaved(true);
      await app.refresh();
      app.notify(record ? "이야기를 수정했어요." : "소중한 오늘을 기록했어요.");
      if (!record) {
        setBody("");
        setPhoto("");
        setAttachments([]);
        setDate(diary ? today() : (search.get("date") ?? today()));
      }
      router.push(
        `/pets/${pet.id}/${diary ? "diary" : "records"}/${memory.id}`,
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
            ? `/pets/${pet.id}/${diary ? "diary" : "records"}/${record.id}`
            : "/home"
        }
      >
        ← {record ? "기록으로 돌아가기" : "오늘의 이야기"}
      </Link>
      <PageHeading
        eyebrow={diary ? "오늘의 작은 기억" : "마음에 남은 순간"}
        title={
          record
            ? "우리의 이야기 다듬기"
            : diary
              ? "오늘의 이야기를 남겨요"
              : "마음 가는 대로, 기록해요"
        }
        body="완벽한 문장보다 지금의 마음이면 충분해요."
      />
      <form
        onSubmit={save}
        className={`${styles.editor} ${styles.diaryEditor}`}
      >
        {diary && (
          <header className={styles.editorQuestion}>
            <p>
              <Icon name="calendar" size={20} />
              오늘의 질문
              <span /> {formatDate(date)}
            </p>
            <h2>{questionText || "오늘의 질문을 준비하고 있어요…"}</h2>
          </header>
        )}
        <section className={styles.editorPhoto}>
          <PhotoPicker
            value={photo}
            onChange={(value) => {
              setSaved(false);
              setPhoto(value);
            }}
          />
        </section>
        <section className={styles.editorFields}>
          <span className="badge">
            <Icon name={diary ? "book" : "edit"} size={14} />
            {diary ? "질문 일기" : "자유 기록"}
          </span>

          <label>
            기억하고 싶은 날짜
            <input
              type="date"
              required
              value={date}
              onChange={(event) => {
                setSaved(false);
                setDate(event.target.value);
              }}
              readOnly={diary}
              max="9999-12-31"
            />
            {diary && <small>오늘의 질문은 오늘 날짜로 남겨요.</small>}
          </label>
          <label htmlFor="memory-body">
            {diary ? "오늘의 답변" : "우리의 이야기"}
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
            required={diary}
            placeholder={`${pet.name}와 함께한 순간, 지금의 마음을 들려주세요.`}
          />
          {diary && prompt.error && (
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
            value={attachments}
            onChange={(value) => {
              setSaved(false);
              setAttachments(value);
            }}
          />
          <div className={styles.editorActions}>
            <Link
              className="button secondary"
              href={record ? `/pets/${pet.id}/records/${record.id}` : "/home"}
            >
              취소
            </Link>
            <button
              className="button primary"
              disabled={busy || (diary && !questionText)}
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

export function RecordDetail({ pet, record }: { pet: Pet; record: Memory }) {
  const app = useApp();
  const router = useRouter();
  const dialog = useRef<HTMLDialogElement>(null);
  const viewer = useRef<HTMLDialogElement>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const url = `/pets/${pet.id}/${record.type === "DIARY" ? "diary" : "records"}/${record.id}`;
  async function remove() {
    setBusy(true);
    setError("");
    try {
      await app.dependencies.workspace.deleteMemory(
        app.account!.id,
        pet.id,
        record.id,
      );
      await app.refresh();
      dialog.current?.close();
      app.notify("기록을 삭제했어요.");
      router.push(`/pets/${pet.id}/timeline`);
    } catch (cause) {
      setError(messageOf(cause));
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className={styles.detailWrap}>
      <Link className="text-link" href={`/pets/${pet.id}/timeline`}>
        ← 우리의 기록
      </Link>
      <header className={styles.detailHeading}>
        <span className="badge">
          <Icon name={record.type === "DIARY" ? "book" : "camera"} size={14} />
          {record.type === "DIARY" ? "질문 일기" : "자유 기록"}
        </span>
        <h1>
          {formatDate(record.recordedDate, {
            year: "numeric",
            month: "long",
            day: "numeric",
          })}
          <br />
          우리의 이야기
        </h1>
        <p>{pet.name}와 함께한 소중한 하루</p>
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
          {record.type === "DIARY" ? (
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
            <span>기억한 날 {record.recordedDate}</span>
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
      {pet.accessRole !== "VIEWER" && (
        <div className={styles.detailActions}>
          <button
            className="text-link danger"
            onClick={() => dialog.current?.showModal()}
          >
            기록 삭제
          </button>
          <Link className="button secondary" href={`${url}/edit`}>
            <Icon name="edit" size={17} />
            이야기 수정하기
          </Link>
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
