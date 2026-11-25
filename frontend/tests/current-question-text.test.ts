import test from "node:test";
import assert from "node:assert/strict";
import { currentQuestionText } from "../features/question/utils/currentQuestionText.ts";
import { renderQuestion } from "../features/question/utils/renderQuestion.ts";

const template = "오늘 {name}{subject} 당신을 웃게 만든 순간은 언제인가요?";
test("#250-S1 현재 질문은 검증된 이름 자리만 현재 반려동물 이름과 조사로 표시하고 원문을 보존한다", () => {
  const snapshot = renderQuestion("보리", template);
  assert.equal(currentQuestionText("구름", snapshot, template), "오늘 구름이 당신을 웃게 만든 순간은 언제인가요?");
  assert.equal(currentQuestionText("코코", snapshot, template), "오늘 코코가 당신을 웃게 만든 순간은 언제인가요?");
  assert.equal(snapshot, "오늘 보리가 당신을 웃게 만든 순간은 언제인가요?");
});
test("#250-S1 20자 이름과 다른 반려동물을 연속 표시해도 이름이 섞이지 않는다", () => {
  const snapshot = renderQuestion("보리", template);
  for (const name of ["가".repeat(20), "W".repeat(20), "호두"])
    assert.equal(currentQuestionText(name, snapshot, template), renderQuestion(name, template));
});
test("#250-S1 질문 내용이 변경됐거나 템플릿이 없으면 배정 원문을 임의로 재작성하지 않는다", () => {
  const snapshot = renderQuestion("보리", template);
  assert.equal(currentQuestionText("코코", snapshot), snapshot);
  assert.equal(currentQuestionText("코코", snapshot, "오늘 {name}와 산책했나요?"), snapshot);
  assert.equal(currentQuestionText("", snapshot, template), snapshot);
});
test("#250-S1 정규식 문자·동일 이름 반복·숫자가 있는 질문도 이름 자리만 바꾼다", () => {
  const repeated = "[{name}] {name}1 (특별한 날?)";
  const snapshot = renderQuestion("C++", repeated);
  assert.equal(currentQuestionText("호두", snapshot, repeated), "[호두] 호두1 (특별한 날?)");
});
test("#250-S1 이름의 달러 기호와 중괄호는 치환 명령으로 해석하지 않는다", () => {
  for (const name of ["A$&", "내{name}", "{subject}"])
    assert.equal(renderQuestion(name, "{name}의 하루"), `${name}의 하루`);
});
