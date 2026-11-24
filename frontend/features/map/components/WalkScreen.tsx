"use client";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { Icon } from "@/components/Icon";
import {
  formatDistance,
  formatDuration,
  isServerPetId,
  pathDistance,
} from "@/features/map/utils/walk";
import { seoulDate, walkHistoryPath } from "@/features/map/utils/walkRecord";
import { NaverWalkCanvas } from "./NaverWalkCanvas";
import { StaticWalkCanvas } from "./StaticWalkCanvas";
import { useWalkSession } from "../hooks/useWalkSession";
import styles from "./WalkScreen.module.css";

type Props = { pet: { id: string; name: string } };

export function WalkScreen({ pet }: Props) {
  const clientId = process.env.NEXT_PUBLIC_NAVER_MAP_CLIENT_ID ?? "";
  const [naverFailed, setNaverFailed] = useState(false);
  const session = useWalkSession(pet.id);
  const { phase, points, seconds, location, error, unsent, closed, outcome, walk } = session;
  // 끝난 산책은 시작한 날(한국 시각)의 산책 길로 보여 준다. 자정을 넘겨 끝난 산책도 시작한 날에 속한다.
  const historyPath = walkHistoryPath(pet.id, walk ? seoulDate(walk.startedAt) : null);
  // 카드 높이에 맞춰 경로 영역을 잡는다. 카드가 길어지거나 화면이 낮아도 경로가 카드에 가려지지 않는다.
  const card = useRef<HTMLDivElement>(null);
  const [cardHeight, setCardHeight] = useState(0);
  useEffect(() => {
    const element = card.current;
    if (!element || typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(() => setCardHeight(element.offsetHeight));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  const back = `/pets/${pet.id}/map`;
  const distance = formatDistance(pathDistance(points));
  const current = points.length > 0 ? points[points.length - 1] : null;
  const walking = phase === "walking" || phase === "ending";
  const canvasProps = { points, current };
  // 이 브라우저에서 만든 반려동물은 서버에 없어서 산책도 이 브라우저 안에서만 연습으로 기록된다.
  const practice = !isServerPetId(pet.id);
  const recording = location === "ok" && !closed;

  return (
    <section
      className={styles.screen}
      aria-label="산책 기록"
      style={{ "--card-height": `${cardHeight}px` } as React.CSSProperties}
    >
      {clientId && !naverFailed ? (
        <NaverWalkCanvas {...canvasProps} clientId={clientId} onUnavailable={() => setNaverFailed(true)} />
      ) : (
        <StaticWalkCanvas {...canvasProps} />
      )}

      <header className={styles.top}>
        <Link className={styles.close} href={back} aria-label="지도로 돌아가기" onClick={walking ? undefined : session.leave}>
          <Icon name="arrow" size={18} />
        </Link>
        {walking && (
          <>
            <span className={styles.chip}>
              <Icon name="paw" size={15} />
              {pet.name}와 산책 중
            </span>
            <span className={styles.chip + " " + styles.status + " " + (recording ? styles.rec : styles.paused)}>
              <i aria-hidden="true" />
              {closed || location === "denied" || location === "unsupported"
                ? "기록 멈춤"
                : location === "waiting"
                  ? "위치 찾는 중"
                  : "기록 중"}
            </span>
          </>
        )}
      </header>

      <div className={styles.card} ref={card}>
        <div className={styles.grab} aria-hidden="true" />
        {phase === "ended" ? (
          <>
            <p className={styles.eyebrow}>
              {outcome?.status === "INTERRUPTED" ? "산책이 중단됐어요" : "산책을 마쳤어요"}
            </p>
            <h2>
              {outcome?.status === "INTERRUPTED"
                ? "위치가 한동안 오지 않아 서버가 산책을 먼저 닫았어요"
                : outcome?.complete === false
                  ? "걸은 길의 일부만 저장됐어요"
                  : practice
                    ? `${pet.name}와 함께 걸은 길을 연습으로 기록했어요`
                    : `${pet.name}와 함께 걸은 길이 저장됐어요`}
            </h2>
            {(outcome?.status === "INTERRUPTED" || outcome?.complete === false) && (
              <p className={styles.warn} role="status">
                {outcome?.status === "INTERRUPTED"
                  ? "닫히기 전까지 서버에 도착한 길만 남아 있어요."
                  : "연결이 불안정해 서버에 보내지 못한 길이 있어요."}
              </p>
            )}
            <dl className={styles.stats}>
              <div><dt>걸은 시간</dt><dd>{formatDuration(seconds)}</dd></div>
              <div><dt>거리</dt><dd>{distance.value}<small>{distance.unit}</small></dd></div>
            </dl>
            <Link className="button primary" href={historyPath}>
              <Icon name="route" size={17} />
              산책 길 보기
            </Link>
            <Link className={styles.link} href={back}>
              지도로 돌아가기
            </Link>
          </>
        ) : walking ? (
          <>
            <dl className={styles.stats} aria-live="off">
              <div><dt>걸은 시간</dt><dd>{formatDuration(seconds)}</dd></div>
              <div><dt>거리</dt><dd>{distance.value}<small>{distance.unit}</small></dd></div>
            </dl>
            {location === "denied" || location === "unsupported" ? (
              <p className={styles.warn} role="alert">
                {location === "denied"
                  ? "위치 권한이 꺼져 있어 길을 기록할 수 없어요. 브라우저 설정에서 위치를 허용해 주세요."
                  : "이 브라우저는 위치를 지원하지 않아 길을 기록할 수 없어요."}
              </p>
            ) : location === "waiting" ? (
              <p className={styles.notice} role="status">현재 위치를 찾는 중이에요…</p>
            ) : (
              <p className={styles.notice}>
                <Icon name="phone" size={18} />
                화면을 켜 두어야 경로가 끊기지 않고 기록돼요.
              </p>
            )}
            {closed && (
              <p className={styles.warn} role="alert">
                서버가 이 산책을 이미 닫아서 이후 길은 기록되지 않아요. 끝내기를 눌러 마무리해 주세요.
              </p>
            )}
            {practice && (
              <p className={styles.notice}>
                이 반려동물은 이 브라우저에만 저장돼 있어서 산책도 서버가 아닌 이 브라우저에서 연습으로만 기록돼요.
              </p>
            )}
            {unsent && !closed && (
              <p className={styles.warn} role="status">
                연결이 불안정해 일부 길이 아직 저장되지 않았어요. 연결되면 다시 보낼게요.
              </p>
            )}
            {error && <p className={styles.warn} role="alert">{error}</p>}
            <button
              type="button"
              className="button primary"
              disabled={phase === "ending"}
              onClick={() => void session.finish()}
            >
              <Icon name="stop" size={18} />
              {phase === "ending" ? "끝내는 중…" : "산책 끝내기"}
            </button>
            {error && (
              <button type="button" className={styles.link} onClick={() => void session.finish({ flush: false })}>
                저장하지 못한 길은 버리고 끝내기
              </button>
            )}
          </>
        ) : (
          <>
            <p className={styles.eyebrow}>WALK</p>
            <h2>{pet.name}와 산책을 시작할까요?</h2>
            <p className={styles.notice}>걷는 동안 지나온 길을 지도에 그려 드려요.</p>
            {practice && (
              <p className={styles.notice}>
                이 반려동물은 이 브라우저에만 저장돼 있어서 산책도 서버가 아닌 이 브라우저에서 연습으로만 기록돼요.
              </p>
            )}
            {error && <p className={styles.warn} role="alert">{error}</p>}
            <button
              type="button"
              className="button primary"
              disabled={phase !== "idle"}
              onClick={() => void session.start()}
            >
              <Icon name="paw" size={17} />
              {phase === "starting" ? "시작하는 중…" : "산책 시작"}
            </button>
          </>
        )}
      </div>
    </section>
  );
}
