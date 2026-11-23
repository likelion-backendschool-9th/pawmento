import type { Credentials } from "../types/account.ts";

// 입력 중 바로 보여주는 형식 확인이다. 서버의 최종 검증을 대신하지 않는다.
export function validateCredentials(input: Credentials) {
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input.email.trim()))
    throw Error("이메일 주소를 확인해 주세요.");
  if (input.password.length < 8 || input.password.length > 72)
    throw Error("비밀번호는 8~72자로 입력해 주세요.");
}

export function validateNickname(nickname: string) {
  if (!nickname.trim() || nickname.trim().length > 20)
    throw Error("닉네임은 1~20자로 입력해 주세요.");
}

export function normalizeEmail(email: string) {
  return email.trim().toLowerCase();
}
