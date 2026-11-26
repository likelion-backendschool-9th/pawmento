"use client";
import { MapRoute } from "@/features/map/components/MapRoute";
// 예전 산책 화면 주소. 지금은 추억 지도의 산책가기 탭에서 산책을 시작한다.
export default function Page() {
  return <MapRoute initialView="outing" />;
}
