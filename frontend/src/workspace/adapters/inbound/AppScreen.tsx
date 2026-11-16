"use client";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { today } from "@/shared/date";
import { useApp } from "@/app/AppProvider";
import { AuthScreen } from "@/auth/adapters/inbound/AuthScreen";
import { Brand } from "@/shared/ui/Brand";
import { Shell } from "./AppShell";
import { HomeScreen } from "./HomeScreen";
import { EmptyState } from "./WorkspaceUi";
import { ArchiveScreen } from "./ArchiveScreen";
import { RecordForm, RecordDetail } from "./RecordScreens";
import { PetList, PetForm } from "./PetScreens";
import { AccountScreen } from "./AccountScreen";
import { CommunityScreen } from "@/life/adapters/inbound/CommunityScreen";
import {
  ModeScreen,
  GardenScreen,
  QuestionHistory,
} from "@/life/adapters/inbound/GardenScreen";
import { MediaScreen } from "@/life/adapters/inbound/MediaScreen";
import { MailboxScreen } from "@/life/adapters/inbound/MailboxScreen";
import { FamilyScreen } from "@/life/adapters/inbound/FamilyScreen";
import { AdminScreen } from "@/life/adapters/inbound/AdminScreen";
import {
  SettingsScreen,
  OfflineScreen,
  ResetPasswordScreen,
} from "@/life/adapters/inbound/SettingsScreen";
import styles from "./Workspace.module.css";
export function AppScreen({ path }: { path: string[] }) {
  const app = useApp();
  const route = "/" + path.join("/");
  if (route === "/offline") return <OfflineScreen />;
  if (route === "/reset-password") return <ResetPasswordScreen />;
  if (route === "/sign-in" || route === "/sign-up")
    return <AuthScreen signUp={route === "/sign-up"} />;
  const valid =
    [
      "/home",
      "/me",
      "/me/activity",
      "/pets",
      "/pets/new",
      "/settings",
    ].includes(route) ||
    /^\/community(?:\/[^/]+(?:\/edit)?)?$/.test(route) ||
    /^\/admin\/(reports|questions|notices|members)$/.test(route) ||
    /^\/family\/invite\/[^/]+$/.test(route) ||
    /^\/pets\/[^/]+(?:\/(?:diary|records)\/[^/]+(?:\/edit)?|\/(?:calendar|timeline|media|family|questions)|\/settings\/mode|\/garden(?:\/mailbox(?:\/[^/]+)?|\/postcards\/new)?)?$/.test(
      route,
    );
  if (!valid)
    return (
      <div className={styles.notFound}>
        <Brand />
        <h1>길을 조금 벗어났네요.</h1>
        <p>우리의 이야기가 있는 곳으로 돌아가요.</p>
        <Link className="button primary" href="/home">
          홈으로 돌아가기
        </Link>
      </div>
    );
  if (!app.account) return <AuthScreen returnTo={route} />;
  return <AuthenticatedScreen key={app.account.id} path={path} />;
}
function AuthenticatedScreen({ path }: { path: string[] }) {
  const app = useApp();
  const search = useSearchParams();
  const route = "/" + path.join("/");
  const petId =
    path[0] === "pets" && path[1] !== "new" ? path[1] : app.state.selectedPetId;
  const pet = app.state.pets.find((item) => item.id === petId);
  let content: React.ReactNode;
  if (route === "/me") content = <AccountScreen />;
  else if (route === "/me/activity") content = <CommunityScreen activity />;
  else if (route === "/settings") content = <SettingsScreen />;
  else if (path[0] === "community")
    content = <CommunityScreen segments={path.slice(1)} />;
  else if (path[0] === "admin") content = <AdminScreen section={path[1]} />;
  else if (path[0] === "family") content = <FamilyScreen inviteId={path[2]} />;
  else if (route === "/pets") content = <PetList />;
  else if (route === "/pets/new") content = <PetForm key="new" />;
  else if (!pet)
    content = (
      <EmptyState
        title="우리의 첫 이야기를 시작해요"
        body="반려동물을 등록하거나 가족 초대에 참여하면 함께 기록할 수 있어요."
        href="/pets/new"
        action="반려동물 등록하기"
      />
    );
  else if (path[2] === "calendar")
    content = <ArchiveScreen key={pet.id + "calendar"} pet={pet} calendar />;
  else if (path[2] === "timeline")
    content = <ArchiveScreen key={pet.id + "timeline"} pet={pet} />;
  else if (path[2] === "media") content = <MediaScreen pet={pet} />;
  else if (path[2] === "family") content = <FamilyScreen pet={pet} />;
  else if (path[2] === "questions") content = <QuestionHistory pet={pet} />;
  else if (path[2] === "settings")
    content = <ModeScreen key={`${pet.id}:${pet.mode}`} pet={pet} />;
  else if (
    path[2] === "garden" &&
    (path[3] === "mailbox" || path[3] === "postcards")
  )
    content = (
      <MailboxScreen
        key={pet.id + path.slice(4).join("/")}
        pet={pet}
        segments={path[3] === "postcards" ? ["new"] : path.slice(4)}
      />
    );
  else if (path[2] === "garden") content = <GardenScreen pet={pet} />;
  else if (path[2] === "diary" || path[2] === "records") {
    const record = app.state.records.find(
      (item) => item.id === path[3] && item.petId === pet.id,
    );
    content =
      path[3] === "new" ? (
        pet.accessRole === "VIEWER" ? (
          <EmptyState
            title="열람 권한으로 보고 있어요"
            body="기록을 남기려면 소유자에게 편집자 역할을 요청해 주세요."
            href={`/pets/${pet.id}/timeline`}
            action="기록 보기"
          />
        ) : (
          <RecordForm
            key={pet.id + path[2] + (search.get("date") ?? today())}
            pet={pet}
            diary={path[2] === "diary"}
          />
        )
      ) : !record ? (
        <EmptyState
          title="기록을 찾을 수 없어요"
          body="삭제되었거나 다른 친구의 기록일 수 있어요."
          href={`/pets/${pet.id}/timeline`}
          action="기록으로 돌아가기"
        />
      ) : path[4] === "edit" && pet.accessRole !== "VIEWER" ? (
        <RecordForm
          key={record.id}
          pet={pet}
          record={record}
          diary={record.type === "DIARY"}
        />
      ) : (
        <RecordDetail pet={pet} record={record} />
      );
  } else if (path[0] === "pets" && path[1])
    content =
      pet.accessRole && pet.accessRole !== "OWNER" ? (
        <EmptyState
          title="공유된 반려동물이에요"
          body="프로필은 소유자가 관리합니다. 함께 기록한 이야기를 만나보세요."
          href={`/pets/${pet.id}/timeline`}
          action="기록 보기"
        />
      ) : (
        <PetForm key={pet.id} pet={pet} />
      );
  else content = <HomeScreen pet={pet} />;
  return (
    <Shell pet={pet} route={route}>
      {app.loading && (
        <div className="loading-bar" role="status">
          기록을 불러오고 있어요…
        </div>
      )}
      {app.error && (
        <div className="error" role="alert">
          {app.error}
          <button
            className="text-link"
            onClick={() => void app.refresh().catch(() => undefined)}
          >
            다시 시도
          </button>
        </div>
      )}
      {content}
    </Shell>
  );
}
