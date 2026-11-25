export function renderQuestion(name: string, template: string): string {
  const last = name.charCodeAt(name.length - 1);
  const particle =
    last >= 0xac00 && last <= 0xd7a3
      ? (last - 0xac00) % 28
        ? "이"
        : "가"
      : "(이)가";
  return template.replace(/\{name\}|\{subject\}/g, token => token === "{name}" ? name : particle);
}
