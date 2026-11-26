import { validateDate } from "../../../lib/date.ts";

/** Calendar age for presentation, calculated from the same Seoul date used by the screen. */
export function petAge(birthday: string, onDate: string): string | null {
  try { validateDate(birthday); validateDate(onDate); } catch { return null; }
  if (birthday > onDate) return null;
  const [birthYear, birthMonth, birthDay] = birthday.split("-").map(Number);
  const [year, month, day] = onDate.split("-").map(Number);
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const months = (year - birthYear) * 12 + month - birthMonth - (day < Math.min(birthDay, lastDay) ? 1 : 0);
  if (months < 1) return "1개월 미만";
  const years = Math.floor(months / 12);
  const remainder = months % 12;
  return years ? `${years}살${remainder ? ` ${remainder}개월` : ""}` : `${months}개월`;
}
