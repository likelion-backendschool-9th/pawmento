export { renderQuestion as question } from "../../question/utils/renderQuestion.ts";
export const speciesName = {
  DOG: "강아지",
  CAT: "고양이",
  OTHER: "그 외 친구",
};
export const messageOf = (cause: unknown) =>
  cause instanceof Error ? cause.message : "잠시 후 다시 시도해 주세요.";
