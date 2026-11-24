"use client";
import { useEffect, useRef, useState } from "react";
import { loadNaverMaps, type NaverMap, type NaverMapsApi, type NaverMarker, type NaverPolyline } from "./naverMaps";
import { DEFAULT_MAP_CENTER } from "./mapCanvas";
import { routeMarks } from "@/features/map/domain/WalkRoutes";
import type { WalkRoutesCanvasProps } from "./walkRoutesCanvas";
import styles from "./WalkHistory.module.css";

const TAG = `<span class="${styles.mapTag}">중단</span>`;
const START = `<span class="${styles.mapStart}"></span>`;
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
      const marks = routeMarks(walk.status);
      const paused = marks.pausedTag;
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
      // 예시 지도와 같은 표시: 끝났거나 진행 중이면 출발점, 중단되면 "중단" 표시. 점이 하나뿐인 산책도 출발점은 그린다.
      if (marks.startDot) {
        shapes.current.push(
          new maps.Marker({
            map: current,
            position: path[0],
            icon: { content: START, anchor: new maps.Point(9, 9) },
          }),
        );
      }
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
