import { fileURLToPath } from "node:url";

import type { NextConfig } from "next";

const repositoryRoot = fileURLToPath(new URL("..", import.meta.url));

const securityHeaders = [
  { key: "Cross-Origin-Opener-Policy", value: "same-origin" },
  { key: "Cross-Origin-Resource-Policy", value: "same-origin" },
  { key: "Permissions-Policy", value: "camera=(), geolocation=(), microphone=()" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" }
] as const;

const nextConfig: NextConfig = {
  poweredByHeader: false,
  reactStrictMode: true,
  turbopack: {
    root: repositoryRoot
  },
  async headers() {
    return [{ source: "/(.*)", headers: [...securityHeaders] }];
  }
};

export default nextConfig;
