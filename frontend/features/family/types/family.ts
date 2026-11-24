export type FamilyRole = "EDITOR" | "VIEWER";
export type FamilyMember = {
  id: string;
  name: string;
  role: FamilyRole;
  actorId?: string;
};
export type FamilyInvite = {
  id: string;
  role: FamilyRole;
  expiresAt: string;
  used: boolean;
};
export type Family = {
  petId: string;
  ownerId: string;
  members: FamilyMember[];
  invites: FamilyInvite[];
};
