import type { Pet } from "../../features/pet/types/Pet.ts";
import type {
  QuestionAnswer,
  QuestionPrompt,
  DemoQuestionAnswerInput,
} from "../../features/question/types/QuestionAnswer.ts";

/** Only produces missing samples; never replaces an existing day's answer. */
export function demoQuestionInputs(
  pet: Pet,
  answers: QuestionAnswer[],
  today: string,
  prompts: readonly QuestionPrompt[] = [],
): DemoQuestionAnswerInput[] {
  const samples = [
    {
      days: 2,
      key: "park",
      question: `오늘 ${pet.name}가 가장 신났던 순간은 언제였나요?`,
      body: "공원에서 공을 꺼내자 귀를 쫑긋 세웠어요. 공을 쫓아 달려오던 표정이 얼마나 환하던지, 저도 함께 웃었어요.",
      photoUrl: "reference:running",
    },
    {
      days: 4,
      key: "nap",
      question: `${pet.name}만의 편안한 휴식 습관은 무엇인가요?`,
      body: "좋아하는 인형에 턱을 올리고 제 옆에서 낮잠을 자요. 조용한 숨소리를 듣고 있으니 평범한 오후가 더 소중하게 느껴졌어요.",
      photoUrl: "reference:sleeping",
    },
    {
      days: 6,
      key: "walk",
      question: `${pet.name}와 다시 함께 걷고 싶은 길은 어디인가요?`,
      body: "노을이 지던 산책길이요. 바람 냄새를 맡느라 자꾸 멈추는 보리를 기다리며 천천히 걸었어요. 서두르지 않아 더 좋았던 시간이었어요.",
      photoUrl: "reference:sunset",
    },
  ];
  return samples.flatMap((sample) => {
    const date = new Date(`${today}T12:00:00Z`);
    date.setUTCDate(date.getUTCDate() - sample.days);
    const recordedDate = date.toISOString().slice(0, 10);
    const questionId = `demo-question-${sample.key}`;
    const family = [
      {
        id: "demo",
        name: "보리네",
        body: sample.body,
        photo: sample.photoUrl,
        hour: 12,
      },
      {
        id: "demo-family-mom",
        name: "엄마",
        body:
          sample.key === "park"
            ? "저는 공원에서 돌아오는 길이 기억나요. 보리가 자꾸 뒤돌아보며 우리를 기다려 줬거든요."
            : sample.key === "nap"
              ? "햇살이 들어오는 소파 끝이 보리의 지정석이에요. 작은 코 고는 소리를 들으면 저도 마음이 편안해져요."
              : "벚꽃이 피던 동네 골목을 다시 걷고 싶어요. 보리랑 꽃잎을 밟으며 걷던 시간이 참 좋았어요.",
        photo: "",
        hour: 13,
      },
      {
        id: "demo-family-dad",
        name: "아빠",
        body:
          sample.key === "park"
            ? "아침에 목줄을 꺼냈을 때부터 신나 있었어요. 현관 앞에서 빙글빙글 돌던 모습이 생각나요."
            : sample.key === "nap"
              ? "제 발등에 턱을 올려놓고 잠드는 걸 좋아해요. 깨울까 봐 한참을 그대로 앉아 있었네요."
              : "비가 그친 강변 길이요. 보리가 풀 냄새를 맡는 동안 같이 하늘을 올려다봤던 기억이 나요.",
        photo: "",
        hour: 14,
      },
    ];
    return family
      .filter(
        (author) =>
          author.id !== "demo" ||
          !(
            prompts.some(
              (prompt) =>
                prompt.petId === pet.id && prompt.questionId === questionId,
            ) ||
            answers.some(
              (answer) =>
                answer.petId === pet.id && answer.questionId === questionId,
            )
          ),
      )
      .filter(
        (author) =>
          !answers.some(
            (a) =>
              a.petId === pet.id &&
              a.questionId === questionId &&
              a.authorId === author.id,
          ),
      )
      .map((author) => ({
        petId: pet.id,
        registeredAt: `${recordedDate}T${author.hour}:00:00+09:00`,
        questionId,
        questionSnapshot: sample.question,
        body: author.body.replaceAll("보리", pet.name),
        photoUrl: author.photo,
        authorId: author.id,
        authorName: author.name,
      }));
  });
}
