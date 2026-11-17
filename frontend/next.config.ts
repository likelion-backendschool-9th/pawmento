import type { NextConfig } from "next";

const backendOrigin =
  process.env.BACKEND_ORIGIN ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  /* config options here */
  // Docker 이미지에 필요한 파일만 .next/standalone으로 모은다 (frontend/Dockerfile)
  output: "standalone",
  cacheComponents: true,
  partialPrefetching: true,
  // 로컬에서 프론트(3000)→백엔드(8080). 쿠키 동일 출처를 위해 /api/만 넘긴다
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${backendOrigin}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
