"use client";
import { useEffect, useRef } from "react";
import {
  loadNaverMaps,
  type NaverMap,
  type NaverMapsApi,
  type NaverMarker,
} from "../utils/naverMaps";
import { placeMarkup } from "../utils/placeMarkup";
import type { PlaceCanvasProps } from "../utils/placeCanvas";
import styles from "./MapScreen.module.css";

export function NaverPlaceCanvas({
  clientId,
  onUnavailable,
  center,
  places,
  selectedId,
  onSelect,
  onBoundsChange,
}: PlaceCanvasProps & { clientId: string; onUnavailable: () => void }) {
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
          zoom: 15,
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
        // 지도를 움직이는 동안이 아니라 멈춘 뒤(idle)에 새 영역을 알린다(#172-S16)
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
    markers.current = places.map((place) => {
      const selected = place.id === selectedId;
      const half = selected ? 26 : 17;
      const marker = new maps.Marker({
        position: new maps.LatLng(place.latitude, place.longitude),
        map: current,
        zIndex: selected ? 2 : 1,
        icon: {
          content: placeMarkup(place.category, selected),
          // 둥근 마커의 가운데가 좌표 위치다
          anchor: new maps.Point(half, half),
        },
      });
      maps.Event.addListener(marker, "click", () =>
        latest.current.onSelect(place.id),
      );
      return marker;
    });
  }, [places, selectedId]);

  return (
    <div
      ref={element}
      className={styles.canvas}
      role="application"
      aria-label="정보 지도"
    />
  );
}
