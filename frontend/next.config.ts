import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  // Docker 이미지에 필요한 파일만 .next/standalone으로 모은다 (frontend/Dockerfile)
  output: "standalone",
  cacheComponents: true,
  partialPrefetching: true,
};

export default nextConfig;
