"use client";
import { useEffect, useRef } from "react";
import { loadNaverMaps, type NaverMap, type NaverMapsApi, type NaverPolyline, type NaverMarker } from "./naverMaps";
import { DEFAULT_MAP_CENTER } from "./mapCanvas";
import type { WalkCanvasProps } from "./walkCanvas";
import styles from "./WalkScreen.module.css";

const HERE = `<span class="${styles.hereMarker}"></span>`;

export function NaverWalkCanvas({
  clientId,
  onUnavailable,
  points,
  current,
}: WalkCanvasProps & { clientId: string; onUnavailable: () => void }) {
  const element = useRef<HTMLDivElement>(null);
  const api = useRef<NaverMapsApi | null>(null);
  const map = useRef<NaverMap | null>(null);
  const line = useRef<NaverPolyline | null>(null);
  const marker = useRef<NaverMarker | null>(null);
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
      })
      .catch(() => latestProps.current.onUnavailable());
    return () => {
      cancelled = true;
      line.current?.setMap(null);
      marker.current?.setMap(null);
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
    const here = current ?? points[points.length - 1];
    if (here) {
      const position = new maps.LatLng(here.latitude, here.longitude);
      marker.current.setPosition(position);
      map.current.setCenter(position);
    }
  }, [points, current]);

  return <div ref={element} className={styles.canvas} role="application" aria-label="걷는 길 지도" />;
}
