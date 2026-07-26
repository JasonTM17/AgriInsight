import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

const repoRoot = resolve(import.meta.dirname, "../../..");

describe("nonce CSP rendering", () => {
  it("forces the root layout onto the per-request rendering path", () => {
    const layout = readFileSync(
      resolve(repoRoot, "web/src/app/layout.tsx"),
      "utf8"
    );

    expect(layout).toContain('import { connection } from "next/server"');
    expect(layout).toMatch(/export default async function RootLayout/);
    expect(layout).toContain("await connection()");
  });
});
