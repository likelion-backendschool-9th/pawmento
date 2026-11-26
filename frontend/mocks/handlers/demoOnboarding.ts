import type { Account } from "../../features/member/types/account.ts";
import { isDemoAccount } from "../../features/member/types/account.ts";
import type { createWorkspaceUseCases } from "./workspaceUseCases.ts";
import type { createLifeUseCases } from "./lifeUseCases.ts";
import { DEMO_ONBOARDING_INVITE } from "../fixtures/demoOnboarding.ts";

// 온보딩 화면 체험용. 실제 회원·초대 API를 부르거나 서버 권한을 만들지 않는다.
export function createDemoOnboarding(workspace: ReturnType<typeof createWorkspaceUseCases>, life: ReturnType<typeof createLifeUseCases>) {
  return {
    invitation: { code: DEMO_ONBOARDING_INVITE.code, petName: DEMO_ONBOARDING_INVITE.pet.name },
    async join(account: Account, rawCode: string) {
      if (!isDemoAccount(account)) throw Error("데모 계정에서만 체험할 수 있어요.");
      if (rawCode.trim().toUpperCase() !== DEMO_ONBOARDING_INVITE.code) throw Error("초대 코드를 다시 확인해 주세요.");
      const { hostId, hostName, pet: input } = DEMO_ONBOARDING_INVITE;
      const owner = { id: hostId, name: hostName, isAdmin: false };
      await life.registerMember(owner);
      const existing = await workspace.load(hostId);
      let pet = existing.pets.find((item) => item.ownerId === hostId);
      if (!pet) {
        pet = await workspace.savePet(hostId, input);
        await workspace.diary.save(hostId, { petId: pet.id, title: "처음 함께 걷던 길", recordedDate: "2026-11-20", body: "같은 길도 함께 걸으면 특별해져요.", photoUrl: "reference:running", authorName: hostName });
      }
      const actor = { id: account.id, name: account.nickname, isAdmin: false };
      const snapshot = await life.load();
      const joined = snapshot.families.some((family) => family.petId === pet.id && family.members.some((member) => member.actorId === account.id));
      if (!joined) {
        const invite = await life.inviteFamily(owner, pet.id, "EDITOR");
        await life.acceptInvite(actor, invite.id, account.nickname);
      }
      await workspace.selectPet(account.id, pet.id);
      return pet;
    },
  };
}
