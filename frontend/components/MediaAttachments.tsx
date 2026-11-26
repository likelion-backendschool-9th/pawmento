"use client";
import { createId } from "../lib/id";
import { checkVoiceFile, planPhotoSelection } from "../lib/mediaAttachments";
import { useEffect, useId, useRef, useState } from "react";
import type { MediaAttachment } from "@/types/MediaAttachment";
import { RecordActionButton } from "./RecordAction";
import { Photo } from "./Photo";
import s from "./MediaAttachments.module.css";
import { PhotoAttachmentCarousel, type PhotoSlide } from "./PhotoAttachmentCarousel";
export function MediaPlayer({ file }: { file: MediaAttachment }) {
  return (
    <div className="media-player">
      {file.type === "PHOTO" ? (
        <Photo src={file.url} alt={file.name} />
      ) : file.type === "VIDEO" ? (
        <video
          src={file.url}
          controls
          preload="metadata"
          aria-label={file.name}
        />
      ) : (
        <audio
          src={file.url}
          controls
          preload="metadata"
          aria-label={file.name}
        />
      )}
      <small>{file.name}</small>
    </div>
  );
}
export function MediaAttachments({
  value,
  onChange,
  photo,
  onPhotoChange,
}: {
  value: MediaAttachment[];
  onChange: (value: MediaAttachment[]) => void;
  photo?: string;
  onPhotoChange?: (value: string) => void;
}) {
  const id = useId();
  const photoInput = useRef<HTMLInputElement>(null);
  const voiceInput = useRef<HTMLInputElement>(null);
  const [error, setError] = useState("");
  const [recording, setRecording] = useState(false);
  const [reading, setReading] = useState(false);
  // Async reads and recordings finish later; append to the latest value, not the one captured at start.
  const latest = useRef(value);
  useEffect(() => {
    latest.current = value;
  }, [value]);
  const busy = recording || reading;
  const recorder = useRef<MediaRecorder | null>(null);
  const stream = useRef<MediaStream | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const active = useRef(true);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      if (timer.current) clearTimeout(timer.current);
      if (recorder.current?.state === "recording") recorder.current.stop();
      stream.current?.getTracks().forEach((track) => track.stop());
    };
  }, []);
  async function read(file: File | Blob) {
    return new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result));
      reader.onerror = () => reject(Error("파일을 읽지 못했어요."));
      reader.readAsDataURL(file);
    });
  }
  async function loadPhoto(file: File) {
    const url = await read(file);
    await new Promise<void>((resolve, reject) => {
      const image = new window.Image();
      image.onload = () => resolve();
      image.onerror = () => reject(Error("손상된 사진이에요. 다른 사진을 선택해 주세요."));
      image.src = url;
    });
    return url;
  }
  async function select(files: FileList | null) {
    if (!files?.length) return;
    setError("");
    setReading(true);
    try {
      const selected = Array.from(files);
      const { primaryIndex } = planPhotoSelection(selected, {
        attachmentCount: value.length,
        hasPrimarySlot: Boolean(onPhotoChange),
        hasPrimaryPhoto: Boolean(photo),
      });
      const added: MediaAttachment[] = [];
      let nextPhoto: string | undefined;
      for (const [index, file] of selected.entries()) {
        const url = await loadPhoto(file);
        if (index === primaryIndex) { nextPhoto = url; continue; }
        added.push({ id: createId(), name: file.name, mimeType: file.type, type: "PHOTO", url });
      }
      if (!active.current) return;
      if (nextPhoto !== undefined) onPhotoChange?.(nextPhoto);
      onChange([...latest.current, ...added]);
    } catch (cause) {
      if (active.current) setError(cause instanceof Error ? cause.message : "첨부하지 못했어요.");
    } finally {
      if (active.current) setReading(false);
    }
  }
  async function selectVoice(files: FileList | null) {
    const file = files?.[0];
    if (!file) return;
    setError("");
    setReading(true);
    try {
      checkVoiceFile(file, value.length);
      const url = await read(file);
      if (!active.current) return;
      onChange([...latest.current, { id: createId(), name: file.name, mimeType: file.type, type: "VOICE", url }]);
    } catch (cause) {
      if (active.current) setError(cause instanceof Error ? cause.message : "첨부하지 못했어요.");
    } finally {
      if (active.current) setReading(false);
    }
  }
  async function startRecording() {
    setError("");
    try {
      if (value.length >= 5) throw Error("추가 첨부는 5개까지 가능해요.");
      // Without recording support (some in-app browsers), the same button chooses a voice file.
      if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) {
        voiceInput.current?.click();
        return;
      }
      stream.current = await navigator.mediaDevices.getUserMedia({
        audio: true,
      });
      const chunks: BlobPart[] = [];
      const next = new MediaRecorder(stream.current);
      recorder.current = next;
      next.ondataavailable = (event) => {
        if (event.data.size) chunks.push(event.data);
      };
      next.onstop = async () => {
        stream.current?.getTracks().forEach((track) => track.stop());
        if (timer.current) clearTimeout(timer.current);
        const blob = new Blob(chunks, { type: next.mimeType });
        if (!active.current) return;
        setRecording(false);
        if (blob.size > 2 * 1024 * 1024) {
          setError("녹음이 너무 커요. 짧게 다시 녹음해 주세요.");
          return;
        }
        const url = await read(blob);
        if (!active.current) return;
        onChange([
          ...latest.current,
          {
            id: createId(),
            name: "우리 아이의 목소리.webm",
            mimeType: next.mimeType,
            type: "VOICE",
            url,
          },
        ]);
      };
      next.start();
      setRecording(true);
      timer.current = setTimeout(() => {
        if (next.state === "recording") next.stop();
      }, 30000);
    } catch (cause) {
      stream.current?.getTracks().forEach((track) => track.stop());
      setError(
        cause instanceof Error ? cause.message : "마이크 권한을 확인해 주세요.",
      );
    }
  }
  const photos: PhotoSlide[] = [
    ...(photo ? [{ id: "primary", src: photo, label: "대표 사진", remove: () => onPhotoChange?.("") }] : []),
    ...value.filter(file => file.type === "PHOTO").map(file => ({
      id: `attachment-${file.id}`, src: file.url, label: file.name,
      remove: () => onChange(value.filter(item => item.id !== file.id)),
    })),
  ];
  const otherMedia = value.filter(file => file.type !== "PHOTO");
  return (
    <section className="attachments">
      <div className="attachments-actions">
        <RecordActionButton icon="camera" disabled={busy} onClick={() => photoInput.current?.click()}>사진 추가</RecordActionButton>
        <input className="visually-hidden" id={`${id}-photo`} ref={photoInput}
          type="file" tabIndex={-1} aria-label="추가 사진 파일" disabled={busy} multiple
          accept="image/jpeg,image/png,image/webp"
          onChange={event => { void select(event.target.files); event.target.value = ""; }} />
        <RecordActionButton icon="mic" danger={recording}
          disabled={reading}
          onClick={() =>
            recording ? recorder.current?.stop() : void startRecording()
          }
        >
          {recording ? "녹음 끝내기" : "목소리 추가"}
        </RecordActionButton>
        <input className="visually-hidden" ref={voiceInput} type="file" tabIndex={-1}
          aria-label="목소리 파일" disabled={busy} accept="audio/*"
          onChange={event => { void selectVoice(event.target.files); event.target.value = ""; }} />
      </div>
      {recording && (
        <p role="status">녹음 중이에요. 최대 30초 뒤 자동으로 마칩니다.</p>
      )}
      <small>
        {onPhotoChange ? "첫 사진 1MB · 추가 첨부 5개, 개별 2MB" : "첨부 최대 5개 · 개별 2MB"}
      </small>
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      {reading && <p role="status">첨부를 불러오고 있어요…</p>}
      {photos.length > 0 && <PhotoAttachmentCarousel photos={photos} disabled={busy} />}
      {otherMedia.length > 0 && <div className={`attachments-list ${s.previews}`}>
        {otherMedia.map((file) => (
          <div key={file.id}>
            <MediaPlayer file={file} />
            <button
              type="button"
              className="text-link danger"
              disabled={busy}
              onClick={() =>
                onChange(value.filter((item) => item.id !== file.id))
              }
            >
              첨부 삭제
            </button>
          </div>
        ))}
      </div>}
    </section>
  );
}
