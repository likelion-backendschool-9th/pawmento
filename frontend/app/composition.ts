import { createId } from "../lib/id.ts";
import { createApiClient } from "../lib/http/apiClient.ts";
import { assertDevelopmentMockAllowed } from "../mocks/setup.ts";
import { SharedWorkspaceRepository } from "../mocks/handlers/SharedWorkspaceRepository.ts";
import { BrowserLifeRepository } from "../mocks/handlers/BrowserLifeRepository.ts";
import { DemoPostcardGenerator } from "../mocks/handlers/DemoPostcardGenerator.ts";
import { PoolQuestionRepository } from "../mocks/handlers/PoolQuestionRepository.ts";
import { createLifeUseCases } from "../features/life/application/useCases.ts";

import { createWorkspaceUseCases } from "../features/workspace/application/useCases.ts";
import { BrowserWorkspaceRepository } from "../mocks/handlers/BrowserWorkspaceRepository.ts";
import { createAuthUseCases } from "../features/auth/application/auth.ts";
import { DemoAuthRepository } from "../mocks/handlers/DemoAuthRepository.ts";
import { SessionAuthRepository } from "../features/auth/adapters/outbound/SessionAuthRepository.ts";
import { createPetUseCases } from "../features/pets/application/useCases.ts";
import { HttpPetListRepository } from "../features/pets/adapters/outbound/HttpPetListRepository.ts";
import { createMapUseCases } from "../features/map/application/useCases.ts";
import { HttpMapPinRepository } from "../features/map/adapters/outbound/HttpMapPinRepository.ts";
import { DemoMapPinRepository } from "../mocks/handlers/DemoMapPinRepository.ts";
import { PetScopedMapPinRepository } from "../features/map/adapters/outbound/PetScopedMapPinRepository.ts";
import { createWalkUseCases } from "../features/map/application/walkUseCases.ts";
import { HttpWalkRepository } from "../features/map/adapters/outbound/HttpWalkRepository.ts";
import { DemoWalkRepository } from "../mocks/handlers/DemoWalkRepository.ts";
import { PetScopedWalkRepository } from "../features/map/adapters/outbound/PetScopedWalkRepository.ts";
import { createWalkHistoryUseCases } from "../features/map/application/walkHistoryUseCases.ts";
import { HttpWalkHistoryRepository } from "../features/map/adapters/outbound/HttpWalkHistoryRepository.ts";
import { PetScopedWalkHistoryRepository } from "../features/map/adapters/outbound/PetScopedWalkHistoryRepository.ts";
import { LocalActiveWalkStore } from "../features/map/adapters/outbound/LocalActiveWalkStore.ts";

export { demoQuestionInputs } from "../mocks/fixtures/demoQuestions.ts";

export function createDependencies() {
  // 개발용 목업을 운영 빌드에서 켜면 거부한다. 반려동물 id·세션 분기와 데모로 시작하기는 그대로다.
  assertDevelopmentMockAllowed();
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
    auth: createAuthUseCases(
      new SessionAuthRepository(http, new DemoAuthRepository()),
    ),
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
