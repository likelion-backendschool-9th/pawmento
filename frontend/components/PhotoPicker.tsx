"use client";
import { useId, useRef, useState } from "react";
import { Photo } from "./Photo";
import { Icon } from "./Icon";
import s from "./PhotoPicker.module.css";
export function PhotoPicker({
  value,
  onChange,
}: {
  value: string;
  onChange: (value: string) => void;
}) {
  const id = useId();
  const input = useRef<HTMLInputElement>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  async function select(file?: File) {
    if (!file) return;
    setError("");
    if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
      setError("JPG, PNG, WebP 사진을 선택해 주세요.");
      return;
    }
    if (file.size > 1024 * 1024) {
      setError("데모에서는 1MB 이하 사진을 선택해 주세요.");
      return;
    }
    setLoading(true);
    try {
      const data = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.onerror = () => reject(Error("사진을 읽지 못했어요."));
        reader.readAsDataURL(file);
      });
      await new Promise<void>((resolve, reject) => {
        const image = new window.Image();
        image.onload = () => resolve();
        image.onerror = () =>
          reject(Error("손상된 사진이에요. 다른 사진을 선택해 주세요."));
        image.src = data;
      });
      onChange(data);
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : "사진을 읽지 못했어요.",
      );
    } finally {
      setLoading(false);
    }
  }
  return (
    <div className={`photo-picker ${s.picker}`}>
      <button type="button" className={s.surface} disabled={loading}
        aria-label={value ? "사진 바꾸기" : "사진 추가"}
        aria-describedby={`${id}-hint`}
        onClick={() => input.current?.click()}>
        {value ? <>
          <Photo key={value} src={value} alt="선택한 사진 미리보기" />
          <span className={s.caption}><Icon name="camera" size={17} />{loading ? "사진 확인 중…" : "사진 바꾸기"}</span>
        </> : <span className={s.placeholder}>
          <Icon name="camera" size={28} />
          <strong>{loading ? "사진 확인 중…" : "사진 추가"}</strong>
          <span>이곳을 눌러 사진을 선택해 주세요.</span>
        </span>}
      </button>
      <input
        className="visually-hidden"
        id={id}
        ref={input}
        tabIndex={-1}
        aria-label="대표 사진 파일"
        type="file"
        accept="image/jpeg,image/png,image/webp"
        disabled={loading}
        onChange={(event) => {
          void select(event.target.files?.[0]);
          event.target.value = "";
        }}
      />
      {value && (
        <button
          type="button"
          className="text-link"
          onClick={() => onChange("")}
        >
          사진 빼기
        </button>
      )}
      <small id={`${id}-hint`}>JPG · PNG · WebP / 최대 1MB</small>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
