import type { LifeRepository } from "../../application/ports.ts";
import type { LifeState } from "../../domain/models.ts";
export function initialLifeState(): LifeState {
  const createdAt = "2026-11-12T09:00:00.000Z";
  return {
    posts: [
      {
        id: "post-walk",
        authorId: "sample-coco",
        author: "코코맘",
        category: "LIFE",
        title: "우리 아이와의 첫 여행, 정말 특별했어요",
        body: "처음으로 함께 바다를 다녀왔어요. 낯선 곳에서도 신나게 냄새를 맡고 뛰어다니던 모습이 아직도 생생해요. 여러분은 첫 여행을 어디로 가셨나요?",
        photoUrl: "reference:running",
        createdAt,
        updatedAt: createdAt,
        likes: [],
        bookmarks: [],
        hidden: false,
      },
      {
        id: "post-care",
        authorId: "sample-haru",
        author: "하루네",
        category: "CARE",
        title: "매일 산책 후 발 관리는 어떻게 하시나요?",
        body: "산책을 좋아하는 친구와 살고 있어요. 발을 씻긴 뒤 충분히 말려주려고 하는데, 각자의 작은 관리 습관이 궁금해요. 건강 관련 판단은 수의사와 상담하고 있어요.",
        photoUrl: "reference:sleeping",
        createdAt,
        updatedAt: createdAt,
        likes: [],
        bookmarks: [],
        hidden: false,
      },
      {
        id: "post-memory",
        authorId: "sample-star",
        author: "별의기억",
        category: "MEMORY",
        title: "평범한 저녁 산책을 오래 기억하고 싶어요",
        body: "오래 걷던 길을 사진으로 남겼어요. 거창하지 않아도 함께했던 순간이 참 소중하네요. 각자의 속도로 기억을 나누어요.",
        photoUrl: "reference:sunset",
        createdAt,
        updatedAt: createdAt,
        likes: [],
        bookmarks: [],
        hidden: false,
      },
    ],
    comments: [],
    reports: [],
    postcards: [],
    families: [],
    notices: [
      {
        id: "welcome",
        title: "PawMento에 오신 것을 환영해요",
        body: "질문과 답변을 함께 남기고, 달력에서 우리만의 시간을 다시 만나보세요. 현재는 브라우저 데모이며 실제 외부 전송과 AI 호출은 하지 않습니다.",
        published: true,
        createdAt,
      },
    ],
    questions: [
      {
        id: "q-1",
        text: "오늘 {name}{subject} 당신을 웃게 만든 순간은 언제인가요?",
        enabled: true,
      },
      {
        id: "q-2",
        text: "오늘 {name}에게 가장 고마웠던 순간을 들려주세요.",
        enabled: true,
      },
      {
        id: "q-3",
        text: "오늘 {name}와 새롭게 발견한 작은 즐거움은 무엇인가요?",
        enabled: true,
      },
      {
        id: "q-4",
        text: "{name}와 함께한 오늘, 오래 기억하고 싶은 풍경은 무엇인가요?",
        enabled: true,
      },
      {
        id: "q-5",
        text: "오늘 {name}에게 해주고 싶은 말이 있나요?",
        enabled: true,
      },
    ],
    members: [
      { id: "demo", name: "보리네", status: "ACTIVE" },
      { id: "sample-coco", name: "코코맘", status: "ACTIVE" },
      { id: "sample-haru", name: "하루네", status: "ACTIVE" },
    ],
  };
}
export class BrowserLifeRepository implements LifeRepository {
  async load() {
    try {
      const raw = localStorage.getItem("pawmento.life.v1");
      if (!raw) return initialLifeState();
      const state = JSON.parse(raw) as LifeState;
      if (
        !state ||
        ![
          "posts",
          "comments",
          "reports",
          "postcards",
          "families",
          "notices",
          "questions",
          "members",
        ].every((key) => Array.isArray(state[key as keyof LifeState]))
      )
        throw Error();
      return state;
    } catch {
      throw Error(
        "데모 데이터를 읽을 수 없어요. 브라우저 저장소를 확인해 주세요.",
      );
    }
  }
  async save(state: LifeState) {
    try {
      localStorage.setItem("pawmento.life.v1", JSON.stringify(state));
    } catch {
      throw Error(
        "브라우저 저장 공간이 부족해 저장하지 못했어요. 입력 내용은 유지됩니다.",
      );
    }
  }
}
