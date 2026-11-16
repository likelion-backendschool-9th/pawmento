"use client";
import Link from "next/link";
import { useState } from "react";
import { useApp } from "@/app/AppProvider";
import { lifeStyles as s } from "./LifeUi";
export function ResetPasswordScreen() {
  const app = useApp();
  const [error, setError] = useState("");
  const [done, setDone] = useState(false);
  return (
    <div className={s.standalone}>
      <h1>데모 비밀번호 변경</h1>
      <p>
        현재 탭에서 가입한 데모 계정만 변경할 수 있어요. 실제 이메일이나 인증
        코드는 발송하지 않습니다.
      </p>
      {done ? (
        <>
          <p role="status">이 탭의 데모 비밀번호를 변경했어요.</p>
          <Link href="/sign-in" className="button primary">
            로그인으로
          </Link>
          <button className="text-link" onClick={() => setDone(false)}>
            다시 변경하기
          </button>
        </>
      ) : (
        <form
          className="form-stack"
          onSubmit={async (event) => {
            event.preventDefault();
            setError("");
            const data = new FormData(event.currentTarget);
            try {
              if (data.get("password") !== data.get("confirmation"))
                throw Error("비밀번호 확인이 일치하지 않아요.");
              await app.dependencies.auth.resetPassword({
                email: String(data.get("email")),
                password: String(data.get("password")),
              });
              setDone(true);
            } catch (cause) {
              setError(
                cause instanceof Error ? cause.message : "변경하지 못했어요.",
              );
            }
          }}
        >
          <label>
            이메일
            <input name="email" type="email" required />
          </label>
          <label>
            새 데모 비밀번호
            <input
              name="password"
              type="password"
              required
              minLength={8}
              maxLength={72}
            />
          </label>
          <label>
            비밀번호 확인
            <input name="confirmation" type="password" required minLength={8} />
          </label>
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          <button className="button primary">데모 비밀번호 변경</button>
          <Link href="/sign-in" className="text-link">
            로그인으로 돌아가기
          </Link>
        </form>
      )}
    </div>
  );
}
