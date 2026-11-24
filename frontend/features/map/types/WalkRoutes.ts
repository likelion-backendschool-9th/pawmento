import type { WalkStatus } from "./Walk.ts";

export type RouteView = {
  walkId: string;
  status: WalkStatus;
  path: { x: number; y: number }[];
};
