import type { QuestionRepository } from "../../../workspace/application/questions.ts";
import type { LifeRepository } from "../../application/ports.ts";
export class PoolQuestionRepository implements QuestionRepository {
  private repository: LifeRepository;
  constructor(repository: LifeRepository) {
    this.repository = repository;
  }
  async findForDate(petId: string, date: string) {
    const pool = (await this.repository.load()).questions.filter(
      (question) => question.enabled,
    );
    if (!pool.length)
      throw Error(
        "등록된 질문이 없어요. 관리자 데모에서 질문을 추가해 주세요.",
      );
    const index =
      ((Math.floor(Date.parse(date) / 86400000) % pool.length) + pool.length) %
      pool.length;
    return { id: `${pool[index].id}-${petId}-${date}`, text: pool[index].text };
  }
}
