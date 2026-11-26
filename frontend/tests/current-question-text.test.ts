import test from "node:test";
import assert from "node:assert/strict";
import { currentQuestionText } from "../features/question/utils/currentQuestionText.ts";
import { renderQuestion } from "../features/question/utils/renderQuestion.ts";
import { loadCurrentQuestion } from "../features/question/utils/loadCurrentQuestion.ts";

const template = "오늘 {name}{subject} 당신을 웃게 만든 순간은 언제인가요?";
const binding = { name: "보리", template };
test("#250-S1 현재 질문은 검증된 이름 자리만 현재 반려동물 이름과 조사로 표시하고 원문을 보존한다", () => {
  const snapshot = renderQuestion("보리", template);
  assert.equal(currentQuestionText("구름", snapshot, template, binding), "오늘 구름이 당신을 웃게 만든 순간은 언제인가요?");
  assert.equal(currentQuestionText("코코", snapshot, template, binding), "오늘 코코가 당신을 웃게 만든 순간은 언제인가요?");
  assert.equal(snapshot, "오늘 보리가 당신을 웃게 만든 순간은 언제인가요?");
});
test("#250-S1 20자 이름과 다른 반려동물을 연속 표시해도 이름이 섞이지 않는다", () => {
  const snapshot = renderQuestion("보리", template);
  for (const name of ["가".repeat(20), "W".repeat(20), "호두"])
    assert.equal(currentQuestionText(name, snapshot, template, binding), renderQuestion(name, template));
});
test("#250-S1 질문 내용이 변경됐거나 템플릿이 없으면 배정 원문을 임의로 재작성하지 않는다", () => {
  const snapshot = renderQuestion("보리", template);
  assert.equal(currentQuestionText("코코", snapshot), snapshot);
  assert.equal(currentQuestionText("코코", snapshot, "오늘 {name}와 산책했나요?"), snapshot);
  assert.equal(currentQuestionText("", snapshot, template, binding), snapshot);
});
test("#250-S1 정규식 문자·동일 이름 반복·숫자가 있는 질문도 이름 자리만 바꾼다", () => {
  const repeated = "[{name}] {name}1 (특별한 날?)";
  const snapshot = renderQuestion("C++", repeated);
  assert.equal(currentQuestionText("호두", snapshot, repeated, { name: "C++", template: repeated }), "[호두] 호두1 (특별한 날?)");
});
test("#250-S1 이름의 달러 기호와 중괄호는 치환 명령으로 해석하지 않는다", () => {
  for (const name of ["A$&", "내{name}", "{subject}"])
    assert.equal(renderQuestion(name, "{name}의 하루"), `${name}의 하루`);
});

test("#250-S1 템플릿 조회가 늦어도 배정 원문과 ID를 먼저 표시하고 같은 요청에서만 이름을 바꾼다", async () => {
  const snapshot = renderQuestion("보리", template);
  const prompt = { id: "p1", questionId: "q1", questionSnapshot: snapshot, displayBinding: binding };
  let release!: (pool: { id: string; mode: string; text: string }[]) => void;
  const pending = new Promise<{ id: string; mode: string; text: string }[]>(resolve => { release = resolve; });
  const shown: { id: string; text: string }[] = [];
  let active = true;
  const loading = loadCurrentQuestion({
    prompt, name: "구름", mode: "ACTIVE",
    loadTemplates: () => pending, isActive: () => active,
    publish: question => shown.push(question),
  });
  assert.deepEqual(shown, [{ id: "p1", text: snapshot }]);
  release([{ id: "q1", mode: "ACTIVE", text: template }]);
  await loading;
  assert.deepEqual(shown.at(-1), { id: "p1", text: renderQuestion("구름", template) });

  const stale: { id: string; text: string }[] = [];
  active = true;
  const late = loadCurrentQuestion({
    prompt, name: "구름", mode: "ACTIVE",
    loadTemplates: async () => { active = false; return [{ id: "q1", mode: "ACTIVE", text: template }]; },
    isActive: () => active,
    publish: question => stale.push(question),
  });
  await late;
  assert.deepEqual(stale, [{ id: "p1", text: snapshot }]);
});

test("#250-S1 템플릿 조회가 실패하거나 완료된 질문이면 배정 원문을 유지한다", async () => {
  const snapshot = renderQuestion("보리", template);
  const shown: string[] = [];
  await loadCurrentQuestion({
    prompt: { id: "p1", questionId: "q1", questionSnapshot: snapshot, displayBinding: binding },
    name: "구름", mode: "ACTIVE",
    loadTemplates: async () => { throw Error("offline"); }, isActive: () => true,
    publish: question => shown.push(question.text),
  });
  let loaded = false;
  await loadCurrentQuestion({
    prompt: { id: "p2", questionId: "q1", questionSnapshot: snapshot, displayBinding: binding, completedAt: "2026-11-25T00:00:00Z" },
    name: "구름", mode: "ACTIVE",
    loadTemplates: async () => { loaded = true; return [{ id: "q1", mode: "ACTIVE", text: template }]; },
    isActive: () => true,
    publish: question => shown.push(question.text),
  });
  assert.deepEqual(shown, [snapshot, snapshot]);
  assert.equal(loaded, false);
});


test("#250-S1 이름 바로 뒤의 글자가 삭제되거나 배정 템플릿을 확인할 수 없으면 원문을 유지한다", () => {
  const assigned = "오늘 {name}와 새롭게 발견한 작은 즐거움은 무엇인가요?";
  const snapshot = renderQuestion("보리", assigned);
  const assignment = { name: "보리", template: assigned };
  const changed = assigned.replace("{name}와", "{name}");
  for (const name of ["보리", "구름"])
    assert.equal(currentQuestionText(name, snapshot, changed, assignment), snapshot);
  assert.equal(currentQuestionText("구름", snapshot, assigned), snapshot);
  assert.equal(currentQuestionText("구름", snapshot, assigned, { name: "다른 아이", template: assigned }), snapshot);
  assert.equal(currentQuestionText("구름", snapshot, assigned, assignment), renderQuestion("구름", assigned));
});

test("#250-S1 이전 배정은 이름을 역추정하지 않고 템플릿 조회 없이 원문만 표시한다", async () => {
  const snapshot = "오늘 보리와 새롭게 발견한 작은 즐거움은 무엇인가요?";
  const shown: string[] = [];
  let loaded = false;
  await loadCurrentQuestion({
    prompt: { id: "old", questionId: "q1", questionSnapshot: snapshot }, name: "구름", mode: "ACTIVE",
    loadTemplates: async () => { loaded = true; return []; }, isActive: () => true,
    publish: question => shown.push(question.text),
  });
  assert.equal(loaded, false);
  assert.deepEqual(shown, [snapshot]);
});
