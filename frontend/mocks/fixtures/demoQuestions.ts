import type { Pet } from "../../features/pet/types/Pet.ts";
import type {
  QuestionAnswer,
  DemoQuestionAnswerInput,
} from "../../features/question/types/QuestionAnswer.ts";

/** Only produces missing samples; never replaces an existing day's answer. */
export function demoQuestionInputs(
  pet: Pet,
  answers: QuestionAnswer[],
  today: string,
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
    if (
      answers.some(
        (answer) => answer.petId === pet.id && answer.questionId === questionId,
      )
    )
      return [];
    return [
      {
        petId: pet.id,
        registeredAt: `${recordedDate}T12:00:00+09:00`,
        questionId,
        questionSnapshot: sample.question,
        body: sample.body.replaceAll("보리", pet.name),
        photoUrl: sample.photoUrl,
        authorName: "보리네",
      },
    ];
  });
}
