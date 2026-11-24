export type PoolQuestion = {
  id: string;
  text: string;
  enabled: boolean;
  mode: "ACTIVE" | "GARDEN" | "LEGACY";
};
