"use client";
import { useEffect, useRef } from "react";
import {
  loadNaverMaps,
  type NaverCircle,
  type NaverMap,
  type NaverMapsApi,
  type NaverMarker,
} from "../utils/naverMaps";
import { boundsAround } from "../utils/nearby";
import { placeMarkup } from "../utils/placeMarkup";
import {
  fitMargin,
  markerShiftToVisible,
  type PlaceCanvasProps,
} from "../utils/placeCanvas";
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
  me,
}: PlaceCanvasProps & { clientId: string; onUnavailable: () => void }) {
  const element = useRef<HTMLDivElement>(null);
  const api = useRef<NaverMapsApi | null>(null);
  const map = useRef<NaverMap | null>(null);
  const markers = useRef<NaverMarker[]>([]);
  // 내 위치의 점과 정확도 원(#226-S2)
  const meLayers = useRef<{ marker: NaverMarker; circle: NaverCircle } | null>(
    null,
  );
  // 이미 처리한 이동 요청. 지도가 아직 준비되지 않았을 때 온 요청은 지도가 만들어진 뒤에 처리한다
  const handledFocus = useRef<number | null>(null);
  // 콜백은 최신 값을 쓰되 지도는 다시 만들지 않는다.
  const latest = useRef({
    onSelect,
    onBoundsChange,
    onUnavailable,
    places,
    coveredBottom,
    selectedId,
    focus,
    me,
  });
  useEffect(() => {
    latest.current = {
      onSelect,
      onBoundsChange,
      onUnavailable,
      places,
      coveredBottom,
      selectedId,
      focus,
      me,
    };
  });

  // 내 위치의 점과 정확도 원을 그린다. 지도 SDK가 늦게 떠도 지도를 만든 직후 같은 함수로 그린다
  const drawMe = () => {
    const maps = api.current;
    const current = map.current;
    if (!maps || !current) return;
    const mark = latest.current.me;
    const layers = meLayers.current;
    if (!mark) {
      layers?.marker.setMap(null);
      layers?.circle.setMap(null);
      meLayers.current = null;
      return;
    }
    const position = new maps.LatLng(mark.point.latitude, mark.point.longitude);
    if (layers) {
      layers.marker.setPosition(position);
      layers.circle.setCenter(position);
      layers.circle.setRadius(mark.accuracy);
      return;
    }
    const circle = new maps.Circle({
      map: current,
      center: position,
      radius: mark.accuracy,
      fillColor: "#385e46",
      fillOpacity: 0.12,
      strokeColor: "#385e46",
      strokeOpacity: 0.4,
      strokeWeight: 1,
      clickable: false,
    });
    const marker = new maps.Marker({
      map: current,
      position,
      clickable: false,
      zIndex: 0,
      icon: {
        content:
          '<span style="display:block;width:20px;height:20px;box-sizing:border-box;border-radius:50%;background:#385e46;border:3px solid #fff;box-shadow:0 0 0 1px rgba(0,0,0,.12),0 2px 8px rgba(0,0,0,.32)"></span>',
        // 둥근 점의 가운데가 좌표 위치다
        anchor: new maps.Point(10, 10),
      },
    });
    meLayers.current = { marker, circle };
  };

  // 검색 결과를 눌러 이동을 요청하면 그 지점으로 지도를 옮긴다(#195-S11). 줌은 바꾸지 않는다.
  // 이동 요청과 선택한 마커 위치를 지도에 맞춘다. 지도 SDK가 늦게 떠서 요청이 먼저 들어왔어도, 지도를 만든 직후 같은 함수로 적용한다
  const settleView = () => {
    const current = map.current;
    const maps = api.current;
    const box = element.current;
    if (!current || !maps || !box) return;
    const { focus: wanted, selectedId: chosen } = latest.current;
    if (wanted && handledFocus.current !== wanted.token) {
      handledFocus.current = wanted.token;
      if (wanted.fitRadiusMeters) {
        // 내 위치로 옮길 때는 그 반경이 보이게 확대 수준도 맞춘다. 검색창과 종류 칩이 덮는 만큼 위·아래를 같게 비워 점이 지도 한가운데에 놓이게 한다(#226-S2)
        const area = boundsAround(wanted.point, wanted.fitRadiusMeters);
        current.fitBounds(
          new maps.LatLngBounds(
            new maps.LatLng(area.minLatitude, area.minLongitude),
            new maps.LatLng(area.maxLatitude, area.maxLongitude),
          ),
          fitMargin(box.clientHeight),
        );
      } else {
        current.setCenter(
          new maps.LatLng(wanted.point.latitude, wanted.point.longitude),
        );
      }
    }
    if (!chosen) return;
    const place = latest.current.places.find((item) => item.id === chosen);
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
    const middle = current.getCenter();
    current.setCenter(
      new maps.LatLng(middle.lat() - (shift / height) * span, middle.lng()),
    );
  };

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
        // 지도가 뜨기 전에 들어온 이동 요청이 있으면 지금 적용한다(이 효과는 한 번만 돈다)
        settleView();
        drawMe();
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
      listeners.forEach((listener) =>
        api.current?.Event.removeListener(listener),
      );
      markers.current.forEach((marker) => marker.setMap(null));
      markers.current = [];
      meLayers.current?.marker.setMap(null);
      meLayers.current?.circle.setMap(null);
      meLayers.current = null;
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

  // 장소를 고르면 카드가 지도 아래를 덮는다. 선택한 마커가 카드 뒤에 가려지거나 칩에 가려지면 보이는 영역 가운데로 옮긴다.
  // 두 일을 한 곳에서 해야, 옮긴 뒤의 영역으로 마커 위치를 잰다.
  useEffect(() => {
    settleView();
  }, [selectedId, focus]);

  useEffect(() => {
    drawMe();
  }, [me]);

  return (
    <div
      ref={element}
      className={styles.canvas}
      role="application"
      aria-label="정보 지도"
    />
  );
}
