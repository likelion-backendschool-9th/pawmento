"use client";
import { useEffect, useId, useRef, useState } from "react";
import { Icon } from "@/components/Icon";
import type { MyLocationState } from "../utils/myLocationController";
import {
  LOCATION_DENIED,
  LOCATION_FAILED,
  LOCATION_INTRO,
  LOCATION_UNSUPPORTED,
} from "../utils/myLocationText";
import { nextTabIndex } from "../utils/tabKeys";
import { PinGlyph } from "./MyLocationArt";
import styles from "./MyLocation.module.css";

const TOAST_MS = 5000;

// "내 위치"의 안내(#226-S1, S3, S4). 권한을 묻기 전 안내와 권한이 꺼져 있을 때의 안내는 시트로, 읽지 못했거나 지원하지 않을 때는 잠깐 보이는 알림으로 알린다.
export function LocationNotice({
  status,
  onContinue,
  onDismiss,
  onRetry,
}: {
  status: MyLocationState["status"];
  onContinue: () => void;
  onDismiss: () => void;
  onRetry: () => void;
}) {
  if (status === "intro") return <IntroSheet onContinue={onContinue} onDismiss={onDismiss} />;
  if (status === "denied") return <DeniedSheet onRetry={onRetry} onDismiss={onDismiss} />;
  if (status === "failed") return <Toast variant="alert" message={LOCATION_FAILED} onDismiss={onDismiss} />;
  if (status === "unsupported") return <Toast variant="off" message={LOCATION_UNSUPPORTED} onDismiss={onDismiss} />;
  return null;
}

// 브라우저의 모달 dialog를 시트로 쓴다. 열려 있는 동안 Tab 이동이 안에서만 돌고 Esc로 닫는다. 닫으면 눌렀던 버튼으로 초점이 돌아간다.
function Sheet({ label, onDismiss, children }: { label: string; onDismiss: () => void; children: React.ReactNode }) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const element = dialog.current;
    if (element && !element.open) element.showModal();
    return () => {
      if (element?.open) element.close();
    };
  }, []);
  return (
    <dialog
      ref={dialog}
      className={styles.sheet}
      aria-label={label}
      onCancel={(event) => {
        event.preventDefault();
        onDismiss();
      }}
      onClick={(event) => {
        // 어두운 바깥(backdrop)을 누르면 닫는다
        if (event.target === dialog.current) onDismiss();
      }}
    >
      <div className={styles.grab} aria-hidden="true" />
      {children}
    </dialog>
  );
}

// 잔잔히 퍼지는 원 위에 위치 핀을 둔 그림
function Badge({ tone, variant }: { tone: "brand" | "warm"; variant: "paw" | "off" }) {
  return (
    <div className={styles.badge} data-tone={tone} aria-hidden="true">
      <PinGlyph variant={variant} size={46} />
    </div>
  );
}

function IntroSheet({ onContinue, onDismiss }: { onContinue: () => void; onDismiss: () => void }) {
  return (
    <Sheet label={LOCATION_INTRO.title} onDismiss={onDismiss}>
      <Badge tone="brand" variant="paw" />
      <h2 className={styles.title}>{LOCATION_INTRO.title}</h2>
      <p className={styles.body}>{LOCATION_INTRO.body}</p>
      <ul className={styles.points}>
        {LOCATION_INTRO.points.map((point) => (
          <li key={point}>
            <span className={styles.tick} aria-hidden="true">
              <Icon name="check" size={13} />
            </span>
            {point}
          </li>
        ))}
      </ul>
      <p className={styles.note}>{LOCATION_INTRO.note}</p>
      <button type="button" className={styles.cta} autoFocus onClick={onContinue}>
        {LOCATION_INTRO.confirm}
      </button>
      <button type="button" className={styles.ghost} onClick={onDismiss}>
        {LOCATION_INTRO.later}
      </button>
    </Sheet>
  );
}

// iPhone이면 iPhone 방법을, 아니면 Android 방법을 먼저 보인다
function firstDevice(): string {
  const iPhone = typeof navigator !== "undefined" && /iPhone|iPad|iPod/.test(navigator.userAgent);
  return iPhone ? "iPhone" : "Android";
}

function DeniedSheet({ onRetry, onDismiss }: { onRetry: () => void; onDismiss: () => void }) {
  const [guide, setGuide] = useState(false);
  const [device, setDevice] = useState(firstDevice);
  const ids = useId();
  const tabs = useRef<Array<HTMLButtonElement | null>>([]);
  const guides = LOCATION_DENIED.guides;
  const index = Math.max(0, guides.findIndex((item) => item.device === device));
  const current = guides[index];
  // 탭 패턴: 왼쪽·오른쪽 방향키로 기기를 바꾸고, 선택한 탭만 Tab 키로 닿는다
  const onTabKey = (event: React.KeyboardEvent) => {
    const next = nextTabIndex(event.key, index, guides.length);
    if (next === null) return;
    event.preventDefault();
    setDevice(guides[next].device);
    tabs.current[next]?.focus();
  };
  return (
    <Sheet label={LOCATION_DENIED.title} onDismiss={onDismiss}>
      <Badge tone="warm" variant="off" />
      <h2 className={styles.title}>{LOCATION_DENIED.title}</h2>
      <p className={styles.body}>{LOCATION_DENIED.reason}</p>
      <p className={styles.body}>{LOCATION_DENIED.fix}</p>
      <button
        type="button"
        className={styles.guideToggle}
        aria-expanded={guide}
        onClick={() => setGuide((open) => !open)}
      >
        {LOCATION_DENIED.guideTitle}
        <span className={styles.chevron} aria-hidden="true">
          <Icon name="chevron" size={16} />
        </span>
      </button>
      {guide && (
        <div className={styles.guide}>
          <div className={styles.tabs} role="tablist" aria-label="기기 선택" onKeyDown={onTabKey}>
            {guides.map((item, position) => (
              <button
                key={item.device}
                ref={(element) => {
                  tabs.current[position] = element;
                }}
                id={`${ids}-tab-${position}`}
                type="button"
                role="tab"
                aria-selected={position === index}
                aria-controls={`${ids}-panel`}
                tabIndex={position === index ? 0 : -1}
                className={styles.tab}
                onClick={() => setDevice(item.device)}
              >
                {item.device}
              </button>
            ))}
          </div>
          <div
            className={styles.panel}
            role="tabpanel"
            id={`${ids}-panel`}
            aria-labelledby={`${ids}-tab-${index}`}
            tabIndex={0}
          >
            <ol className={styles.steps}>
              {current.steps.map((step) => (
                <li key={step}>{step}</li>
              ))}
            </ol>
          </div>
        </div>
      )}
      <button type="button" className={styles.cta} autoFocus onClick={onRetry}>
        {LOCATION_DENIED.retry}
      </button>
      <button type="button" className={styles.ghost} onClick={onDismiss}>
        {LOCATION_DENIED.close}
      </button>
    </Sheet>
  );
}

// 잠깐 보였다 사라지는 안내 카드. 누르면 바로 닫는다
function Toast({ variant, message, onDismiss }: { variant: "alert" | "off"; message: string; onDismiss: () => void }) {
  useEffect(() => {
    const timer = setTimeout(onDismiss, TOAST_MS);
    return () => clearTimeout(timer);
  }, [message, onDismiss]);
  return (
    <div className={styles.toast} role="status" onClick={onDismiss}>
      <span className={styles.toastIcon} aria-hidden="true">
        <PinGlyph variant={variant} size={22} />
      </span>
      <span>{message}</span>
    </div>
  );
}
