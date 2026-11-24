import { createId } from "../lib/id.ts";
import { createApiClient } from "../lib/http/apiClient.ts";
import { assertDevelopmentMockAllowed } from "../mocks/setup.ts";
import { SharedWorkspaceRepository } from "../mocks/handlers/SharedWorkspaceRepository.ts";
import { BrowserLifeRepository } from "../mocks/handlers/BrowserLifeRepository.ts";
import { DemoPostcardGenerator } from "../mocks/handlers/DemoPostcardGenerator.ts";
import { PoolQuestionRepository } from "../mocks/handlers/PoolQuestionRepository.ts";
import { createLifeUseCases } from "../mocks/handlers/lifeUseCases.ts";

import { createWorkspaceUseCases } from "../mocks/handlers/workspaceUseCases.ts";
import { BrowserWorkspaceRepository } from "../mocks/handlers/BrowserWorkspaceRepository.ts";
import { createMemberSession } from "../features/member/session.ts";
import { DemoAuthRepository } from "../mocks/handlers/DemoAuthRepository.ts";
import { createPetApi } from "../features/pet/api/petApi.ts";
import { createPetManagementApi } from "../features/pet/api/petManagementApi.ts";
import { createMapPinApi } from "../features/map/api/mapPinApi.ts";
import { createWalkApi } from "../features/map/api/walkApi.ts";
import { createWalkHistoryApi } from "../features/map/api/walkHistoryApi.ts";
import { LocalActiveWalkStore } from "../features/map/LocalActiveWalkStore.ts";
import { createMapSource } from "../features/map/source.ts";
import { createWalkSession } from "../features/map/walkSession.ts";
import { DemoMapPinRepository } from "../mocks/handlers/DemoMapPinRepository.ts";
import { DemoWalkRepository } from "../mocks/handlers/DemoWalkRepository.ts";

export { demoQuestionInputs } from "../mocks/fixtures/demoQuestions.ts";

export function createDependencies() {
  // 개발용 목업을 운영 빌드에서 켜면 거부한다. 반려동물 id·세션 분기와 데모로 시작하기는 그대로다.
  assertDevelopmentMockAllowed();
  const http = createApiClient();
  const lifeRepository = new BrowserLifeRepository();
  // 데모 산책 기록과 날짜별 보기가 같은 메모리를 보도록 한 곳에서 만든다
  const demoWalks = new DemoWalkRepository();
  const mapSource = createMapSource({
    pins: createMapPinApi(http),
    walks: createWalkApi(http),
    history: createWalkHistoryApi(http),
    demoPins: new DemoMapPinRepository(),
    demoWalks,
  });
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
    auth: createMemberSession(http, new DemoAuthRepository()),
    pets: createPetApi(http),
    petManagement: createPetManagementApi(http),
    walk: createWalkSession(mapSource.walk, new LocalActiveWalkStore()),
    walkHistory: mapSource.walkHistory,
    map: mapSource.map,
  };
}
export type Dependencies = ReturnType<typeof createDependencies>;
