"use client";
import { RecordActionLink } from "@/components/RecordAction";
import { PetDescription } from "@/features/pet/components/PetDescription";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { useApp } from "@/app/AppProvider";
import { isDemoAccount } from "@/features/member/types/account";
import { ArchiveNavigation } from "@/components/ArchiveNavigation";
import { Icon } from "@/components/Icon";
import { CollectionHeading } from "@/components/CollectionHeading";
import type { MapBounds } from "@/features/map/types/MapBounds";
import type { MapPinList } from "@/features/map/types/MapPin";
import { DEFAULT_MAP_CENTER } from "../utils/mapCanvas";
import { NaverMapCanvas } from "./NaverMapCanvas";
import { StaticMapCanvas } from "./StaticMapCanvas";
import { pinDetail } from "../utils/pinDetail";
import { pinMarkup, type PinMode } from "../utils/pinMarkup";
import { WalkHistory } from "./WalkHistory";
import styles from "./MapScreen.module.css";

type Props = {
  pet: { id: string; name: string; mode: PinMode };
  // 일기 상세 화면이 열 수 있는 기록인가(브라우저에 저장된 기록만 연다)
  canOpenDiary: (diaryId: string) => boolean;
  // 처음 보여 줄 보기. 기본은 기억 핀이고, 산책이 끝난 뒤에는 산책 길로 들어온다.
  initialView?: "pins" | "walks";
  // 산책 길을 처음 열 날짜(YYYY-MM-DD)
  initialDate?: string | null;
};

const REQUEST_DELAY_MS = 250;

