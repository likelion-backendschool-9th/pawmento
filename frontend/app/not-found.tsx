import Link from "next/link";
import { Brand } from "@/components/Brand";
export default function NotFound() {
  return (
    <main className="route-loading">
      <Brand />
      <h1>페이지를 찾을 수 없어요</h1>
      <p>주소를 확인하거나 홈에서 다시 시작해 주세요.</p>
      <Link className="button primary" href="/home">
        홈으로
      </Link>
    </main>
  );
}
