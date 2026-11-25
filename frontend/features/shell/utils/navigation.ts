import { mobileNavLinks } from "./mobileNavigation.ts";

export type AppNavKey =
  "home" | "diary" | "questions" | "community" | "places" | "mailbox";
export function activeNavigation(
  route: string,
  petId?: string,
): AppNavKey | null {
  const inRoute = (base: string) =>
    route === base || route.startsWith(base + "/");
  if (route === "/home") return "home";
  if (inRoute("/community")) return "community";
  if (inRoute("/places")) return "places";
  if (!petId) return null;
  const base = `/pets/${petId}`;
  if (route === `${base}/garden` || route === base) return "home";
  if (inRoute(`${base}/garden/mailbox`) || inRoute(`${base}/garden/postcards`))
    return "mailbox";
  if (inRoute(`${base}/questions`)) return "questions";
  if (
    ["diary", "calendar", "media", "records", "timeline"].some((p) =>
      inRoute(`${base}/${p}`),
    )
  )
    return "diary";
  return null;
}
export function appNavigation(
  petId?: string,
  mode: "ACTIVE" | "GARDEN" = "ACTIVE",
) {
  const base = petId ? `/pets/${petId}` : null;
  const links = [
    { key: "home", href: "/home", label: "홈", icon: "home" },
    {
      key: "diary",
      href: base ? `${base}/diary` : "/pets",
      label: "다이어리",
      icon: "book",
    },
    {
      key: "questions",
      href: base ? `${base}/questions` : "/pets",
      label: "오늘의 질문",
      icon: "question",
    },
    { key: "community", href: "/community", label: "커뮤니티", icon: "users" },
    mode === "GARDEN" && base
      ? {
          key: "mailbox",
          href: `${base}/garden/mailbox`,
          label: "우체통",
          icon: "mail",
        }
      : { key: "places", href: "/places", label: "정보 지도", icon: "map" },
  ] as const;
  return mobileNavLinks<(typeof links)[number]>(links.slice(0, 4), links[4]);
}
