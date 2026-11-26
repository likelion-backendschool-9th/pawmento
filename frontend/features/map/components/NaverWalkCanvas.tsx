"use client";
import { useEffect, useRef, useState } from "react";
import { loadNaverMaps, type NaverMap, type NaverMapsApi, type NaverPolyline, type NaverMarker } from "../utils/naverMaps";
import { DEFAULT_MAP_CENTER } from "../utils/mapCanvas";
import type { WalkCanvasProps } from "../utils/walkCanvas";
import styles from "./WalkCanvas.module.css";

const HERE = `<span class="${styles.hereMarker}"></span>`;
const START = `<span class="${styles.startMarker}"></span>`;

export function NaverWalkCanvas({
  clientId,
  onUnavailable,
  points,
  current,
  fitRoute = false,
}: WalkCanvasProps & { clientId: string; onUnavailable: () => void; fitRoute?: boolean }) {
  const element = useRef<HTMLDivElement>(null);
  const api = useRef<NaverMapsApi | null>(null);
  const map = useRef<NaverMap | null>(null);
  const line = useRef<NaverPolyline | null>(null);
  const marker = useRef<NaverMarker | null>(null);
  const startMarker = useRef<NaverMarker | null>(null);
  // 지도가 만들어진 뒤에만 길을 그릴 수 있다. 준비되면 그동안 쌓인(또는 복원된) 좌표를 다시 적용한다.
  const [ready, setReady] = useState(false);
  const latestProps = useRef({ onUnavailable });
  useEffect(() => {
    latestProps.current = { onUnavailable };
  });

  useEffect(() => {
    let cancelled = false;
    window.navermap_authFailure = () => latestProps.current.onUnavailable();
    loadNaverMaps(clientId)
      .then((maps) => {
        if (cancelled || !element.current) return;
        api.current = maps;
        const start = current ?? points[points.length - 1] ?? DEFAULT_MAP_CENTER;
        map.current = new maps.Map(element.current, {
          center: new maps.LatLng(start.latitude, start.longitude),
          zoom: 16,
          zoomControl: false,
          scaleControl: false,
          mapDataControl: false,
        });
        line.current = new maps.Polyline({
          map: map.current,
          path: [],
          strokeColor: "#688647",
          strokeWeight: 5,
          strokeOpacity: 0.95,
          strokeLineCap: "round",
          strokeLineJoin: "round",
        });
        marker.current = new maps.Marker({
          map: map.current,
          position: new maps.LatLng(start.latitude, start.longitude),
          icon: { content: HERE, anchor: new maps.Point(14, 14) },
        });
        startMarker.current = new maps.Marker({
          map: map.current,
          position: new maps.LatLng(start.latitude, start.longitude),
          icon: { content: START, anchor: new maps.Point(9, 9) },
        });
        startMarker.current.setMap(null); // 첫 점을 받은 뒤에만 보인다
        setReady(true);
      })
      .catch(() => latestProps.current.onUnavailable());
    return () => {
      cancelled = true;
      setReady(false);
      line.current?.setMap(null);
      marker.current?.setMap(null);
      startMarker.current?.setMap(null);
      map.current?.destroy();
      map.current = null;
    };
    // 지도는 한 번만 만들고, 이후 길과 위치는 아래 효과가 갱신한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clientId]);

  useEffect(() => {
    const maps = api.current;
    if (!maps || !map.current || !line.current || !marker.current) return;
    line.current.setPath(points.map((p) => new maps.LatLng(p.latitude, p.longitude)));
    // 출발점: 첫 점을 받은 뒤부터 그 자리에 보인다
    if (startMarker.current) {
      if (points.length > 0) {
        startMarker.current.setPosition(new maps.LatLng(points[0].latitude, points[0].longitude));
        startMarker.current.setMap(map.current);
      } else {
        startMarker.current.setMap(null);
      }
    }
    const here = current ?? points[points.length - 1];
    if (here) {
      const position = new maps.LatLng(here.latitude, here.longitude);
      marker.current.setPosition(position);
      if (fitRoute && points.length > 1) {
        const first = new maps.LatLng(points[0].latitude, points[0].longitude);
        const bounds = new maps.LatLngBounds(first, first);
        points.forEach((point) => bounds.extend(new maps.LatLng(point.latitude, point.longitude)));
        map.current.fitBounds(bounds, { top: 28, right: 28, bottom: 28, left: 28 });
      } else map.current.setCenter(position);
    }
  }, [ready, points, current, fitRoute]);

  return <div ref={element} className={styles.canvas} role="application" aria-label="걷는 길 지도" />;
}
