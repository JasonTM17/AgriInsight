import { expect, type Page } from "@playwright/test";

export function requiredE2eEnvironment(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`Real platform E2E requires ${name}`);
  return value;
}

export async function loginWithRealOidc(
  page: Page,
  username: string,
  returnTo: string
) {
  await page.goto(`/api/auth/login?returnTo=${encodeURIComponent(returnTo)}`);
  await page.locator("#username").fill(username);
  await page
    .locator("#password")
    .fill(requiredE2eEnvironment("AGRIINSIGHT_WEB_E2E_PERSONA_PASSWORD"));
  await page.locator("#kc-login").click();
  await expect(page).toHaveURL(new RegExp(`${returnTo.replace("?", "\\?")}(?:$|&)`));
}
