"use client";
import { useEffect, useMemo, useSyncExternalStore } from "react";
import { useApp } from "@/app/AppProvider";
import { PlaceSearchController } from "../utils/placeSearchController.ts";

// 검색창 하나의 검색 흐름(#195-S10, S13). 입력을 setInput에 넘기면 멈춘 뒤 검색하고, state가 결과·오류를 알려 준다.
export function usePlaceSearch() {
  const { dependencies } = useApp();
  const controller = useMemo(
    () => new PlaceSearchController((query) => dependencies.places.search(query)),
    [dependencies],
  );
  useEffect(() => () => controller.dispose(), [controller]);
  const state = useSyncExternalStore(controller.subscribe, controller.getState, controller.getState);
  return { state, setInput: controller.setInput, retry: controller.retry };
}
