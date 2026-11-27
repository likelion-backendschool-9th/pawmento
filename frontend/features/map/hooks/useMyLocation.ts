"use client";
import { useEffect, useMemo, useSyncExternalStore } from "react";
import {
  MyLocationController,
  type IntroSeen,
  type LocationFix,
  type LocationPermission,
  type LocationReadResult,
  type LocationSource,
} from "../utils/myLocationController.ts";

// 위치는 한 번 읽는다. 동네 단위 거리를 보여 주는 용도라 정확도 우선은 끄고(배터리), 시간 초과는 10초, 1분 안에 읽은 값은 다시 쓴다(#226 가정).
const READ_OPTIONS: PositionOptions = { enableHighAccuracy: false, timeout: 10_000, maximumAge: 60_000 };
const INTRO_KEY = "pawmento.location-intro-seen";

const hasGeolocation = () => typeof navigator === "undefined" || "geolocation" in navigator;

function browserSource(): LocationSource {
  return {
    get supported() {
      return hasGeolocation();
    },
    async permission(): Promise<LocationPermission> {
      try {
        const status = await navigator.permissions.query({ name: "geolocation" });
        return status.state;
      } catch {
        // 권한 조회를 지원하지 않는 브라우저
        return "unknown";
      }
    },
    read(): Promise<LocationReadResult> {
      return new Promise((resolve) => {
        navigator.geolocation.getCurrentPosition(
          (position) =>
            resolve({
              ok: true,
              fix: {
                point: { latitude: position.coords.latitude, longitude: position.coords.longitude },
                accuracy: position.coords.accuracy,
              },
            }),
          (error) =>
            resolve({
              ok: false,
              reason: error.code === error.PERMISSION_DENIED ? "denied" : error.code === error.TIMEOUT ? "timeout" : "unavailable",
            }),
          READ_OPTIONS,
        );
      });
    },
  };
}

// 안내를 본 적이 있다는 사실만 이 기기에 남긴다. 위치는 남기지 않는다(#226-S10). 저장소를 쓸 수 없으면 이번 화면에서만 기억한다
function browserIntroSeen(): IntroSeen {
  let seenHere = false;
  return {
    get() {
      try {
        return seenHere || window.localStorage.getItem(INTRO_KEY) === "1";
      } catch {
        return seenHere;
      }
    },
    set() {
      seenHere = true;
      try {
        window.localStorage.setItem(INTRO_KEY, "1");
      } catch {
        // 저장하지 못해도 이번 화면에서는 다시 묻지 않는다
      }
    },
  };
}

const subscribeNever = () => () => {};

// "내 위치" 버튼 하나의 흐름(#226-S1~S4). 위치를 읽으면 onFix로 알린다.
export function useMyLocation(onFix: (fix: LocationFix) => void) {
  const controller = useMemo(
    () => new MyLocationController(browserSource(), browserIntroSeen()),
    [],
  );
  useEffect(() => {
    controller.setOnFix(onFix);
  });
  useEffect(() => () => controller.dispose(), [controller]);
  const state = useSyncExternalStore(controller.subscribe, controller.getState, controller.getState);
  // 서버에서 그릴 때는 지원하는 것으로 본다
  const supported = useSyncExternalStore(subscribeNever, hasGeolocation, () => true);
  return {
    state,
    supported,
    press: controller.press,
    continueIntro: controller.continueIntro,
    dismiss: controller.dismiss,
  };
}
