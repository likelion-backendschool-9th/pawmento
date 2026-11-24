# 배포와 DB 구성

배포·DB 구성에서 정한 것과 그 이유, 아직 정하지 않은 것을 모은다. 실행 명령과 파일 역할은 [README의 실행과 확인](../README.md#실행과-확인)에 있다.

## 브랜치와 서버

| 브랜치 | 서버 | 배포 |
| --- | --- | --- |
| `develop` | 개발 서버. 다른 팀과 함께 쓰는 공용 서버 | 머지하면 검사 → 이미지를 GHCR에 올림 → 서버에 배포 (#10) |
| `main` | 운영 서버. 나중에 클라우드에 따로 구성 | 아직 없음 |

- `main`으로 바로 배포하지 않는다. `main`에 반영하려면 `develop → main` PR과 승인이 필요해서, 기능을 머지할 때마다 서버에서 확인하기 어렵다. `develop`은 자주 바뀌는 팀 확인용, `main`은 검증된 것만 올라가는 출시용으로 나눈다.
- 운영 서버가 생기면 `main` push에 배포 단계를 더한다. 브랜치 규칙은 바꾸지 않는다.

## 로컬·테스트 DB (#5)

Spring Initializr가 PostgreSQL, Docker Compose, Testcontainers를 골랐을 때 생성하는 표준 구성을 따른다.

| 상황 | DB를 띄우는 것 | 접속 정보 |
| --- | --- | --- |
| 로컬 실행 `bootRun` | Spring Boot Docker Compose 지원이 `backend/compose.yaml`을 실행 | 실행 중인 컨테이너에서 자동 연결 |
| 테스트·CI | Testcontainers (`TestcontainersConfiguration`) | `@ServiceConnection`이 자동 연결 |
| 개발 서버 | 루트 `compose.yaml` + `compose.prod.yaml`의 `db` 서비스 | 환경 변수 `SPRING_DATASOURCE_*` |

고른 이유:

- 팀원은 Docker만 켜 두면 실행과 테스트를 할 수 있다. 접속 정보를 설정하거나 DB를 먼저 띄울 필요가 없다.
- 처음에는 `.env`와 `DB_*` 환경 변수로 접속 정보를 넘겼다(닫힌 PR #6). 이 방식은 값이 빠졌을 때 `jdbc:postgresql://${DB_HOST}...`처럼 원인을 알기 어려운 오류가 나고, 로컬·CI·`.env` 세 곳의 값을 맞춰야 했다.
- CI에 DB 서비스 컨테이너를 두지 않는다. GitHub Actions 러너의 Docker로 Testcontainers가 로컬과 같은 방식으로 돈다.

알아 둘 것:

- `spring-boot-docker-compose`는 `developmentOnly`라 실행 파일(jar)과 Docker 이미지에 들어가지 않는다. 개발 서버에서는 compose 자동 실행이 일어나지 않고 환경 변수로만 연결한다.
- 로컬 DB 포트는 비어 있는 포트로 자동 지정된다. PC에 PostgreSQL이 이미 있어도 충돌하지 않는다. DB 도구로 접속할 때는 `backend/`에서 `docker compose port postgres 5432`로 확인한다.
- 앱을 꺼도 로컬 DB는 켜 둔다(`spring.docker.compose.lifecycle-management: start-only`).
- Compose 자동 실행은 테스트에서 동작하지 않는다(Spring Boot 기본값 `spring.docker.compose.skip.in-tests=true`). 그래서 테스트는 Testcontainers가 맡는다.
- Compose 자동 실행과 `@ServiceConnection`이 만든 접속 정보는 `spring.datasource.url` 설정보다 우선한다. 직접 지정한 DB를 쓰려면 자동 실행을 끈다(`spring.docker.compose.enabled=false`).

## 개발 서버 (#10)

```
인터넷 → 호스트 nginx (팀 도메인, HTTPS)
       → 127.0.0.1:TEAM_PORT → 팀 nginx (nginx/proxy.conf)
       → /api/ 는 backend:8080, 나머지는 frontend:3000
```

- 팀에 배정된 포트 하나로 프론트엔드와 백엔드를 함께 받아야 해서 팀 nginx를 둔다. 경로 규칙과 업로드 크기를 서버 관리자에게 요청하지 않고 팀이 정할 수 있다.
- HTTPS는 호스트 nginx가 처리한다. 호스트 nginx에 필요한 설정은 [`nginx/host.conf.example`](../nginx/host.conf.example)에 있다.
- HTTPS는 호스트 nginx에서 끝나므로 백엔드는 `X-Forwarded-Proto`를 믿어야 요청을 HTTPS로 인식한다(`SERVER_FORWARD_HEADERS_STRATEGY: native`, `compose.prod.yaml`). 로그인 쿠키의 `Secure`와 리다이렉트 주소가 여기에 달려 있다. Tomcat은 내부망 주소(Docker 네트워크 포함)에서 온 헤더만 믿는다. 팀 nginx(`nginx/proxy.conf`)는 호스트 nginx가 보낸 헤더를 그대로 넘긴다.
- 백엔드 API 경로는 `/api/v1`로 시작한다(#22). 팀 nginx는 `/api/` 전체를 백엔드로 넘기므로 버전이 바뀌어도 nginx는 고치지 않는다.
- compose 파일은 Docker 공식 관례대로 셋으로 나눈다.

  | 파일 | 역할 | 실행 |
  | --- | --- | --- |
  | `compose.yaml` | 공통 정의 (서비스, DB 연결, `nginx/proxy.conf`) | 혼자 쓰지 않음 |
  | `compose.override.yaml` | 로컬: Dockerfile로 직접 빌드, `localhost:8000` | `docker compose up --build` (자동으로 합쳐짐) |
  | `compose.prod.yaml` | 서버: GHCR 이미지, 팀 포트, `restart`, `.env` 필수 값 | `docker compose -f compose.yaml -f compose.prod.yaml up -d` |

  - 로컬과 서버가 같은 공통 정의와 같은 Dockerfile·nginx 설정을 쓴다. 서버에서만 드러나던 문제(이미지 빌드, nginx 경로, 컨테이너 사이 연결)를 머지 전에 로컬에서 볼 수 있다.
  - override는 아무 옵션 없이 자동으로 합쳐지므로 로컬용으로 둔다. 서버는 `-f`로 prod 파일을 직접 지정하고, 이때 override는 읽지 않는다.
  - 매일 개발은 지금처럼 `bootRun`(`backend/compose.yaml`의 DB)과 `npm run dev`로 한다. 루트 `compose.yaml`은 Docker 관례, `backend/compose.yaml`은 Spring Boot 표준(의존 서비스만)이라 폴더를 나눠 둘 다 지킨다. IDE에서 백엔드를 실행할 때 작업 폴더를 `backend/`로 두는 이유다.
- 서버에는 저장소를 clone하지 않는다. 배포 워크플로가 이미지를 만든 커밋의 `compose.yaml`, `compose.prod.yaml`, `nginx/proxy.conf`를 서버 `~/pawmento`에 복사한다(`appleboy/scp-action`). 그래서 서버의 설정과 이미지가 항상 같은 커밋에서 온다. `git pull`은 develop의 최신 커밋을 받아서, 머지가 이어지면 이미지보다 새 설정이 들어갈 수 있다. 서버에서 직접 만드는 것은 `.env`뿐이고, 복사할 때 덮어쓰지 않는다.

### 공용 서버에서 지킬 것

| 항목 | 방법 | 이유 |
| --- | --- | --- |
| 포트 | `TEAM_PORT` 하나만 `127.0.0.1`에 연다 | 밖에서는 호스트 nginx를 거쳐 도메인으로만 들어오게 한다 |
| 이름 | `COMPOSE_PROJECT_NAME`으로 네트워크·볼륨 이름을 나누고 `container_name`은 두지 않는다 | 다른 팀의 컨테이너·볼륨과 이름이 겹치지 않게 한다 |
| 이미지 정리 | `docker image prune`에 저장소 라벨(`org.opencontainers.image.source`) 필터를 건다 | 다른 팀 이미지를 지우지 않는다 |

### 비밀값을 두는 곳

| 값 | 위치 | 이유 |
| --- | --- | --- |
| 서버 접속 정보 (`DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_PORT`, `DEPLOY_HOST_FINGERPRINT`) | GitHub 환경 `develop`의 Secrets | 배포 작업만 읽는다 |
| 앱 비밀값 (DB 비밀번호, 인증 키 등) | 서버의 `.env` (`.env.prod.example` 참고) | 워크플로 로그와 이미지에 남지 않는다 |
| 알림 웹훅 (`CAMPFIRE_WEBHOOK_URL`) | 저장소 Secrets | 노출돼도 피해가 적고, 환경 지정 없이 읽어야 한다 |
| 네이버 지도 Client ID (`NAVER_MAP_CLIENT_ID`) | 저장소 Variables (Secrets가 아님) | 브라우저 번들에 공개되는 값이라 비밀이 아니다. 프론트를 빌드할 때 필요해서 서버 `.env`로는 넘길 수 없다. Client Secret은 서버 `.env`에만 둔다 |

서버 접속 정보를 넣을 때 주의할 것:

- `DEPLOY_HOST`는 GitHub Actions 러너가 인터넷에서 SSH로 닿는 주소(도메인이나 공인 IP)를 넣는다. 내 PC의 `~/.ssh/config` 별칭이나 사설 IP를 넣으면 러너가 찾지 못한다(`lookup ... no such host`). 별칭의 실제 주소는 `ssh -G <별칭>`의 `hostname`·`port`로 확인한다.
- `DEPLOY_HOST_FINGERPRINT`는 서버 호스트 키의 `SHA256:...` 지문이다. 배포 액션(Go SSH)은 ECDSA 키를 ED25519보다 먼저 고르므로 `ssh-keygen -lf /etc/ssh/ssh_host_ecdsa_key.pub`의 값을 넣는다. 비어 있으면 호스트 키 확인을 건너뛴다.
- 배포 키는 배포 전용으로 따로 만들어 서버 계정의 `authorized_keys`에 한 줄로 넣는다. 문제가 생기면 그 줄만 지운다.

백엔드가 서버에서 받는 값은 `compose.prod.yaml`의 backend 환경 변수에 있다. 로그인 서명 키 `JWT_SECRET`(#14)은 없으면 재배포할 때마다 로그인이 풀려서 필수로 받고, 네이버 지도 키 `NAVER_MAP_CLIENT_ID`·`NAVER_MAP_CLIENT_SECRET`(#23)은 비어 있으면 주소 조회만 건너뛴다. 새 값이 생기면 `compose.prod.yaml`, `.env.prod.example`, 서버 `.env` 세 곳에 더한다.

AI 엽서(#103)는 생성기 `PAWMENTO_POSTCARD_GENERATOR`의 기본값이 `fake`라서 키 없이도 배포된다. 실제 그림은 Cloudflare Workers AI로 만든다([AI 엽서](postcard/generate.md)).

1. Cloudflare 대시보드에서 계정 ID를 확인하고, 권한을 **Workers AI**만 준 API 토큰을 만든다.
2. 서버 `.env`에 `CLOUDFLARE_ACCOUNT_ID`, `CLOUDFLARE_AI_TOKEN`을 넣고 `PAWMENTO_POSTCARD_GENERATOR=cloudflare`로 바꾼 뒤 backend를 다시 띄운다. 생성기가 `cloudflare`인데 두 값 중 하나라도 비어 있으면 백엔드가 시작하지 않는다.
3. 모델은 `PAWMENTO_POSTCARD_CLOUDFLARE_MODEL`(기본 `@cf/black-forest-labs/flux-2-klein-9b`)로 바꾼다. 팀 전체 하루 상한 `PAWMENTO_POSTCARD_DAILY_LIMIT_TOTAL`은 무료 한도(하루 10,000 Neuron)를 쓰는 동안 3이다. 앱은 한국 시간 자정, Cloudflare는 UTC 자정(한국 오전 9시)에 한도를 새로 세서 Cloudflare 하루에 앱의 이틀 치(최대 6장, 약 8,400 Neuron)가 걸칠 수 있다.

생성기를 바꾸면 그 전에 요청해 대기 중이던 엽서는 동의받은 생성기와 달라 `FAILED`가 되고 회원당 횟수가 돌아온다. 무료 한도를 넘긴 호출은 Cloudflare가 `429`(오류 번호 `3036`)로 거절하고, 엽서는 실패로 끝난다.

### 배포 버전 고정과 되돌리기

이미지에는 `sha-<커밋 7자리>`와 `latest` 태그가 붙는다. 배포 워크플로는 머지한 커밋의 `sha-` 태그를 `IMAGE_TAG`로 넘긴다.

- 되돌리기: 서버의 `~/pawmento`에서 이전 태그로 다시 띄운다. `.env`의 `COMPOSE_FILE`로 `-f`를 생략할 수 있다.

  ```bash
  IMAGE_TAG=sha-<이전 커밋> docker compose up -d --no-build
  ```

- 시연·외부 테스트 기간: 머지하면 자동으로 다시 배포되므로, 그 기간에는 `develop` 머지를 멈추거나 배포 작업을 끈다. 끄는 방법은 GitHub 환경 `develop`의 `DEPLOY_HOST`를 잠시 지우는 것이다(배포 단계만 건너뛰고 검사·이미지 업로드는 계속된다).

## PostgreSQL 버전

`postgres:18-alpine`을 세 곳에서 쓴다. 버전을 바꿀 때는 함께 바꾼다.

- `backend/compose.yaml` (로컬)
- `backend/src/test/java/com/pawmento/TestcontainersConfiguration.java` (테스트·CI)
- `compose.yaml` (로컬 전체 구성·서버 공통)

PostgreSQL 18부터 컨테이너의 데이터 위치가 `/var/lib/postgresql/<버전>/docker`로 바뀌었다. 그래서 볼륨은 상위 폴더 `/var/lib/postgresql`에 연결한다.

## 사진 저장 방식

스프린트 1의 일기에 사진 업로드가 있다. 이후 영상·음성도 올린다. 서비스 원칙상 미디어 접근 권한을 보호하고, 보호자가 기록을 내보낼 수 있어야 한다.

| 기준 | 서버 디스크 (Docker 볼륨) | 객체 스토리지 (S3 호환) |
| --- | --- | --- |
| 구현 | 파일 저장·읽기만 하면 된다. 가장 쉽다 | SDK 연동, 버킷·권한 설정이 필요하다 |
| 접근 권한 | 백엔드가 권한을 확인한 뒤 파일을 내려준다 | 서명된 URL(유효 시간이 있는 주소)로 내려준다 |
| 용량 | 공용 서버의 남은 디스크에 묶인다. 다른 팀과 나눠 쓴다 | 쓰는 만큼 늘어난다 |
| 백업 | 직접 해야 한다 | 서비스가 내구성을 보장한다 |
| 비용 | 추가 비용 없음 | 저장 용량·전송량만큼 든다 |
| 운영 서버(클라우드)로 옮길 때 | 파일을 옮기고 저장 코드를 바꿔야 한다 | 그대로 쓰거나 설정만 바꾼다 |

제안:

1. 저장소를 바꿀 수 있게 **파일 저장을 인터페이스(포트) 뒤에 둔다.** 도메인 코드는 "저장한다·읽는다"만 알고, 디스크인지 객체 스토리지인지는 구현(어댑터)이 정한다. 백엔드 구조(헥사고날)를 정할 때 함께 정한다.
2. **개발 서버는 서버 디스크(Docker 볼륨)로 시작한다.** 지금 필요한 건 기능 확인이고, 추가 비용과 외부 계정 없이 바로 쓸 수 있다.
3. **운영 서버는 객체 스토리지를 쓴다.** 클라우드로 옮길 때 구현만 바꾼다.

정한 것:

- **개발 서버는 Docker 볼륨에 저장한다.** 위 제안 1·2번대로 간다. 운영 서버의 저장소는 클라우드로 옮길 때 다시 정한다. (#47)
  - 볼륨은 `external`이 아닌 일반 볼륨 `media-data`다. DB 볼륨처럼 `COMPOSE_PROJECT_NAME`으로 이름이 나뉘어 공용 서버에서 다른 팀과 겹치지 않고, 팀원이 로컬에서 볼륨을 따로 만들 필요가 없다. (#47)
  - 백엔드 컨테이너의 `/var/lib/pawmento/media`에 연결하고, 경로는 환경 변수 `PAWMENTO_MEDIA_ROOT`로 넘긴다. 백엔드는 root가 아닌 `app` 사용자로 돌기 때문에 이미지에서 이 폴더를 `app` 소유로 만들어 둔다. 새 볼륨은 처음 연결될 때 이 소유권을 이어받는다. (#47)
  - 업로드·검증·열람·정리의 세부 설계는 #34(`docs/media/README.md`)에 있다.
- **개발 서버는 지금 백업하지 않는다.** DB와 파일은 사라질 수 있다. 외부 테스터를 받기 전에 DB와 `media-data` 볼륨을 함께 백업·복구하는 절차를 만든다. 서버에서 `docker compose down -v`는 쓰지 않는다(볼륨까지 지워진다). (#47)
- **사진 한 장은 최대 15MB다.** 휴대폰 사진(보통 2~8MB, 고화질 10MB 이상)을 대부분 원본 그대로 받는다. 영상·음성은 크기가 달라 따로 정한다. (#4)
- **사진은 한 요청에 한 장씩 올린다.** 업로드 API는 사진 한 장을 받아 id를 돌려주고, 일기를 저장할 때는 그 id만 보낸다. 모바일 회선에서 한 장이 실패해도 그 장만 다시 올리면 되고, 장마다 진행률을 보여 줄 수 있다. 일기와 사진을 한 요청에 보내면 요청 크기가 장수만큼 커져 nginx 두 곳과 Spring의 제한을 모두 크게 올려야 한다. (#4)
- 크기 제한을 맞추는 곳 (#4)

  | 위치 | 값 | 비고 |
  | --- | --- | --- |
  | Spring `spring.servlet.multipart.max-file-size` | `15MB` | 기본값은 1MB. 사진 업로드 API를 만들 때 `application.yml`에 넣는다 |
  | Spring `spring.servlet.multipart.max-request-size` | `16MB` | 사진 한 장과 요청의 나머지 부분 |
  | 팀 nginx `nginx/proxy.conf`, 호스트 nginx `client_max_body_size` | `20m` | 지금 값 그대로. 한 요청이 한 장이라 여유가 있다 |

팀이 정할 것:

- 올리기만 하고 일기에 연결되지 않은 사진을 언제 지울지 (한 장씩 올리는 방식이라 생긴다). 정리 작업의 설계는 #34에 있고 시간 값은 #33에서 정한다
- 개발 서버의 디스크 여유 공간 (서버 관리자에게 확인)

## 아직 정하지 않은 것

- **외부 테스터용 서버**: 개발 서버는 머지할 때마다 바뀌고 데이터가 초기화될 수 있으며 백업이 없다. 테스터가 실제 기록을 오래 남긴다면 `main`을 공용 서버에 따로 띄우는 방법을 검토한다(팀 포트 추가 필요). 테스터 모집 시기·인원·기간을 정한 뒤 결정한다.
- **운영 서버**: 클라우드 종류, 운영 DB, 도메인
- **스키마 관리**: `ddl-auto`나 Flyway 같은 마이그레이션 도구. 백엔드 구조 작업에서 정한다. 외부 테스터를 받기 전에는 데이터를 지우지 않는 방식이어야 한다
- **모바일 확인**: 휴대폰에서 PC의 IP로 접속하면 HTTPS가 아니라 서비스 워커·PWA 설치·마이크 녹음이 동작하지 않는다. 방법(`next dev --experimental-https`, Android 포트 포워딩, 개발 서버)은 PWA 작업 때 정한다

## 참고

- Spring Boot 문서: Docker Compose 지원, Testcontainers, Service Connections (`docs.spring.io/spring-boot/reference/features/dev-services.html`)
- PostgreSQL 공식 이미지 문서: 18부터 바뀐 데이터 경로 (`PGDATA`)
- Next.js 문서: `next dev --experimental-https` (`frontend/node_modules/next/dist/docs/01-app/03-api-reference/06-cli/next.md`)
