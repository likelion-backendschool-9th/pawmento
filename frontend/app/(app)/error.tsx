"use client";
import Link from "next/link";
export default function ErrorPage({
  retry,
}: {
  error: unknown;
  retry: () => void;
}) {
  return (
    <section className="route-loading" role="alert">
      <h1>화면을 불러오지 못했어요</h1>
      <p>잠시 후 다시 시도해 주세요. 저장된 기록은 그대로 유지돼요.</p>
      <button className="button primary" onClick={retry}>
        다시 시도
      </button>{" "}
      <Link className="text-link" href="/home">
        홈으로
      </Link>
    </section>
  );
}
