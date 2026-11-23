"use client";
import { createId } from "../lib/id";
import { useEffect, useId, useRef, useState } from "react";
import type { MediaAttachment } from "@/features/workspace/domain/models";
import { Icon } from "./Icon";
import { Photo } from "./Photo";
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
}: {
  value: MediaAttachment[];
  onChange: (value: MediaAttachment[]) => void;
}) {
  const id = useId();
  const [error, setError] = useState("");
  const [recording, setRecording] = useState(false);
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
  async function select(files: FileList | null) {
    if (!files) return;
    setError("");
    try {
      if (value.length + files.length > 5)
        throw Error("기록 하나에 추가 첨부는 5개까지 가능해요.");
      const added: MediaAttachment[] = [];
      for (const file of Array.from(files)) {
        if (!/^(image\/(jpeg|png|webp)|audio\/)/.test(file.type))
          throw Error("사진 또는 음성 파일을 선택해 주세요.");
        if (file.size > 2 * 1024 * 1024)
          throw Error(
            "브라우저 데모에서는 첨부 파일 한 개가 2MB 이하여야 해요.",
          );
        added.push({
          id: createId(),
          name: file.name,
          mimeType: file.type,
          type: file.type.startsWith("image/") ? "PHOTO" : "VOICE",
          url: await read(file),
        });
      }
      onChange([...value, ...added]);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "첨부하지 못했어요.");
    }
  }
  async function startRecording() {
    setError("");
    try {
      if (value.length >= 5) throw Error("추가 첨부는 5개까지 가능해요.");
      if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder)
        throw Error(
          "이 브라우저는 녹음을 지원하지 않아요. 음성 파일로 첨부해 주세요.",
        );
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
        onChange([
          ...value,
          {
            id: createId(),
            name: "우리 아이의 목소리.webm",
            mimeType: next.mimeType,
            type: "VOICE",
            url: await read(blob),
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
  return (
    <section className="attachments">
      <div className="attachments-heading">
        <strong>사진 · 목소리</strong>
        <span>선택</span>
      </div>
      <div className="attachments-actions">
        <label htmlFor={id} className="button secondary">
          <Icon name="plus" size={15} />
          파일 추가
        </label>
        <input
          className="visually-hidden"
          id={id}
          type="file"
          disabled={recording}
          multiple
          accept="image/jpeg,image/png,image/webp,audio/*"
          onChange={(event) => {
            void select(event.target.files);
            event.target.value = "";
          }}
        />
        <button
          type="button"
          className={`button ${recording ? "destructive" : "secondary"}`}
          onClick={() =>
            recording ? recorder.current?.stop() : void startRecording()
          }
        >
          <Icon name="mic" size={16} />
          {recording ? "녹음 끝내기" : "목소리 녹음"}
        </button>
      </div>
      {recording && (
        <p role="status">녹음 중이에요. 최대 30초 뒤 자동으로 마칩니다.</p>
      )}
      <small>
        추가 첨부 최대 5개 · 개별 2MB · 미디어는 자동 재생되지 않아요.
      </small>
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      <div className="attachments-list">
        {value.map((file) => (
          <div key={file.id}>
            <MediaPlayer file={file} />
            <button
              type="button"
              className="text-link danger"
              disabled={recording}
              onClick={() =>
                onChange(value.filter((item) => item.id !== file.id))
              }
            >
              첨부 삭제
            </button>
          </div>
        ))}
      </div>
    </section>
  );
}
