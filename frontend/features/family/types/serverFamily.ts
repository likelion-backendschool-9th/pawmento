// 서버 가족 API의 화면용 타입. 데모 목업의 family.ts(EDITOR·VIEWER)와 섞지 않는다
export type FamilyPerson = {
  memberId: string | null;
  nickname: string | null;
  tag: string | null;
};
export type FamilyMemberRow = FamilyPerson & { joinedAt: string };
export type FamilyList = { owner: FamilyPerson; members: FamilyMemberRow[] };
export type InviteCode = { code: string; expiresAt: string };
// 가입 응답의 반려동물. 서버 id를 정확히 읽지 못하면 id가 null이다
export type JoinedPet = { id: string | null; name: string; tag: string };
// 내가 가족인 반려동물. id를 정확히 아는 것만 목록에 남는다
export type FamilyPet = { id: string; name: string; tag: string; joinedAt: string };