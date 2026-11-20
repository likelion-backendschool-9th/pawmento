"use client";
import { useEffect, useRef, useState } from "react";
import { loadNaverMaps, type NaverMap, type NaverMapsApi, type NaverMarker, type NaverPolyline } from "./naverMaps";
import { DEFAULT_MAP_CENTER } from "./mapCanvas";
import type { WalkRoutesCanvasProps } from "./walkRoutesCanvas";
import styles from "./WalkHistory.module.css";

const TAG = `<span class="${styles.mapTag}">중단</span>`;
// 위쪽 날짜 막대와 아래쪽 목록에 가리지 않도록 경로를 맞출 때 비워 두는 여백(px)
const FIT_MARGIN = { top: 150, right: 36, bottom: 300, left: 36 };

export function NaverWalkRoutesCanvas({
  clientId,
  onUnavailable,
  walks,
  selectedId,
  onSelect,
}: WalkRoutesCanvasProps & { clientId: string; onUnavailable: () => void }) {
  const element = useRef<HTMLDivElement>(null);
  const api = useRef<NaverMapsApi | null>(null);
  const map = useRef<NaverMap | null>(null);
  const shapes = useRef<(NaverPolyline | NaverMarker)[]>([]);
  const [ready, setReady] = useState(false);
  const latest = useRef({ onSelect, onUnavailable });
  useEffect(() => {
    latest.current = { onSelect, onUnavailable };
  });

  useEffect(() => {
    let cancelled = false;
    window.navermap_authFailure = () => latest.current.onUnavailable();
    loadNaverMaps(clientId)
      .then((maps) => {
        if (cancelled || !element.current) return;
        api.current = maps;
        map.current = new maps.Map(element.current, {
          center: new maps.LatLng(DEFAULT_MAP_CENTER.latitude, DEFAULT_MAP_CENTER.longitude),
          zoom: 15,
          zoomControl: false,
          scaleControl: false,
          mapDataControl: false,
        });
        setReady(true);
      })
      .catch(() => latest.current.onUnavailable());
    return () => {
      cancelled = true;
      setReady(false);
      shapes.current.forEach((shape) => shape.setMap(null));
      shapes.current = [];
      map.current?.destroy();
      map.current = null;
    };
  }, [clientId]);

  // 날짜나 선택이 바뀌면 경로를 다시 그리고, 그날의 경로가 한눈에 들어오게 맞춘다.
  useEffect(() => {
    const maps = api.current;
    const current = map.current;
    if (!ready || !maps || !current) return;
    shapes.current.forEach((shape) => shape.setMap(null));
    shapes.current = [];
    let bounds: ReturnType<NaverMapsApi["LatLngBounds"]["prototype"]["extend"]> | null = null;
    walks.forEach((walk) => {
      if (walk.points.length === 0) return;
      const path = walk.points.map((p) => new maps.LatLng(p.latitude, p.longitude));
      const paused = walk.status !== "ENDED" && walk.status !== "IN_PROGRESS";
      const active = selectedId === null || walk.walkId === selectedId;
      const line = new maps.Polyline({
        map: current,
        path,
        strokeColor: paused ? "#bf8a5b" : "#688647",
        strokeWeight: paused ? 4 : 5,
        strokeOpacity: active ? 0.95 : 0.4,
        strokeStyle: paused ? "shortdot" : "solid",
        strokeLineCap: "round",
        strokeLineJoin: "round",
        clickable: true,
      });
      maps.Event.addListener(line, "click", () => latest.current.onSelect(walk.walkId));
      shapes.current.push(line);
      if (paused) {
        shapes.current.push(
          new maps.Marker({
            map: current,
            position: path[path.length - 1],
            icon: { content: TAG, anchor: new maps.Point(-6, 14) },
          }),
        );
      }
      path.forEach((point) => {
        bounds = bounds ? bounds.extend(point) : new maps.LatLngBounds(point, point);
      });
    });
    if (bounds) current.fitBounds(bounds, FIT_MARGIN);
  }, [ready, walks, selectedId]);

  return <div ref={element} className={styles.canvas} role="application" aria-label="산책 길 지도" />;
}