export function MapScreen({
  pet,
  canOpenDiary,
  initialView = "pins",
  initialDate = null,
}: Props) {
  const { dependencies, account } = useApp();
  // 화면 확인용 예시 핀은 데모 계정에만 보여 준다
  const sample = isDemoAccount(account);
  const garden = pet.mode === "GARDEN";
  // Client ID는 공개되는 값이다. 빌드할 때 번들에 들어간다.
  const clientId = process.env.NEXT_PUBLIC_NAVER_MAP_CLIENT_ID ?? "";
  const [naverFailed, setNaverFailed] = useState(false);
  const [bounds, setBounds] = useState<MapBounds | null>(null);
  const [result, setResult] = useState<MapPinList | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const request = useRef(0);
  // 지도에 무엇을 보여 줄까: 기억 핀 또는 날짜별 산책 길
  const [view, setView] = useState<"pins" | "walks">(initialView);
  // 빈 화면의 "산책 길 보기"를 키보드로 눌러 화면이 바뀌어도 초점을 잃지 않게, 위쪽 전환 버튼으로 옮긴다
  const walksSwitch = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!bounds) return;
    const current = ++request.current;
    const timer = setTimeout(() => {
      setLoading(true);
      setError("");
      dependencies.map
        .listPins(pet.id, bounds, { sample })
        .then((list) => {
          if (current !== request.current) return; // 더 최근 요청이 있으면 버린다
          setResult(list);
          setSelectedId((id) =>
            list.pins.some((pin) => pin.diaryId === id) ? id : null,
          );
        })
        .catch((cause) => {
          if (current !== request.current) return;
          setError(
            cause instanceof Error
              ? cause.message
              : "기억을 불러오지 못했어요.",
          );
        })
        .finally(() => {
          if (current === request.current) setLoading(false);
        });
    }, REQUEST_DELAY_MS);
    return () => clearTimeout(timer);
  }, [bounds, pet.id, dependencies, sample, attempt]);

  const onBoundsChange = useCallback(
    (next: MapBounds) => setBounds(next),
    [],
  );
  const pins = result?.pins ?? [];
  const selected = pins.find((pin) => pin.diaryId === selectedId) ?? null;
  const unit = garden ? "송이" : "개";
  const canvasProps = {
    center: DEFAULT_MAP_CENTER,
    pins,
    selectedId,
    mode: pet.mode,
    onSelect: setSelectedId,
    onBoundsChange,
  };

  return (
    <>
      <CollectionHeading
        title="추억 지도"
        body={<PetDescription name={pet.name} suffix="와 다녀온 곳,">지도에서 다시 만나요.</PetDescription>}
      >
        <RecordActionLink icon="paw" compact href={`/pets/${pet.id}/map/walk`}>산책 시작</RecordActionLink>
        <RecordActionLink compact href={`/pets/${pet.id}/diary/new`}>기록 남기기</RecordActionLink>
      </CollectionHeading>
      <ArchiveNavigation petId={pet.id} active="map" />
      <div className={styles.switch} role="group" aria-label="지도 보기">
        <button type="button" aria-pressed={view === "pins"} onClick={() => setView("pins")}>
          <Icon name="pin" size={16} />
          기억 핀
        </button>
        <button
          ref={walksSwitch}
          type="button"
          aria-pressed={view === "walks"}
          onClick={() => setView("walks")}
        >
          <Icon name="route" size={16} />
          산책 길
        </button>
      </div>
      {view === "walks" ? (
        <WalkHistory pet={pet} initialDate={initialDate} />
      ) : (
      <>
      <div className={styles.stage} data-mode={pet.mode} aria-busy={loading}>
        {clientId && !naverFailed ? (
          <NaverMapCanvas
            {...canvasProps}
            clientId={clientId}
            onUnavailable={() => setNaverFailed(true)}
          />
        ) : (
          <StaticMapCanvas {...canvasProps} />
        )}

        {/* 기억이 하나도 없으면 칩을 숨기고, 핀을 선택하면 선택 카드에 자리를 내준다 */}
        {result && !error && pins.length > 0 && !selected && (
          <div className={styles.chip} role="status">
            <span
              className={styles.chipIcon}
              dangerouslySetInnerHTML={{
                __html: pinMarkup(pet.mode).replace(
                  /width="\d+" height="\d+"/,
                  'width="18" height="26"',
                ),
              }}
            />
            이 지역의 기억 <b>{pins.length}{unit}</b>
          </div>
        )}

        {result?.truncated && (
          <p className={styles.notice} role="status">
            기억이 많아서 일부만 보여요. 지도를 확대해 보세요.
          </p>
        )}

        {error && (
          <div className={styles.message} role="alert">
            <strong>기억을 불러오지 못했어요</strong>
            <p>{error}</p>
            <button
              type="button"
              className="button primary"
              onClick={() => setAttempt((count) => count + 1)}
            >
              다시 불러오기
            </button>
          </div>
        )}

        {result && !error && pins.length === 0 && !loading && (
          <div className={styles.message}>
            <span className={styles.emptyArt} aria-hidden="true">
              <span className={styles.emptyCircle}>
                <Icon name="paw" size={34} />
              </span>
              <span className={styles.emptyBadge}>
                <Icon name="pin" size={16} />
              </span>
            </span>
            <strong>이 지역에는 붙인 기억이 아직 없어요</strong>
            <p>
              <PetDescription name={pet.name} suffix="와 함께한 곳,">기록에 위치를 붙이면 여기에 모여요.</PetDescription>
            </p>
            <RecordActionLink href={`/pets/${pet.id}/diary/new`}>기록 쓰러 가기</RecordActionLink>
            <button
              type="button"
              className={styles.textButton}
              onClick={() => {
                setView("walks");
                walksSwitch.current?.focus();
              }}
            >
              산책 길 보기
            </button>
          </div>
        )}

        {selected && (
          <aside className={styles.sheet} aria-label="선택한 기억">
            <div className={styles.grab} aria-hidden="true" />
            <p className={styles.eyebrow}>{garden ? "기억의 정원" : "기억의 장소"}</p>
            <h2>{selected.placeName ?? "이름 없는 장소"}</h2>
            {(() => {
              const detail = pinDetail(
                Boolean(result?.sample),
                canOpenDiary(selected.diaryId),
              );
              if (detail === "OPEN")
                return (
                  <Link
                    className="button primary"
                    href={`/pets/${pet.id}/diary/${selected.diaryId}`}
                  >
                    기억 펼쳐보기
                    <Icon name="arrow" size={17} />
                  </Link>
                );
              return (
                <p className={styles.muted}>
                  {detail === "SAMPLE"
                    ? "화면을 확인하기 위한 예시 기억이에요."
                    : "기억 상세 화면은 곧 열려요. 지금은 지도에서 장소만 볼 수 있어요."}
                </p>
              );
            })()}
          </aside>
        )}
      </div>
      {result?.sample && (
        <p className={styles.note}>
          데모 계정에서는 서울숲 근처의 예시 기억을 보여 드려요. 로그인하면 내
          기록의 위치가 나타나요.
        </p>
      )}
      </>
      )}
    </>
  );
}
