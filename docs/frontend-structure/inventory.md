# 프론트 구조 전환 인벤토리 (#182)

상위 문서: [프론트엔드 책임과 폴더 구조 개편](README.md) (#164). 이 문서는 개편안 7절 1단계 결과로, `develop`(`a9c6378`) 기준 현재 코드와 서버 API를 대조한다.

## 한눈에

- `frontend/src`의 `.ts`/`.tsx` 180개, 테스트 15개 파일(`tests/*.test.ts`, 114개 test)이다.
- 화면이 실제로 서버를 부르는 곳은 **인증·내 정보**뿐이다. 반려동물·일기·질문·엽서·커뮤니티·가족·관리자는 모두 브라우저 저장소(`localStorage`)에서 처리한다.
- 서버 API(`HttpPetListRepository`, `Http*Walk*`, `HttpMapPinRepository`)는 만들어져 있지만, 화면의 반려동물이 브라우저 UUID라서 서버 경로를 타지 않는다. 지도·산책은 반려동물 id가 양의 정수일 때만 서버로 간다.
- 백엔드에는 반려동물·일기·질문·엽서·게시글·첨부 API가 이미 있으나 화면과 연결되지 않았다. 연결은 담당 이슈(#85, #104, #129 등)가 진행 중이다.

## 1. 라우트와 화면

URL은 그대로 둔다. 라우트 파일은 `src/app` → `app`으로 옮기기만 한다.

| 라우트 | 화면 코드(현재) | 데이터 출처(현재) |
| --- | --- | --- |
| `/`, `(auth)/sign-in`, `sign-up`, `reset-password`, `offline` | `workspace/.../LandingScreen`, `auth/.../AuthScreen`, `life/.../ResetPasswordScreen`, `OfflineScreen` | 인증 서버 + 데모 세션 |
| `/home`, `/me`, `/me/activity`, `/settings` | `workspace/.../HomeScreen`, `AccountScreen`, `life/.../SettingsScreen` | 회원 서버 + 브라우저 |
| `/pets`, `/pets/new`, `/pets/[petId]`, `settings/mode` | `workspace/.../PetScreens`, `life/.../ModeScreen` | 브라우저 |
| `pets/[petId]/diary/**`, `records/**`, `calendar`, `timeline` | `workspace/.../JournalScreens`, `ArchiveScreen` | 브라우저 |
| `pets/[petId]/questions/**` | `workspace/.../JournalScreens`, `useCurrentQuestion`, `life/.../QuestionHistory` | 브라우저 + `demoQuestions` |
| `pets/[petId]/media` | `life/.../MediaScreen` | 브라우저 |
| `pets/[petId]/map`, `map/walk`, `map/walks/**` | `map/.../MapScreen`(산책가기·산책기록·기억핀 탭, `map/walk`는 산책가기 탭), `WalkWidget`, `WalkHistory` | 서버(정수 id) / 데모 샘플 |
| `pets/[petId]/garden/**`(우체통·엽서) | `life/.../MailboxScreen`, `PostcardPaper` | 브라우저 + `DemoPostcardGenerator` |
| `pets/[petId]/family`, `family/invite/[inviteId]` | `life/.../FamilyScreen` | 브라우저 |
| `/community/**` | `life/.../CommunityScreen` | 브라우저 |
| `/admin/{members,notices,questions,reports}` | `life/.../AdminScreen` | 브라우저 |

## 2. 서버 호출 코드와 서버 API 대응

| 도메인 | 프론트가 부르는 API | 서버에 있지만 화면이 안 쓰는 API | 서버에 없음 |
| --- | --- | --- | --- |
| member/auth | `POST /auth/signup`, `/auth/login`, `/auth/logout`, `GET·PATCH /members/me` | `POST /auth/oauth/{provider}` | 비밀번호 재설정, 탈퇴(#155) |
| pet | `GET /pets`(구현만 있고 화면 미사용) | `POST·GET·PATCH·DELETE /pets/{id}`, `PATCH /pets/{id}/mode` | — |
| map | `GET /maps/pins`, `GET·POST /maps/walks`, `/walks/{id}/points`, `/walks/{id}/end` | — | 현재 산책 조회(#167) |
| diary | — | `/diaries` CRUD, `/diaries/calendar`, 첨부 조회 | — |
| question | — | `/questions/prompts/current`, 답변 CRUD, `/admin/questions` | 관리자 수정·활성 전환(#181 진행 중) |
| media | — | `/media/uploads`, `/media/{id}/content`, 미리보기·삭제 | — |
| postcard | — | `/postcards` 목록·생성·사용량·상세·이미지·삭제 | — |
| community | — | `/posts` CRUD, `/posts/categories` | 댓글, 신고 접수(#112) |
| family | — | — | 초대·역할(#133) |
| admin | — | `GET /admin/reports` | 회원 관리, 공지, 신고 처리(#92) |

## 3. 브라우저 저장 키

| 키 | 코드 | 정리 방향 |
| --- | --- | --- |
| `pawmento.workspace.v2.{accountId}` (+ 이관 대상 `v1`) | `workspace/adapters/outbound/BrowserWorkspaceRepository.ts`, `workspaceStorage.ts` | 목업으로 이동, v1→v2 이관 코드 제거 |
| `pawmento.life.v1` | `life/adapters/outbound/BrowserLifeRepository.ts` | 목업으로 이동 |
| `pawmento.activeWalk.v2.{owner}.{petId}` | `map/adapters/outbound/LocalActiveWalkStore.ts` | **유지**. #167 대체 확인 뒤 제거 |

## 4. 데모·출처 혼합 코드

| 코드 | 하는 일 | 정리 방향 |
| --- | --- | --- |
| `auth/.../SessionAuthRepository`, `DemoAuthRepository`, `auth/domain/account.ts`의 `isDemoAccount` | 데모 시작이면 탭 로컬 세션, 아니면 HTTP | 목업 세션과 실제 세션 분리. 체험 진입점은 #168 |
| `map/.../PetScoped{Walk,WalkHistory,MapPin}Repository`, `isServerPetId`, `isDemoWalkId` | 반려동물 id 형식으로 서버/데모 선택 | 개편안 5절이 금지한 자동 분기. 목업 설정으로 대체 |
| `DemoWalkRepository`, `demoWalkSamples`, `DemoMapPinRepository` | 산책·핀 샘플 | `mocks/fixtures`·`handlers` |
| `app/demoQuestions.ts`, `life/.../PoolQuestionRepository` | 질문 풀·데모 답변 | `mocks/fixtures` |
| `life/.../DemoPostcardGenerator`, `postcardScenes` | 엽서 데모 그림 | `mocks` (그림 구성은 화면 표시라 `features/postcard`에 남길지 이동 때 확인) |
| `life/.../SharedWorkspaceRepository` | workspace와 life 저장소를 묶음 | 목업 내부로 이동 |
| `app/AppProvider.tsx`(212줄) 데모 시드 | 데모 시작 시 반려동물·일기·답변 생성 | 목업 setup으로 이동 |

## 5. 프론트에 있는 업무 판단

개편안 2절 기준 백엔드로 통일할 대상이다. 지금은 브라우저 저장 기능이 이것 없이는 돌지 않는다.

- `life/application/useCases.ts`: `requireAdmin`, `requireOwner`, 가족 초대·역할·탈퇴, 엽서 하루 제한, 신고 처리, 공지·질문 관리
- `workspace/application/useCases.ts`: `validatePet`, 모드 전환, 반려동물 삭제
- `question/application/useCases.ts`: 현재 질문 배정과 진행
- `shared/application/JournalStore.ts`: `requireJournalAuthor`, `registrationDate`(등록일 확정)
- `map/application/WalkRecorder.ts`: 좌표 버퍼·전송(브라우저 작업이라 남김)

## 6. 테스트

| 파일 | 개수 | 대상 | 분류 |
| --- | --- | --- | --- |
| `hexagonal-architecture.test.ts` | 5 | 계층 의존 | 새 의존·목업·저장 경계 검사로 대체 |
| `workspace.test.ts`, `workspace-migration.test.ts` | 10, 4 | 브라우저 반려동물·기록, v1 이관 | 목업 시나리오로 이동, 이관 테스트 제거 |
| `life.test.ts`, `postcard-demo.test.ts` | 9, 3 | 가족·커뮤니티·엽서 업무 | 목업 시나리오로 이동, 업무 검증은 백엔드 담당 |
| `question-workflow.test.ts`, `demo-questions.test.ts` | 7, 2 | 질문 진행 | 목업 시나리오로 이동 |
| `session-source.test.ts` | 4 | 데모/HTTP 세션 | 목업·실제 분리 검사로 변경 |
| `walk.test.ts`, `walk-history.test.ts`, `map-pins.test.ts`, `location-picker.test.ts` | 18, 21, 14, 5 | 산책·지도 API·화면 보조 | 경로만 갱신 |
| `mypage-home.test.ts`, `date-navigation.test.ts`, `id.test.ts` | 8, 2, 2 | 화면 보조 | 경로만 갱신 |

테스트를 줄이거나 옮기면 `docs/testing-coverage.md`에 기록한다.

## 7. 보존할 사용자 동작

- 모든 URL과 `(auth)`·`(app)` 레이아웃, 일상/정원 모드 화면, 질문/다이어리 분리, 반응형·키보드 흐름
- 사진 위치 미동의 시 전송 전 GPS 제거와 실패 시 업로드 중단
- 미저장 입력 경고(`shared/unsavedChanges.ts`, `UnsavedChangesProvider`)
- 진행 중 산책 새로고침 복원(`LocalActiveWalkStore`, #167 전까지)
- 데모로 시작하기(운영 체험 진입점, #168 전까지)

## 8. 겹치는 작업

| 이슈 | 담당 | 겹치는 곳 |
| --- | --- | --- |
| #85, #180 | D-Gyun103 | 반려동물 화면·API 연결 |
| #129, #104 | isle-blu | 엽서·커뮤니티 화면 API 연결 |
| #167 | jangabc3 | 산책 화면 복원(3~5단계) |
| #172 | jangabc3 | 정보 지도 화면 |
| #168 | isle-blu, LuvZian | 데모 진입점 |
