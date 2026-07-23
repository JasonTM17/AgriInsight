import assert from "node:assert/strict";
import test from "node:test";

import {
  AUTH_ADAPTER_INVARIANTS,
  BETTER_AUTH_FIT,
  OPENID_CLIENT_FIT,
  SELECTED_ADAPTER,
  failedInvariants,
} from "../../src/adapter-fit.ts";

test("contract analysis rejects Better Auth only on the non-replaceable refresh fence", () => {
  assert.deepEqual(failedInvariants(BETTER_AUTH_FIT), [
    "atomic-refresh-lease-version-fencing",
  ]);
  assert.match(BETTER_AUTH_FIT.boundary, /no transactional hook/i);
});

test("openid-client wins every invariant while leaving session ownership local", () => {
  assert.equal(SELECTED_ADAPTER, OPENID_CLIENT_FIT);
  assert.equal(SELECTED_ADAPTER.adapter, "openid-client");
  assert.equal(AUTH_ADAPTER_INVARIANTS.every((item) => SELECTED_ADAPTER.satisfied[item]), true);
});
