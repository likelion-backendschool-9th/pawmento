import { createId } from "../shared/id.ts";
import { createApiClient } from "../shared/http/apiClient.ts";
import { SharedWorkspaceRepository } from "../life/adapters/outbound/SharedWorkspaceRepository.ts";
import { BrowserLifeRepository } from "../life/adapters/outbound/BrowserLifeRepository.ts";
import { DemoPostcardGenerator } from "../life/adapters/outbound/DemoPostcardGenerator.ts";
import { PoolQuestionRepository } from "../life/adapters/outbound/PoolQuestionRepository.ts";
import { createLifeUseCases } from "../life/application/useCases.ts";

import { createWorkspaceUseCases } from "../workspace/application/useCases.ts";
import { BrowserWorkspaceRepository } from "../workspace/adapters/outbound/BrowserWorkspaceRepository.ts";
import { createAuthUseCases } from "../auth/application/auth.ts";
import { SessionAuthRepository } from "../auth/adapters/outbound/SessionAuthRepository.ts";
import { createPetUseCases } from "../pets/application/useCases.ts";
import { HttpPetListRepository } from "../pets/adapters/outbound/HttpPetListRepository.ts";

export function createDependencies() {
  const http = createApiClient();
  const lifeRepository = new BrowserLifeRepository();
  const runtime = {
    id: () => createId(),
    now: () => new Date().toISOString(),
  };
  return {
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
        id: () => createId(),
        now: () => new Date().toISOString(),
      },
      new PoolQuestionRepository(lifeRepository),
    ),
    auth: createAuthUseCases(new SessionAuthRepository(http)),
    pets: createPetUseCases(new HttpPetListRepository(http)),
  };
}
export type Dependencies = ReturnType<typeof createDependencies>;
