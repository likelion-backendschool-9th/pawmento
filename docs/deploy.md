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
| 개발 서버 | `compose.develop.yaml`의 `db` 서비스 | 환경 변수 `SPRING_DATASOURCE_*` |

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
       → 127.0.0.1:TEAM_PORT → 팀 nginx (nginx/develop.conf)
       → /api/ 는 backend:8080, 나머지는 frontend:3000
```

- 팀에 배정된 포트 하나로 프론트엔드와 백엔드를 함께 받아야 해서 팀 nginx를 둔다. 경로 규칙과 업로드 크기를 서버 관리자에게 요청하지 않고 팀이 정할 수 있다.
- HTTPS는 호스트 nginx가 처리한다. 호스트 nginx에 필요한 설정은 [`nginx/host.conf.example`](../nginx/host.conf.example)에 있다.
- 백엔드 API 경로는 `/api/`로 시작한다는 전제다. 백엔드·프론트엔드 구조를 정할 때 다르게 정하면 `nginx/develop.conf`를 고친다.

### 공용 서버에서 지킬 것

| 항목 | 방법 | 이유 |
| --- | --- | --- |
| 포트 | `TEAM_PORT` 하나만 `127.0.0.1`에 연다 | 밖에서는 호스트 nginx를 거쳐 도메인으로만 들어오게 한다 |
| 이름 | `COMPOSE_PROJECT_NAME`으로 네트워크·볼륨 이름을 나누고 `container_name`은 두지 않는다 | 다른 팀의 컨테이너·볼륨과 이름이 겹치지 않게 한다 |
| 이미지 정리 | `docker image prune`에 저장소 라벨(`org.opencontainers.image.source`) 필터를 건다 | 다른 팀 이미지를 지우지 않는다 |

### 비밀값을 두는 곳

| 값 | 위치 | 이유 |
| --- | --- | --- |
| 서버 접속 정보 (`DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_PORT`) | GitHub 환경 `develop`의 Secrets | 배포 작업만 읽는다 |
| 앱 비밀값 (DB 비밀번호, 인증 키 등) | 서버의 `.env` (`.env.develop.example` 참고) | 워크플로 로그와 이미지에 남지 않는다 |
| 알림 웹훅 (`CAMPFIRE_WEBHOOK_URL`) | 저장소 Secrets | 노출돼도 피해가 적고, 환경 지정 없이 읽어야 한다 |

인증 키처럼 인증 방식에 따라 생기는 값은 인증 작업 때 서버 `.env`와 `.env.develop.example`에 더한다.

### 배포 버전 고정과 되돌리기

이미지에는 `sha-<커밋 7자리>`와 `latest` 태그가 붙는다. 배포 워크플로는 머지한 커밋의 `sha-` 태그를 `IMAGE_TAG`로 넘긴다.

- 되돌리기: 서버의 저장소 폴더에서 이전 태그로 다시 띄운다.

  ```bash
  IMAGE_TAG=sha-<이전 커밋> docker compose -f compose.develop.yaml up -d --no-build
  ```

- 시연·외부 테스트 기간: 머지하면 자동으로 다시 배포되므로, 그 기간에는 `develop` 머지를 멈추거나 배포 작업을 끈다. 끄는 방법은 GitHub 환경 `develop`의 `DEPLOY_HOST`를 잠시 지우는 것이다(배포 단계만 건너뛰고 검사·이미지 업로드는 계속된다).

## PostgreSQL 버전

`postgres:18-alpine`을 세 곳에서 쓴다. 버전을 바꿀 때는 함께 바꾼다.

- `backend/compose.yaml` (로컬)
- `backend/src/test/java/com/pawmento/TestcontainersConfiguration.java` (테스트·CI)
- `compose.develop.yaml` (개발 서버)

PostgreSQL 18부터 컨테이너의 데이터 위치가 `/var/lib/postgresql/<버전>/docker`로 바뀌었다. 그래서 볼륨은 상위 폴더 `/var/lib/postgresql`에 연결한다.

## 사진 저장 방식 (제안)

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

팀이 정할 것:

- 위 제안대로 갈지, 처음부터 객체 스토리지(예: 개발 서버에 S3 호환 저장소를 함께 띄우기)로 갈지
- 사진 한 장의 최대 크기. 팀 nginx·호스트 nginx의 `client_max_body_size`(지금 20m)와 Spring의 업로드 제한을 같이 맞춘다
- 개발 서버의 디스크 여유 공간 (서버 관리자에게 확인)

## 아직 정하지 않은 것

- **외부 테스터용 서버**: 개발 서버는 머지할 때마다 바뀌고 데이터가 초기화될 수 있으며 백업이 없다. 테스터가 실제 기록을 오래 남긴다면 `main`을 공용 서버에 따로 띄우는 방법을 검토한다(팀 포트 추가 필요). 테스터 모집 시기·인원·기간을 정한 뒤 결정한다.
- **운영 서버**: 클라우드 종류, 운영 DB, 도메인
- **스키마 관리**: `ddl-auto`나 Flyway 같은 마이그레이션 도구. 백엔드 구조 작업에서 정한다. 외부 테스터를 받기 전에는 데이터를 지우지 않는 방식이어야 한다
- **forwarded 헤더**: 백엔드가 `X-Forwarded-Proto` 등을 믿도록 하는 설정(`SERVER_FORWARD_HEADERS_STRATEGY`)은 인증 작업 때 넣는다. 리다이렉트와 소셜 로그인 콜백 주소가 HTTPS로 만들어지려면 필요하다
- **모바일 확인**: 휴대폰에서 PC의 IP로 접속하면 HTTPS가 아니라 서비스 워커·PWA 설치·마이크 녹음이 동작하지 않는다. 방법(`next dev --experimental-https`, Android 포트 포워딩, 개발 서버)은 PWA 작업 때 정한다

## 참고

- Spring Boot 문서: Docker Compose 지원, Testcontainers, Service Connections (`docs.spring.io/spring-boot/reference/features/dev-services.html`)
- PostgreSQL 공식 이미지 문서: 18부터 바뀐 데이터 경로 (`PGDATA`)
- Next.js 문서: `next dev --experimental-https` (`frontend/node_modules/next/dist/docs/01-app/03-api-reference/06-cli/next.md`)
