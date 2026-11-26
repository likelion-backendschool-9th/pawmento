"use client";
import { formatDuration, pathDistance } from "../utils/walk";
import type { useWalkSession } from "../hooks/useWalkSession";
import styles from "./WalkWidget.module.css";

type Props = { session: ReturnType<typeof useWalkSession> };

export function WalkWidget({ session }: Props) {
  const { phase, location, closed, points, seconds, error, unsent, outcome } = session;
  const active = phase === "walking" || phase === "ending";
  const moving = phase === "walking" && location === "ok" && !closed;
  const status = phase === "ended" ? "산책을 마쳤어요" : closed ? "기록이 멈췄어요" : active ? location === "ok" ? "함께 걷는 중" : "위치를 기다리고 있어요" : "산책을 떠나 볼까요?";
  return (
    <section className={styles.widget} aria-label="산책 위젯">
      <div className={styles.summary}>
        <div className={styles.scene} data-moving={moving} aria-label={moving ? "걷는 강아지" : "쉬는 강아지"} role="img">
          <svg viewBox="0 0 120 100" aria-hidden="true">
            <ellipse cx="60" cy="87" rx="42" ry="5" fill="currentColor" opacity=".12" />
            <g className={styles.dog} fill="#dfa65e" stroke="#b98043" strokeWidth="1.3" strokeLinejoin="round">
              <path className={styles.tail} d="M30 52C7 55 8 32 21 31C16 43 23 44 35 43Z" fill="#f6d49b" />
              <path className={styles.backLeg} d="M38 66L35 84Q29 88 26 83L28 64Z" />
              <path className={styles.backLeg} d="M77 64L83 83Q83 88 75 85L67 66Z" />
              <path d="M27 49Q39 35 70 45L84 57L77 73Q49 81 29 68Z" fill="#edbd7c" />
              <path d="M32 62Q50 70 74 58L76 72Q47 82 31 69Z" fill="#fff3da" stroke="none" />
              <path className={styles.frontLeg} d="M36 66L43 83Q43 89 34 85L28 66Z" fill="#fff3da" />
              <path className={styles.frontLeg} d="M71 64L70 84Q65 89 60 84L61 65Z" fill="#fff3da" />
              <g className={styles.head}>
                <path d="M66 40L63 15Q78 12 84 36M85 36L98 15Q107 21 101 45" />
                <path d="M68 31L68 21L78 35M91 33L98 23L98 40" fill="#e7a498" stroke="none" />
                <path d="M64 39Q79 27 96 39L105 51Q113 63 98 68Q76 75 65 57Z" fill="#f4c887" />
                <path d="M83 37L91 48Q101 47 108 54Q109 67 96 69Q79 67 75 57Z" fill="#fff5df" stroke="none" />
                <path d="M100 50Q109 47 109 53Q107 58 103 56Z" fill="#4b4036" stroke="none" />
                {moving ? <ellipse cx="91" cy="46" rx="2.4" ry="3" fill="#4b4036" stroke="none" /> : <path d="M87 47Q91 50 95 46" fill="none" stroke="#4b4036" strokeWidth="2" />}
                <path d="M96 60Q101 63 105 59" fill="none" stroke="#4b4036" />
                <path d="M70 61L82 69L71 77L65 66Z" fill="#849660" stroke="none" />
              </g>
            </g>
          </svg>
        </div>
        <div className={styles.metrics}>
          <p className={styles.status}><i data-active={moving} />{status}</p>
          <dl>
            <div><dt>걸은 거리</dt><dd>{Math.round(pathDistance(points)).toLocaleString("ko-KR")}<small>m</small></dd></div>
            <div><dt>산책 시간</dt><dd>{formatDuration(seconds)}</dd></div>
          </dl>
        </div>
      </div>
      <button type="button" className={styles.action} disabled={phase === "checking" || phase === "starting" || phase === "ending"} onClick={() => void (active ? session.finish() : session.start())}>
        <span aria-hidden="true">{active ? "□" : "↗"}</span>
        {phase === "checking" ? "산책 확인 중…" : phase === "starting" ? "시작하는 중…" : phase === "ending" ? "끝내는 중…" : active ? "산책 끝내기" : "산책 시작"}
      </button>
      {active && <p className={styles.note}>화면을 켜 두면 지나온 길을 기록할 수 있어요.</p>}
      {active && (location === "denied" || location === "unsupported" || closed) && <p className={styles.warning} role="alert">{closed ? "서버가 산책을 닫았어요. 끝내기를 눌러 마무리해 주세요." : location === "denied" ? "위치 권한을 허용해야 길을 기록할 수 있어요." : "이 브라우저에서는 위치를 기록할 수 없어요."}</p>}
      {unsent && !closed && <p className={styles.warning} role="status">아직 저장되지 않은 길이 있어요. 연결되면 다시 보낼게요.</p>}
      {phase === "ended" && (outcome?.status === "INTERRUPTED" || outcome?.complete === false) && <p className={styles.warning}>서버에 도착한 길만 저장됐어요.</p>}
      {error && <p className={styles.warning} role="alert">{error}</p>}
      {error && active && <button className={styles.discard} type="button" onClick={() => void session.finish({ flush: false })}>저장하지 못한 길은 버리고 끝내기</button>}
    </section>
  );
}
