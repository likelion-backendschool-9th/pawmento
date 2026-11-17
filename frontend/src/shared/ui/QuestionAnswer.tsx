import styles from "./QuestionAnswer.module.css";
export function QuestionAnswer({
  question,
  answer,
  compact = false,
}: {
  question: string;
  answer: string;
  compact?: boolean;
}) {
  return (
    <dl className={`${styles.pair} ${compact ? styles.compact : ""}`}>
      <div>
        <dt>
          <span>Q</span>질문
        </dt>
        <dd>{question || "이전 기록의 질문 정보가 없어요."}</dd>
      </div>
      <div>
        <dt>
          <span>A</span>남긴 답변
        </dt>
        <dd>{answer}</dd>
      </div>
    </dl>
  );
}
