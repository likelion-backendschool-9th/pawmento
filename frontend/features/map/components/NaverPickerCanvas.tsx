"use client";
import { useEffect, useRef } from "react";
import type { MapPoint } from "@/features/map/types/MapBounds";
import { loadNaverMaps, type NaverMap, type NaverMapsApi } from "../utils/naverMaps";
import styles from "./LocationPicker.module.css";

// 위치 고르기용 네이버 지도. 가운데 핀은 화면에 고정이고, 지도를 움직인 뒤 멈추면 지도의 가운데 좌표를 알려 준다.
export function NaverPickerCanvas({
  clientId,
  center,
  onCenterChange,
  onUnavailable,
}: {
  clientId: string;
  center: MapPoint;
  onCenterChange: (point: MapPoint) => void;
  onUnavailable: () => void;
}) {
  const element = useRef<HTMLDivElement>(null);
  const map = useRef<NaverMap | null>(null);
  const mapsApi = useRef<NaverMapsApi | null>(null);
  const latest = useRef({ onCenterChange, onUnavailable });
  useEffect(() => {
    latest.current = { onCenterChange, onUnavailable };
  });

  useEffect(() => {
    let cancelled = false;
    let listener: unknown;
    let api: Awaited<ReturnType<typeof loadNaverMaps>> | null = null;
    window.navermap_authFailure = () => latest.current.onUnavailable();
    loadNaverMaps(clientId)
      .then((maps) => {
        if (cancelled || !element.current) return;
        api = maps;
        mapsApi.current = maps;
        const created = new maps.Map(element.current, {
          center: new maps.LatLng(center.latitude, center.longitude),
          zoom: 16,
          zoomControl: false,
          scaleControl: false,
          mapDataControl: false,
        });
        map.current = created;
        const emit = () => {
          const point = created.getCenter();
          latest.current.onCenterChange({
            latitude: point.lat(),
            longitude: point.lng(),
          });
        };
        listener = maps.Event.addListener(created, "idle", emit);
        emit();
      })
      .catch(() => latest.current.onUnavailable());
    return () => {
      cancelled = true;
      if (listener) api?.Event.removeListener(listener);
      map.current?.destroy();
      map.current = null;
    };
    // 지도는 한 번만 만든다. 처음 center로 만들고, 그 뒤 center가 바뀌면 아래에서 옮긴다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clientId]);

  // 검색 결과를 고르면 지도를 다시 만들지 않고 가운데만 옮긴다. 옮긴 뒤 idle 이벤트가 새 가운데 좌표를 알린다.
  useEffect(() => {
    const maps = mapsApi.current;
    if (!map.current || !maps) return;
    map.current.setCenter(new maps.LatLng(center.latitude, center.longitude));
  }, [center]);

  return (
    <div
      ref={element}
      className={styles.canvas}
      role="application"
      aria-label="위치를 고를 지도"
    />
  );
}
