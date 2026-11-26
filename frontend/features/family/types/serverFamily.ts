// 서버 가족 API의 화면용 타입. 데모 목업의 family.ts(EDITOR·VIEWER)와 섞지 않는다
export type FamilyPerson = {
  memberId: string | null;
  nickname: string | null;
  tag: string | null;
};
export type FamilyMemberRow = FamilyPerson & { joinedAt: string };
export type FamilyList = { owner: FamilyPerson; members: FamilyMemberRow[] };
export type InviteCode = { code: string; expiresAt: string };
export type JoinedPet = { id: string; name: string; tag: string };
export type FamilyPet = JoinedPet & { joinedAt: string };
