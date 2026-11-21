"use client";
import { useParams } from "next/navigation";
import { MapRoute } from "@/map/adapters/inbound/MapRoute";
// 산책 길 보기(고른 날짜). 산책을 끝낸 직후에는 그 산책이 시작한 날을 연다.
export default function Page() {
  const { date } = useParams<{ date: string }>();
  return <MapRoute initialView="walks" initialDate={date} />;
}
