export type Account = {
  id: string;
  email: string;
  nickname: string;
  role?: "USER" | "ADMIN";
};
export type Credentials = { email: string; password: string };
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
