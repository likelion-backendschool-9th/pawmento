import type { Category } from "../../features/community/types/community.ts";
import { categories } from "../../features/community/utils/categories.ts";
import type { JournalItem } from "../../features/diary/types/JournalItem.ts";
import type { FamilyRole } from "../../features/family/types/family.ts";
import type { Postcard } from "../../features/postcard/types/Postcard.ts";
import type { Actor, LifeState } from "../../features/shell/types/life.ts";
import type { RuntimePort } from "./workspaceUseCases.ts";
export interface LifeRepository {
  load(): Promise<LifeState>;
  save(state: LifeState): Promise<void>;
}
export interface PostcardGenerator {
  generate(
    records: readonly JournalItem[],
    petName: string,
    petPhotoUrl?: string,
  ): Promise<{
    text: string;
    imageUrl: string;
    demoArtwork?: Postcard["demoArtwork"];
  }>;
}
export function requireText(value: string, label: string, max = 3000) {
  if (!value.trim() || value.length > max)
    throw Error(`${label}은(는) 1~${max.toLocaleString()}자로 입력해 주세요.`);
}
export function requireOwner(ownerId: string, actor: Actor) {
  if (ownerId !== actor.id) throw Error("작성자만 변경할 수 있어요.");
}
export function requireAdmin(actor: Actor) {
  if (!actor.isAdmin) throw Error("관리자 데모 권한이 필요해요.");
}
export function createLifeUseCases(
  repository: LifeRepository,
  generator: PostcardGenerator,
  runtime: RuntimePort,
) {
  const dayKey = (timestamp: string) =>
    new Date(Date.parse(timestamp) + 9 * 3600000).toISOString().slice(0, 10);
  let queue: Promise<unknown> = Promise.resolve();
  const mutate = <T>(
    operation: (state: LifeState) => T,
    saveOnlyWhenChanged = false,
  ): Promise<T> => {
    const result = queue.then(async () => {
      const state = structuredClone(await repository.load());
      const before = saveOnlyWhenChanged ? JSON.stringify(state) : undefined;
      const value = operation(state);
      if (!saveOnlyWhenChanged || JSON.stringify(state) !== before)
        await repository.save(state);
      return value;
    });
    queue = result.catch(() => undefined);
    return result;
  };
  const post = (state: LifeState, id: string) => {
    const value = state.posts.find((item) => item.id === id);
    if (!value) throw Error("게시글을 찾을 수 없어요.");
    return value;
  };
  const family = (state: LifeState, actor: Actor, petId: string) => {
    let value = state.families.find((item) => item.petId === petId);
    if (!value) {
      value = { petId, ownerId: actor.id, members: [], invites: [] };
      state.families.push(value);
    }
    return value;
  };
  return {
    load: () => repository.load(),
    registerMember: (actor: Actor) =>
      mutate((state) => {
        const member = state.members.find((item) => item.id === actor.id);
        if (member) {
          member.name = actor.name;
          return member;
        }
        const value = {
          id: actor.id,
          name: actor.name,
          status: "ACTIVE" as const,
        };
        state.members.push(value);
        return value;
      }, true),
    withdrawActor: (actor: Actor) =>
      mutate((state) => {
        const ids = state.posts
          .filter((post) => post.authorId === actor.id)
          .map((post) => post.id);
        state.posts = state.posts.filter((post) => post.authorId !== actor.id);
        state.comments = state.comments.filter(
          (comment) =>
            comment.authorId !== actor.id && !ids.includes(comment.postId),
        );
        state.reports = state.reports.filter(
          (report) =>
            report.reporterId !== actor.id && !ids.includes(report.postId),
        );
        state.postcards = state.postcards.filter(
          (card) => card.ownerId !== actor.id,
        );
        state.families = state.families
          .filter((family) => family.ownerId !== actor.id)
          .map((family) => ({
            ...family,
            members: family.members.filter(
              (member) => member.actorId !== actor.id,
            ),
          }));
        state.members = state.members.filter(
          (member) => member.id !== actor.id,
        );
      }),
    savePost: (
      actor: Actor,
      input: {
        category: Category;
        title: string;
        body: string;
        photoUrl: string;
      },
      id?: string,
    ) =>
      mutate((state) => {
        requireText(input.title, "제목", 100);
        requireText(input.body, "내용", 5000);
        if (!(input.category in categories))
          throw Error("카테고리를 선택해 주세요.");
        const old = id ? post(state, id) : undefined;
        if (old) requireOwner(old.authorId, actor);
        const value = {
          ...input,
          id: id ?? runtime.id(),
          authorId: actor.id,
          author: actor.name,
          createdAt: old?.createdAt ?? runtime.now(),
          updatedAt: runtime.now(),
          likes: old?.likes ?? [],
          bookmarks: old?.bookmarks ?? [],
          hidden: old?.hidden ?? false,
        };
        state.posts = id
          ? state.posts.map((item) => (item.id === id ? value : item))
          : [value, ...state.posts];
        return value;
      }),
    deletePost: (actor: Actor, id: string) =>
      mutate((state) => {
        requireOwner(post(state, id).authorId, actor);
        state.posts = state.posts.filter((item) => item.id !== id);
        state.comments = state.comments.filter((item) => item.postId !== id);
      }),
    togglePost: (actor: Actor, id: string, type: "likes" | "bookmarks") =>
      mutate((state) => {
        const value = post(state, id);
        value[type] = value[type].includes(actor.id)
          ? value[type].filter((item) => item !== actor.id)
          : [...value[type], actor.id];
      }),
    saveComment: (actor: Actor, postId: string, body: string, id?: string) =>
      mutate((state) => {
        post(state, postId);
        requireText(body, "댓글", 1000);
        const old = id
          ? state.comments.find(
              (item) => item.id === id && item.postId === postId,
            )
          : undefined;
        if (id && !old) throw Error("댓글을 찾을 수 없어요.");
        if (old) requireOwner(old.authorId, actor);
        const value = {
          id: id ?? runtime.id(),
          postId,
          authorId: actor.id,
          author: actor.name,
          body: body.trim(),
          createdAt: old?.createdAt ?? runtime.now(),
        };
        state.comments = id
          ? state.comments.map((item) => (item.id === id ? value : item))
          : [...state.comments, value];
        return value;
      }),
    deleteComment: (actor: Actor, id: string) =>
      mutate((state) => {
        const value = state.comments.find((item) => item.id === id);
        if (!value) throw Error("댓글을 찾을 수 없어요.");
        requireOwner(value.authorId, actor);
        state.comments = state.comments.filter((item) => item.id !== id);
      }),
    reportPost: (actor: Actor, id: string, reason: string) =>
      mutate((state) => {
        post(state, id);
        requireText(reason, "신고 사유", 500);
        if (
          state.reports.some(
            (item) => item.postId === id && item.reporterId === actor.id,
          )
        )
          throw Error("이미 신고한 게시글이에요.");
        state.reports.push({
          id: runtime.id(),
          postId: id,
          reporterId: actor.id,
          reason,
          status: "PENDING",
          createdAt: runtime.now(),
        });
      }),
    resolveReport: (
      actor: Actor,
      id: string,
      resolution: "RESOLVED" | "DISMISSED",
    ) =>
      mutate((state) => {
        requireAdmin(actor);
        const value = state.reports.find((item) => item.id === id);
        if (!value) throw Error("신고를 찾을 수 없어요.");
        value.status = resolution;
        if (resolution === "RESOLVED") {
          const target = state.posts.find((item) => item.id === value.postId);
          if (target) target.hidden = true;
        }
      }),
    requestPostcard: async (
      actor: Actor,
      petId: string,
      petName: string,
      records: readonly JournalItem[],
      consent: boolean,
      retryId?: string,
      petPhotoUrl?: string,
    ) => {
      if (!consent) throw Error("데모 생성 안내를 확인해 주세요.");
      if (!records.length || records.some((item) => item.petId !== petId))
        throw Error("이 친구의 기록을 한 개 이상 선택해 주세요.");
      const value = await mutate((state) => {
        const old = retryId
          ? state.postcards.find(
              (item) => item.id === retryId && item.ownerId === actor.id,
            )
          : undefined;
        if (retryId && !old) throw Error("엽서를 찾을 수 없어요.");
        if (
          state.postcards.filter(
            (item) =>
              item.ownerId === actor.id &&
              dayKey(item.createdAt) === dayKey(runtime.now()),
          ).length >= 5 &&
          !old
        )
          throw Error("데모에서는 하루 5장까지 만들 수 있어요.");
        const card: Postcard = {
          id: old?.id ?? runtime.id(),
          petId,
          ownerId: actor.id,
          title: `${petName}와 함께한 작은 순간`,
          text: "",
          imageUrl: "",
          sourceEntries: records.map((item) => ({
            kind: item.kind,
            id: item.id,
          })),
          status: "PROCESSING",
          error: "",
          saved: old?.saved ?? false,
          createdAt: old?.createdAt ?? runtime.now(),
        };
        state.postcards = old
          ? state.postcards.map((item) => (item.id === old.id ? card : item))
          : [card, ...state.postcards];
        return card;
      });
      try {
        const generated = await generator.generate(
          records,
          petName,
          petPhotoUrl,
        );
        return await mutate((state) => {
          const card = state.postcards.find((item) => item.id === value.id)!;
          Object.assign(card, generated, { status: "COMPLETED" });
          return card;
        });
      } catch (cause) {
        return await mutate((state) => {
          const card = state.postcards.find((item) => item.id === value.id)!;
          card.status = "FAILED";
          card.error =
            cause instanceof Error ? cause.message : "생성에 실패했어요.";
          return card;
        });
      }
    },
    togglePostcard: (actor: Actor, id: string) =>
      mutate((state) => {
        const card = state.postcards.find((item) => item.id === id);
        if (!card) throw Error("엽서를 찾을 수 없어요.");
        requireOwner(card.ownerId, actor);
        card.saved = !card.saved;
      }),
    deletePostcard: (actor: Actor, id: string) =>
      mutate((state) => {
        const card = state.postcards.find((item) => item.id === id);
        if (!card) throw Error("엽서를 찾을 수 없어요.");
        requireOwner(card.ownerId, actor);
        state.postcards = state.postcards.filter((item) => item.id !== id);
      }),
    inviteFamily: (actor: Actor, petId: string, role: FamilyRole) =>
      mutate((state) => {
        const value = family(state, actor, petId);
        requireOwner(value.ownerId, actor);
        if (!["EDITOR", "VIEWER"].includes(role))
          throw Error("역할을 선택해 주세요.");
        const invite = {
          id: runtime.id(),
          role,
          expiresAt: new Date(
            Date.parse(runtime.now()) + 7 * 86400000,
          ).toISOString(),
          used: false,
        };
        value.invites.push(invite);
        return invite;
      }),
    acceptInvite: (actor: Actor, inviteId: string, name: string) =>
      mutate((state) => {
        requireText(name, "이름", 20);
        const value = state.families.find((item) =>
          item.invites.some((invite) => invite.id === inviteId),
        );
        const invite = value?.invites.find((item) => item.id === inviteId);
        if (
          !value ||
          !invite ||
          invite.used ||
          invite.expiresAt < runtime.now()
        )
          throw Error("만료되었거나 이미 사용한 초대예요.");
        if (
          value.members.some(
            (item) => item.actorId === actor.id && actor.id !== value.ownerId,
          )
        )
          throw Error("이미 참여한 가족이에요.");
        invite.used = true;
        value.members.push({
          id: runtime.id(),
          name: name.trim(),
          role: invite.role,
          actorId: actor.id,
        });
        return value;
      }),
    changeFamilyRole: (
      actor: Actor,
      petId: string,
      memberId: string,
      role: FamilyRole,
    ) =>
      mutate((state) => {
        const value = family(state, actor, petId);
        requireOwner(value.ownerId, actor);
        if (!["EDITOR", "VIEWER"].includes(role))
          throw Error("역할을 확인해 주세요.");
        const member = value.members.find((item) => item.id === memberId);
        if (!member) throw Error("가족을 찾을 수 없어요.");
        member.role = role;
      }),
    removeFamilyMember: (actor: Actor, petId: string, memberId: string) =>
      mutate((state) => {
        const value = family(state, actor, petId);
        requireOwner(value.ownerId, actor);
        value.members = value.members.filter((item) => item.id !== memberId);
      }),
    revokeInvite: (actor: Actor, petId: string, id: string) =>
      mutate((state) => {
        const value = family(state, actor, petId);
        requireOwner(value.ownerId, actor);
        value.invites = value.invites.filter((item) => item.id !== id);
      }),
    saveQuestion: (
      actor: Actor,
      text: string,
      id?: string,
      mode: "ACTIVE" | "GARDEN" = "ACTIVE",
    ) =>
      mutate((state) => {
        requireAdmin(actor);
        requireText(text, "질문", 300);
        if (!["ACTIVE", "GARDEN"].includes(mode))
          throw Error("질문의 모드를 선택해 주세요.");
        if (id) {
          const value = state.questions.find((item) => item.id === id);
          if (!value) throw Error("질문을 찾을 수 없어요.");
          value.text = text;
          value.mode = mode;
        } else
          state.questions.push({ id: runtime.id(), text, enabled: true, mode });
      }),
    toggleQuestion: (actor: Actor, id: string) =>
      mutate((state) => {
        requireAdmin(actor);
        const value = state.questions.find((item) => item.id === id);
        if (value) value.enabled = !value.enabled;
      }),
    saveNotice: (actor: Actor, title: string, body: string, id?: string) =>
      mutate((state) => {
        requireAdmin(actor);
        requireText(title, "공지 제목", 100);
        requireText(body, "공지 내용", 3000);
        const old = id
          ? state.notices.find((item) => item.id === id)
          : undefined;
        if (id && !old) throw Error("공지를 찾을 수 없어요.");
        const value = {
          id: id ?? runtime.id(),
          title,
          body,
          published: old?.published ?? true,
          createdAt: old?.createdAt ?? runtime.now(),
        };
        state.notices = id
          ? state.notices.map((item) => (item.id === id ? value : item))
          : [value, ...state.notices];
      }),
    toggleNotice: (actor: Actor, id: string) =>
      mutate((state) => {
        requireAdmin(actor);
        const value = state.notices.find((item) => item.id === id);
        if (value) value.published = !value.published;
      }),
    toggleMember: (actor: Actor, id: string) =>
      mutate((state) => {
        requireAdmin(actor);
        if (id === actor.id)
          throw Error("자신의 데모 권한은 변경할 수 없어요.");
        const value = state.members.find((item) => item.id === id);
        if (value)
          value.status = value.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE";
      }),
  };
}
