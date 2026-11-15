import { SharedWorkspaceRepository } from "../life/adapters/outbound/SharedWorkspaceRepository.ts";
import { BrowserLifeRepository } from "../life/adapters/outbound/BrowserLifeRepository.ts";
import { DemoPostcardGenerator } from "../life/adapters/outbound/DemoPostcardGenerator.ts";
import { PoolQuestionRepository } from "../life/adapters/outbound/PoolQuestionRepository.ts";
import { createLifeUseCases } from "../life/application/useCases.ts";
import { createGetTodayQuestion } from "../workspace/application/questions.ts";

import { createWorkspaceUseCases } from "../workspace/application/useCases.ts";
import { BrowserWorkspaceRepository } from "../workspace/adapters/outbound/BrowserWorkspaceRepository.ts";
import { createAuthUseCases } from "../auth/application/auth.ts";
import { DemoAuthRepository } from "../auth/adapters/outbound/DemoAuthRepository.ts";

export function createDependencies() {
  const lifeRepository = new BrowserLifeRepository();
  const runtime = {
    id: () => crypto.randomUUID(),
    now: () => new Date().toISOString(),
  };
  return {
    getTodayQuestion: createGetTodayQuestion(
      new PoolQuestionRepository(lifeRepository),
    ),
    life: createLifeUseCases(
      lifeRepository,
      new DemoPostcardGenerator(),
      runtime,
    ),
    workspace: createWorkspaceUseCases(
      new SharedWorkspaceRepository(
        new BrowserWorkspaceRepository(),
        lifeRepository,
      ),
      {
        id: () => crypto.randomUUID(),
        now: () => new Date().toISOString(),
      },
    ),
    auth: createAuthUseCases(new DemoAuthRepository()),
  };
}
export type Dependencies = ReturnType<typeof createDependencies>;
