import { createHash } from "node:crypto";
import { isIP } from "node:net";

import { AuthError } from "@/server/auth/auth-error";
import type { WebEnvironment } from "@/server/config/environment";

const WINDOW_MS = 60_000;
const GLOBAL_LIMIT = 120;
const CLIENT_LIMIT = 20;

type WindowCounter = { count: number; resetAt: number };

export class LoginRateLimiter {
  private readonly counters = new Map<string, WindowCounter>();

  assertAllowed(
    request: Request,
    env: WebEnvironment,
    now = Date.now()
  ): void {
    this.increment("global", GLOBAL_LIMIT, now);
    this.increment(this.clientKey(request, env), CLIENT_LIMIT, now);
    if (this.counters.size > 2_048) this.removeExpired(now);
  }

  private clientKey(request: Request, env: WebEnvironment): string {
    if (!env.trustForwardedHeaders) return "client:direct";
    const forwardedFor = request.headers
      .get("x-forwarded-for")
      ?.split(",", 1)[0]
      ?.trim();
    const normalized = forwardedFor && isIP(forwardedFor)
      ? forwardedFor
      : "proxy-unknown";
    return `client:${createHash("sha256").update(normalized).digest("hex")}`;
  }

  private increment(key: string, limit: number, now: number): void {
    const current = this.counters.get(key);
    if (!current || current.resetAt <= now) {
      this.counters.set(key, { count: 1, resetAt: now + WINDOW_MS });
      return;
    }
    if (current.count >= limit) {
      throw new AuthError(
        "rate_limited",
        429,
        "Quá nhiều yêu cầu đăng nhập. Vui lòng thử lại sau."
      );
    }
    current.count += 1;
  }

  private removeExpired(now: number): void {
    for (const [key, counter] of this.counters) {
      if (counter.resetAt <= now) this.counters.delete(key);
    }
  }
}

export const loginRateLimiter = new LoginRateLimiter();
