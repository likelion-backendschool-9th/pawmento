# 프론트엔드 이미지. frontend/ 폴더를 빌드 컨텍스트로 쓴다: docker build -t pawmento-frontend frontend
# next.config.ts의 output: 'standalone' 결과물만 담아 node_modules 없이 실행한다.

FROM node:24-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:24-alpine AS build
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# NEXT_PUBLIC_ 값은 빌드할 때 번들에 들어간다. 네이버 지도 Client ID는 브라우저에 공개되는 값이라 빌드 인자로 받는다
# (Client Secret은 넣지 않는다). 비어 있으면 지도 화면이 예시 지도로 동작한다
ARG NEXT_PUBLIC_NAVER_MAP_CLIENT_ID=""
ENV NEXT_PUBLIC_NAVER_MAP_CLIENT_ID=$NEXT_PUBLIC_NAVER_MAP_CLIENT_ID
RUN npm run build

FROM node:24-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0
# standalone 서버는 public과 .next/static을 따로 복사해야 제공한다
COPY --from=build --chown=node:node /app/public ./public
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static
USER node
EXPOSE 3000
CMD ["node", "server.js"]
