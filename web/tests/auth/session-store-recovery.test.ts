import { describe, expect, it } from "vitest";
import type { Pool } from "pg";

import { PostgresSessionStore } from "@/server/auth/postgres-session-store";

describe("PostgreSQL session store recovery", () => {
  it("retries schema validation after a transient connection failure", async () => {
    let schemaAttempts = 0;
    const pool = {
      async query(sql: string) {
        if (sql.includes("schema_migrations")) {
          schemaAttempts += 1;
          if (schemaAttempts === 1) throw new Error("temporary database outage");
          return { rows: [{ version: 1 }] };
        }
        return { rows: [] };
      }
    } as unknown as Pool;
    const store = new PostgresSessionStore(pool);

    await expect(store.findSession(Buffer.alloc(32))).rejects.toThrow(
      "temporary database outage"
    );
    await expect(store.findSession(Buffer.alloc(32))).resolves.toBeNull();
    expect(schemaAttempts).toBe(2);
  });
});
