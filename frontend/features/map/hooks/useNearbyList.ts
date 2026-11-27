"use client";
import { useEffect, useMemo, useSyncExternalStore } from "react";
import { useApp } from "@/app/AppProvider";
import type { MapPoint } from "../types/MapBounds.ts";
import type { PlaceCategory } from "../types/Place.ts";
import { boundsAround, type NearbyRadius } from "../utils/nearby.ts";
import { NearbyListController } from "../utils/nearbyListController.ts";

// 가까운 순 목록의 조회(#226-S5~S9). 기준점·반경·종류가 바뀔 때만 다시 조회하고, 기준점이 없으면(null) 조회하지 않는다.
// 지도를 움직이거나 장소를 눌러 지도가 옮겨 가도 목록은 다시 조회하지 않는다.
export function useNearbyList(
  point: MapPoint | null,
  radius: NearbyRadius,
  categories: readonly PlaceCategory[],
) {
  const { dependencies } = useApp();
  const controller = useMemo(
    () =>
      new NearbyListController((bounds, query) =>
        dependencies.places.list(bounds, query),
      ),
    [dependencies],
  );
  const latitude = point?.latitude;
  const longitude = point?.longitude;
  const kinds = categories.join(",");
  useEffect(() => {
    if (latitude === undefined || longitude === undefined) {
      controller.load(null);
      return;
    }
    controller.load({
      bounds: boundsAround({ latitude, longitude }, radius),
      categories: kinds === "" ? [] : (kinds.split(",") as PlaceCategory[]),
    });
  }, [controller, latitude, longitude, radius, kinds]);
  // 화면이 숨겨지거나 개발 모드(Strict Mode)에서 정리되면 기다리던 조회의 응답을 버린다. 같은 조건으로 다시 보이면 위 효과가 다시 조회한다
  useEffect(() => () => controller.dispose(), [controller]);
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getState,
    controller.getState,
  );
  return { state, retry: controller.retry };
}
