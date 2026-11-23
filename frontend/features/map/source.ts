// #168 결정 뒤 정리
// 반려동물 id 형식으로 서버와 데모 산책을 한곳에서 섞는다.
// 서버 반려동물(양의 정수)은 HTTP를, 그 밖은 데모 저장소를 쓴다.

import { checkedMapArea } from "./api/mapPinApi.ts";
import { assertWalkDay, loadWalkDays } from "./api/walkHistoryApi.ts";
import type { MapBounds } from "./types/MapBounds.ts";
import type { MapPinList, MapPinOptions } from "./types/MapPin.ts";
import type { Walk, WalkEnd, WalkPoint } from "./types/Walk.ts";
import type { WalkDayList, WalkDays, WalkHistoryOptions } from "./types/WalkRecord.ts";
import { isDemoWalkId, isServerPetId } from "./utils/walk.ts";

type PinApi = {
  listPins(petId: string, bounds: MapBounds, options?: MapPinOptions): Promise<MapPinList>;
};

type WalkApi = {
  start(petId: string): Promise<Walk>;
  addPoints(walkId: string, points: readonly WalkPoint[]): Promise<void>;
  end(walkId: string): Promise<WalkEnd>;
};

type HistoryApi = {
  listDay(petId: string, date: string, options?: WalkHistoryOptions): Promise<WalkDayList>;
  listDays(petId: string, dates: readonly string[], options?: WalkHistoryOptions): Promise<WalkDays>;
};

type DemoPins = {
  list(petId: string, bounds: MapBounds, options?: MapPinOptions): Promise<MapPinList>;
};

type DemoWalks = WalkApi & {
  listByDate(petId: string, date: string, options?: WalkHistoryOptions): Promise<WalkDayList>;
};

export function createMapSource(input: {
  pins: PinApi;
  walks: WalkApi;
  history: HistoryApi;
  demoPins: DemoPins;
  demoWalks: DemoWalks;
}) {
  const { pins, walks, history, demoPins, demoWalks } = input;

  return {
    map: {
      async listPins(petId: string, bounds: MapBounds, options?: MapPinOptions) {
        const area = checkedMapArea(petId, bounds);
        if (isServerPetId(petId)) return pins.listPins(petId, area, options);
        return demoPins.list(petId, area, options);
      },
    },
    walk: {
      start(petId: string) {
        return (isServerPetId(petId) ? walks : demoWalks).start(petId);
      },
      addPoints(walkId: string, points: readonly WalkPoint[]) {
        return (isDemoWalkId(walkId) ? demoWalks : walks).addPoints(walkId, points);
      },
      end(walkId: string) {
        return (isDemoWalkId(walkId) ? demoWalks : walks).end(walkId);
      },
    },
    walkHistory: {
      listDay(petId: string, date: string, options?: WalkHistoryOptions) {
        if (isServerPetId(petId)) return history.listDay(petId, date, options);
        assertWalkDay(petId, date);
        return demoWalks.listByDate(petId, date, options);
      },
      listDays(petId: string, dates: readonly string[], options?: WalkHistoryOptions) {
        if (isServerPetId(petId)) return history.listDays(petId, dates, options);
        return loadWalkDays(petId, dates, (date) => demoWalks.listByDate(petId, date, options));
      },
    },
  };
}
