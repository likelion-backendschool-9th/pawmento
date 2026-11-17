export const question = (name: string, template: string) => {
  const last = name.charCodeAt(name.length - 1);
  const particle =
    last >= 0xac00 && last <= 0xd7a3
      ? (last - 0xac00) % 28
        ? "이"
        : "가"
      : "(이)가";
  return template.replaceAll("{name}", name).replaceAll("{subject}", particle);
};
export const speciesName = {
  DOG: "강아지",
  CAT: "고양이",
  OTHER: "그 외 친구",
};
export const messageOf = (cause: unknown) =>
  cause instanceof Error ? cause.message : "잠시 후 다시 시도해 주세요.";
