// Five destinations: home, diary, mode-specific map/mailbox, community, and my page.
export const MOBILE_NAV_LIMIT = 4;

export function mobileNavLinks<T>(links: readonly T[], lastLink: T): T[] {
  return [...links.slice(0, MOBILE_NAV_LIMIT), lastLink];
}
