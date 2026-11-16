"use client";
import { useId, useState } from "react";
import { Photo } from "./Photo";
import { Icon } from "./Icon";
export function PhotoPicker({
  value,
  onChange,
}: {
  value: string;
  onChange: (value: string) => void;
}) {
  const id = useId();
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
    <div className="photo-picker">
      {value ? (
        <Photo key={value} src={value} alt="선택한 사진 미리보기" />
      ) : (
        <div className="upload-placeholder">
          <Icon name="camera" size={34} />
          <strong>이 순간을 함께 담아볼까요?</strong>
          <span>사진 없이 글만 남겨도 괜찮아요.</span>
        </div>
      )}
      <label htmlFor={id} className="button secondary">
        <Icon name="plus" size={16} />
        {loading
          ? "사진을 확인하는 중…"
          : value
            ? "사진 바꾸기"
            : "사진 선택하기"}
      </label>
      <input
        className="visually-hidden"
        id={id}
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
      <small>JPG, PNG, WebP · 최대 1MB · 브라우저에 저장</small>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
