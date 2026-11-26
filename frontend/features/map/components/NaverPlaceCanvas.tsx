"use client";
import { useEffect, useRef } from "react";
import {
  loadNaverMaps,
  type NaverMap,
  type NaverMapsApi,
  type NaverMarker,
} from "../utils/naverMaps";
import { placeMarkup } from "../utils/placeMarkup";
import { markerShiftToVisible, type PlaceCanvasProps } from "../utils/placeCanvas";
import styles from "./MapScreen.module.css";

export function NaverPlaceCanvas({
  clientId,
  onUnavailable,
  center,
  places,
  selectedId,
  onSelect,
  onBoundsChange,
  coveredBottom,
  focus,
}: PlaceCanvasProps & { clientId: string; onUnavailable: () => void }) {
  const element = useRef<HTMLDivElement>(null);
  const api = useRef<NaverMapsApi | null>(null);
  const map = useRef<NaverMap | null>(null);
  const markers = useRef<NaverMarker[]>([]);
  // 이미 처리한 이동 요청. 지도가 아직 준비되지 않았을 때 온 요청은 지도가 만들어진 뒤에 처리한다
  const handledFocus = useRef<number | null>(null);
  // 콜백은 최신 값을 쓰되 지도는 다시 만들지 않는다.
  const latest = useRef({ onSelect, onBoundsChange, onUnavailable, places, coveredBottom });
  useEffect(() => {
    latest.current = { onSelect, onBoundsChange, onUnavailable, places, coveredBottom };
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

  // 검색 결과를 눌러 이동을 요청하면 그 지점으로 지도를 옮긴다(#195-S11). 줌은 바꾸지 않는다.
  // 장소를 고르면 카드가 지도 아래를 덮는다. 선택한 마커가 카드 뒤에 가려지거나 칩에 가려지면 보이는 영역 가운데로 옮긴다.
  // 두 일을 한 곳에서 해야, 옮긴 뒤의 영역으로 마커 위치를 잰다.
  useEffect(() => {
    const current = map.current;
    const maps = api.current;
    const box = element.current;
    if (!current || !maps || !box) return;
    if (focus && handledFocus.current !== focus.token) {
      handledFocus.current = focus.token;
      current.setCenter(new maps.LatLng(focus.point.latitude, focus.point.longitude));
    }
    if (!selectedId) return;
    const place = latest.current.places.find((item) => item.id === selectedId);
    if (!place) return;
    const height = box.clientHeight;
    if (height <= 0) return;
    const bounds = current.getBounds();
    const north = bounds.getNE().lat();
    const span = north - bounds.getSW().lat();
    if (!(span > 0)) return;
    const shift = markerShiftToVisible({
      y: ((north - place.latitude) / span) * height,
      height,
      covered: latest.current.coveredBottom?.() ?? 0,
    });
    if (shift === 0) return;
    // 마커를 위로 옮기려면 지도의 중심을 그만큼 남쪽으로 옮긴다
    const center = current.getCenter();
    current.setCenter(new maps.LatLng(center.lat() - (shift / height) * span, center.lng()));
  }, [selectedId, focus]);

  return (
    <div
      ref={element}
      className={styles.canvas}
      role="application"
      aria-label="정보 지도"
    />
  );
}
