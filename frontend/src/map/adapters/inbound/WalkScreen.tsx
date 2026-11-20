"use client";
import Link from "next/link";
import { useState } from "react";
import { Icon } from "@/shared/ui/Icon";
import { formatDistance, formatDuration, pathDistance } from "@/map/domain/Walk";
import { NaverWalkCanvas } from "./NaverWalkCanvas";
import { StaticWalkCanvas } from "./StaticWalkCanvas";
import { useWalkSession } from "./useWalkSession";
import styles from "./WalkScreen.module.css";

type Props = { pet: { id: string; name: string } };

export function WalkScreen({ pet }: Props) {
  const clientId = process.env.NEXT_PUBLIC_NAVER_MAP_CLIENT_ID ?? "";
  const [naverFailed, setNaverFailed] = useState(false);
  const session = useWalkSession(pet.id);
  const { phase, points, seconds, location, error, unsent, closed } = session;
  const back = `/pets/${pet.id}/map`;
  const distance = formatDistance(pathDistance(points));
  const current = points.length > 0 ? points[points.length - 1] : null;
  const walking = phase === "walking" || phase === "ending";
  const canvasProps = { points, current };

  return (
    <section className={styles.screen} aria-label="산책 기록">
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
            <span className={styles.chip + " " + styles.rec}>
              <i aria-hidden="true" />
              기록 중
            </span>
          </>
        )}
      </header>

      <div className={styles.card}>
        {phase === "ended" ? (
          <>
            <p className={styles.eyebrow}>산책을 마쳤어요</p>
            <h2>{pet.name}와 함께 걸은 길이 저장됐어요</h2>
            <dl className={styles.stats}>
              <div><dt>걸은 시간</dt><dd>{formatDuration(seconds)}</dd></div>
              <div><dt>거리</dt><dd>{distance.value}<small>{distance.unit}</small></dd></div>
            </dl>
            <Link className="button primary" href={back}>
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
              <p className={styles.notice}>화면을 켜 두어야 경로가 끊기지 않고 기록돼요</p>
            )}
            {closed && (
              <p className={styles.warn} role="alert">
                이 산책은 이미 닫혔어요. 끝내기를 눌러 마무리해 주세요.
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
