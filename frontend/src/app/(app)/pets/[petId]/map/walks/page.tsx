"use client";
import { MapRoute } from "@/map/adapters/inbound/MapRoute";
// 산책 길 보기(오늘)
export default function Page() {
  return <MapRoute initialView="walks" />;
}
