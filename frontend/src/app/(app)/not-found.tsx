import Link from "next/link";
export default function NotFound() {
  return (
    <section className="route-loading">
      <h1>친구 또는 기록을 찾을 수 없어요</h1>
      <p>삭제되었거나 접근할 수 없는 기록일 수 있어요.</p>
      <Link className="button primary" href="/pets">
        반려동물 관리로
      </Link>
    </section>
  );
}
