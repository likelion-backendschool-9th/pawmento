import type { QuestionPoolPort } from "./questionAnswerUseCases.ts";
import type { LifeRepository } from "./lifeUseCases.ts";
export class PoolQuestionRepository implements QuestionPoolPort {
  constructor(private repository: LifeRepository) {}
  async list(mode: "ACTIVE" | "GARDEN") {
    return (await this.repository.load()).questions
      .filter((q) => q.enabled && q.mode === mode)
      .map((q) => ({ id: q.id, text: q.text }));
  }
}
