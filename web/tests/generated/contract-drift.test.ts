import { spawnSync } from "node:child_process";

import { describe, expect, it } from "vitest";

describe("generated OpenAPI clients", () => {
  it("match both checked-in backend and analytics contracts", () => {
    const result = spawnSync(
      process.execPath,
      ["scripts/generate-openapi-types.mjs", "--check"],
      {
        cwd: process.cwd(),
        encoding: "utf8"
      }
    );
    expect(result.stderr).toBe("");
    expect(result.status).toBe(0);
  });
});
