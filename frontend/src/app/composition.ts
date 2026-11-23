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
import { createMapUseCases } from "../map/application/useCases.ts";
import { HttpMapPinRepository } from "../map/adapters/outbound/HttpMapPinRepository.ts";
import { DemoMapPinRepository } from "../map/adapters/outbound/DemoMapPinRepository.ts";
import { PetScopedMapPinRepository } from "../map/adapters/outbound/PetScopedMapPinRepository.ts";
import { createWalkUseCases } from "../map/application/walkUseCases.ts";
import { HttpWalkRepository } from "../map/adapters/outbound/HttpWalkRepository.ts";
import { DemoWalkRepository } from "../map/adapters/outbound/DemoWalkRepository.ts";
import { PetScopedWalkRepository } from "../map/adapters/outbound/PetScopedWalkRepository.ts";
import { createWalkHistoryUseCases } from "../map/application/walkHistoryUseCases.ts";
import { HttpWalkHistoryRepository } from "../map/adapters/outbound/HttpWalkHistoryRepository.ts";
import { PetScopedWalkHistoryRepository } from "../map/adapters/outbound/PetScopedWalkHistoryRepository.ts";
import { LocalActiveWalkStore } from "../map/adapters/outbound/LocalActiveWalkStore.ts";

export function createDependencies() {
  const http = createApiClient();
  const lifeRepository = new BrowserLifeRepository();
  // 데모 산책 기록과 날짜별 보기가 같은 메모리를 보도록 한 곳에서 만든다
  const demoWalks = new DemoWalkRepository();
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
    walk: createWalkUseCases(
      new PetScopedWalkRepository(new HttpWalkRepository(http), demoWalks),
      new LocalActiveWalkStore(),
    ),
    walkHistory: createWalkHistoryUseCases(
      new PetScopedWalkHistoryRepository(
        new HttpWalkHistoryRepository(http),
        demoWalks,
      ),
    ),
    map: createMapUseCases(
      new PetScopedMapPinRepository(
        new HttpMapPinRepository(http),
        new DemoMapPinRepository(),
      ),
    ),
  };
}
export type Dependencies = ReturnType<typeof createDependencies>;
