export type Account = {
  id: string;
  email: string;
  nickname: string;
  role?: "USER" | "ADMIN";
  // 탭 안에서만 쓰는 데모 세션이 만든 계정이다. 서버 로그인 계정에는 없다.
  demo?: true;
};

export type Credentials = { email: string; password: string };

// 지금 세션이 어디서 왔는지. null은 아직 로그인하지 않았거나 확인 전이다
export type SessionSource = "http" | "demo" | null;

// 데모 여부는 이메일 모양이 아니라 인증 세션의 출처로 판단한다(데모 인증 저장소가 만든 계정에만 demo 표시가 있다).
// 일반 회원가입으로 만든 계정은 이메일이 @pawmento.local로 끝나도 데모가 아니다.
export function isDemoAccount(account: Account | null | undefined) {
  return account?.demo === true;
}
