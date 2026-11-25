import { createGardenGuide } from "../utils/gardenGuide.ts";

// Only a dismissed UI guide is persisted, never account or journal data.
export const gardenGuide = createGardenGuide(() => window.localStorage);
