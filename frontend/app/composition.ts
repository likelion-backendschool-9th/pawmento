import { createId } from "../lib/id.ts";
import { createApiClient } from "../lib/http/apiClient.ts";
import {
  assertDevelopmentMockAllowed,
  isDevelopmentMockRequested,
} from "../mocks/setup.ts";
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
import { createFamilyApi } from "../features/family/api/familyApi.ts";
import { createPostApi } from "../features/community/api/postApi.ts";
import { createMapPinApi } from "../features/map/api/mapPinApi.ts";
import { createPlaceApi } from "../features/map/api/placeApi.ts";
import { selectPlaceRepository } from "../features/map/placeSource.ts";
import { createWalkApi } from "../features/map/api/walkApi.ts";
import { createWalkHistoryApi } from "../features/map/api/walkHistoryApi.ts";
import { LocalActiveWalkStore } from "../features/map/LocalActiveWalkStore.ts";
import { createMapSource } from "../features/map/source.ts";
import { createWalkSession } from "../features/map/walkSession.ts";
import { DemoMapPinRepository } from "../mocks/handlers/DemoMapPinRepository.ts";
import { DemoPlaceRepository } from "../mocks/handlers/DemoPlaceRepository.ts";
import { DemoEmailVerification } from "../mocks/handlers/DemoEmailVerification.ts";
import { createDemoOnboarding } from "../mocks/handlers/demoOnboarding.ts";
import { DemoWalkRepository } from "../mocks/handlers/DemoWalkRepository.ts";

export { demoDiarySamples } from "../mocks/fixtures/demoDiaries.ts";
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
  const life = createLifeUseCases(lifeRepository, new DemoPostcardGenerator(), runtime);
  const workspace = createWorkspaceUseCases(
    new SharedWorkspaceRepository(new BrowserWorkspaceRepository(), lifeRepository),
    runtime,
    new PoolQuestionRepository(lifeRepository),
  );
  return {
    life,
    workspace,
    onboardingPreview: createDemoOnboarding(workspace, life),
    emailVerificationPreview: { createSession: () => new DemoEmailVerification() },
    auth: createMemberSession(http, new DemoAuthRepository()),
    pets: createPetApi(http),
    petManagement: createPetManagementApi(http),
    family: createFamilyApi(http),
    // 커뮤니티 글 API. 데모 계정이면 화면이 selectCommunitySource로 life의 데모 글을 고른다(#104-S9)
    posts: createPostApi(http),
    walk: createWalkSession(mapSource.walk, new LocalActiveWalkStore()),
    walkHistory: mapSource.walkHistory,
    map: mapSource.map,
    // 정보 지도의 장소. 목업을 명시적으로 켰을 때만 예시 장소를 보이고, 그 밖에는 서버를 부른다(#172-S19)
    places: selectPlaceRepository({
      mockRequested: isDevelopmentMockRequested(),
      server: createPlaceApi(http),
      demo: new DemoPlaceRepository(),
    }),
  };
}
export type Dependencies = ReturnType<typeof createDependencies>;
