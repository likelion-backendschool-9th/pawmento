"use client";
import Link from "next/link";
import { Icon } from "@/components/Icon";
import { lifeStyles as s } from "./LifeUi";
export function OfflineScreen() {
  return (
    <div className={s.standalone}>
      <Icon name="wifi" size={44} />
      <h1>잠시 연결이 쉬어가고 있어요</h1>
      <p>
        저장된 기록은 브라우저에 남아 있어요.
        <br />
        네트워크 연결을 확인한 뒤 다시 열어주세요.
      </p>
      <button className="button primary" onClick={() => location.reload()}>
        다시 연결하기
      </button>
      <Link href="/" className="text-link">
        처음 화면으로
      </Link>
    </div>
  );
}
