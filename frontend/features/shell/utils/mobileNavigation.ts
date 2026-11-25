// Common four items plus one mode-specific destination: places in ACTIVE, mailbox in GARDEN.
export const MOBILE_NAV_LIMIT = 4;

export function mobileNavLinks<T>(links: readonly T[], lastLink: T): T[] {
  return [...links.slice(0, MOBILE_NAV_LIMIT), lastLink];
}
