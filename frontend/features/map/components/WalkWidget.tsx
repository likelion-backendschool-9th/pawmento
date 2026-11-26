"use client";
import { formatDistance, formatDuration, pathDistance } from "../utils/walk";
import type { useWalkSession } from "../hooks/useWalkSession";
import styles from "./WalkWidget.module.css";

type Props = { session: ReturnType<typeof useWalkSession> };

export function WalkWidget({ session }: Props) {
  const { phase, location, closed, points, seconds, error, unsent, outcome } = session;
  const active = phase === "walking" || phase === "ending";
  const moving = phase === "walking" && location === "ok" && !closed;
  // 위치 권한이 꺼졌거나 위치를 쓸 수 없거나 서버가 닫은 산책은 기록이 멈춘 상태다
  const stopped = closed || location === "denied" || location === "unsupported";
  const status = phase === "ended" ? "산책을 마쳤어요" : !active ? "산책을 떠나 볼까요?" : stopped ? "기록이 멈췄어요" : location === "ok" ? "함께 걷는 중" : "위치를 기다리고 있어요";
  const distance = formatDistance(pathDistance(points));
  return (
    <section className={styles.widget} aria-label="산책 위젯">
      <div className={styles.summary}>
        <div className={styles.scene} data-moving={moving} aria-label={moving ? "걷는 강아지" : "쉬는 강아지"} role="img">
          <span className={styles.sprite} aria-hidden="true" />
        </div>
        <div className={styles.metrics}>
          <p className={styles.status}><i data-active={moving} />{status}</p>
          <dl>
            <div><dt>걸은 거리</dt><dd>{distance.value}<small>{distance.unit}</small></dd></div>
            <div><dt>산책 시간</dt><dd>{formatDuration(seconds)}</dd></div>
          </dl>
        </div>
      </div>
      <button type="button" className={styles.action} disabled={phase === "checking" || phase === "starting" || phase === "ending"} onClick={() => void (active ? session.finish() : session.start())}>
        <span aria-hidden="true">{active ? "□" : "↗"}</span>
        {phase === "checking" ? "산책 확인 중…" : phase === "starting" ? "시작하는 중…" : phase === "ending" ? "끝내는 중…" : active ? "산책 끝내기" : "산책 시작"}
      </button>
      {/* 산책 세션은 이 화면 안에서만 이어진다. 다른 화면으로 가면 위치 수집이 멈춘다. */}
      {active && <p className={styles.note}>이 화면을 떠나면 기록이 멈춰요. 화면을 켜 두어 주세요.</p>}
      {active && (location === "denied" || location === "unsupported" || closed) && <p className={styles.warning} role="alert">{closed ? "서버가 산책을 닫았어요. 끝내기를 눌러 마무리해 주세요." : location === "denied" ? "위치 권한을 허용해야 길을 기록할 수 있어요." : "이 브라우저에서는 위치를 기록할 수 없어요."}</p>}
      {unsent && !closed && <p className={styles.warning} role="status">아직 저장되지 않은 길이 있어요. 연결되면 다시 보낼게요.</p>}
      {phase === "ended" && (outcome?.status === "INTERRUPTED" || outcome?.complete === false) && <p className={styles.warning}>서버에 도착한 길만 저장됐어요.</p>}
      {error && <p className={styles.warning} role="alert">{error}</p>}
      {error && active && <button className={styles.discard} type="button" onClick={() => void session.finish({ flush: false })}>저장하지 못한 길은 버리고 끝내기</button>}
    </section>
  );
}
