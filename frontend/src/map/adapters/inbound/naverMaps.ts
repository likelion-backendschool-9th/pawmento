// 네이버 지도(Dynamic Map) JavaScript SDK를 필요할 때 한 번만 불러온다.
// Client ID는 브라우저에 공개되는 값이다. Client Secret은 서버에서만 쓰고 여기에는 두지 않는다.
export type NaverLatLng = { lat(): number; lng(): number };
export type NaverBounds = { getSW(): NaverLatLng; getNE(): NaverLatLng };
export type NaverMap = {
  getBounds(): NaverBounds;
  getCenter(): NaverLatLng;
  destroy(): void;
};
export type NaverMarker = { setMap(map: NaverMap | null): void };
export type NaverMapsApi = {
  Map: new (element: HTMLElement, options: Record<string, unknown>) => NaverMap;
  LatLng: new (latitude: number, longitude: number) => NaverLatLng;
  Point: new (x: number, y: number) => unknown;
  Marker: new (options: Record<string, unknown>) => NaverMarker;
  Event: {
    addListener(
      target: object,
      eventName: string,
      handler: () => void,
    ): unknown;
    removeListener(listener: unknown): void;
  };
};

declare global {
  interface Window {
    naver?: { maps?: NaverMapsApi };
    navermap_authFailure?: () => void;
  }
}

let loading: Promise<NaverMapsApi> | null = null;

export function loadNaverMaps(clientId: string): Promise<NaverMapsApi> {
  if (window.naver?.maps) return Promise.resolve(window.naver.maps);
  if (loading) return loading;
  loading = new Promise<NaverMapsApi>((resolve, reject) => {
    const script = document.createElement("script");
    script.src = `https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=${encodeURIComponent(clientId)}`;
    script.async = true;
    script.onload = () => {
      const maps = window.naver?.maps;
      if (maps) resolve(maps);
      else reject(new Error("지도를 불러오지 못했어요."));
    };
    script.onerror = () => reject(new Error("지도를 불러오지 못했어요."));
    document.head.appendChild(script);
  }).catch((cause) => {
    loading = null; // 다음에 다시 시도할 수 있게 한다
    throw cause;
  });
  return loading;
}
