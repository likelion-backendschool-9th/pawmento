"use client";
import { useEffect, useRef } from "react";
import {
  loadNaverMaps,
  type NaverMap,
  type NaverMapsApi,
  type NaverMarker,
} from "./naverMaps";
import { pinMarkup } from "./pinMarkup";
import type { MapCanvasProps } from "./mapCanvas";
import styles from "./MapScreen.module.css";

export function NaverMapCanvas({
  clientId,
  onUnavailable,
  center,
  pins,
  selectedId,
  mode,
  onSelect,
  onBoundsChange,
}: MapCanvasProps & { clientId: string; onUnavailable: () => void }) {
  const element = useRef<HTMLDivElement>(null);
  const api = useRef<NaverMapsApi | null>(null);
  const map = useRef<NaverMap | null>(null);
  const markers = useRef<NaverMarker[]>([]);
  // 콜백은 최신 값을 쓰되 지도는 다시 만들지 않는다.
  const latest = useRef({ onSelect, onBoundsChange, onUnavailable });
  useEffect(() => {
    latest.current = { onSelect, onBoundsChange, onUnavailable };
  });

  useEffect(() => {
    let cancelled = false;
    const listeners: unknown[] = [];
    // 등록하지 않은 주소에서 열면 SDK가 이 함수를 부른다
    window.navermap_authFailure = () => latest.current.onUnavailable();
    loadNaverMaps(clientId)
      .then((maps) => {
        if (cancelled || !element.current) return;
        api.current = maps;
        const created = new maps.Map(element.current, {
          center: new maps.LatLng(center.latitude, center.longitude),
          zoom: 14,
          zoomControl: false,
          scaleControl: false,
          mapDataControl: false,
        });
        map.current = created;
        const emitBounds = () => {
          const bounds = created.getBounds();
          const southWest = bounds.getSW();
          const northEast = bounds.getNE();
          latest.current.onBoundsChange({
            minLatitude: southWest.lat(),
            minLongitude: southWest.lng(),
            maxLatitude: northEast.lat(),
            maxLongitude: northEast.lng(),
          });
        };
        listeners.push(maps.Event.addListener(created, "idle", emitBounds));
        listeners.push(
          maps.Event.addListener(created, "click", () =>
            latest.current.onSelect(null),
          ),
        );
        emitBounds();
      })
      .catch(() => latest.current.onUnavailable());
    return () => {
      cancelled = true;
      listeners.forEach((listener) => api.current?.Event.removeListener(listener));
      markers.current.forEach((marker) => marker.setMap(null));
      markers.current = [];
      map.current?.destroy();
      map.current = null;
    };
    // 지도는 한 번만 만든다. center는 처음 위치로만 쓴다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clientId]);

  useEffect(() => {
    const maps = api.current;
    const current = map.current;
    if (!maps || !current) return;
    markers.current.forEach((marker) => marker.setMap(null));
    markers.current = pins.map((pin) => {
      const selected = pin.diaryId === selectedId;
      const marker = new maps.Marker({
        position: new maps.LatLng(pin.latitude, pin.longitude),
        map: current,
        zIndex: selected ? 2 : 1,
        icon: {
          content: pinMarkup(mode, selected),
          anchor: new maps.Point(selected ? 29 : 20, selected ? 84 : 58),
        },
      });
      maps.Event.addListener(marker, "click", () =>
        latest.current.onSelect(pin.diaryId),
      );
      return marker;
    });
  }, [pins, selectedId, mode]);

  return (
    <div
      ref={element}
      className={styles.canvas}
      role="application"
      aria-label="추억 지도"
    />
  );
}
