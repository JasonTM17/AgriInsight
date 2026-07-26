import { expect } from "@playwright/test";
import type { Page } from "@playwright/test";

type CspViolation = Readonly<{
  blockedUri: string;
  columnNumber: number;
  directive: string;
  lineNumber: number;
  sample: string;
  sourceFile: string;
  target: string;
}>;

export async function installCspViolationRecorder(page: Page): Promise<void> {
  await page.addInitScript(() => {
    const state = window as Window & {
      __agriInsightCspViolations?: CspViolation[];
    };
    state.__agriInsightCspViolations = [];
    document.addEventListener("securitypolicyviolation", (event) => {
      state.__agriInsightCspViolations?.push({
        blockedUri: event.blockedURI,
        columnNumber: event.columnNumber,
        directive: event.effectiveDirective,
        lineNumber: event.lineNumber,
        sample: event.sample,
        sourceFile: event.sourceFile,
        target:
          event.target instanceof Element
            ? event.target.outerHTML.slice(0, 500)
            : event.target?.constructor.name ?? "unknown"
      });
    });
  });
}

export async function expectNoCspViolations(page: Page): Promise<void> {
  await page.waitForTimeout(300);
  const violations = await page.evaluate(() => {
    const state = window as Window & {
      __agriInsightCspViolations?: CspViolation[];
    };
    const recorded = state.__agriInsightCspViolations ?? [];
    state.__agriInsightCspViolations = [];
    return recorded;
  });
  expect(violations).toEqual([]);
}

export function expectNonceCspHeader(
  response: Readonly<{ headers(): Record<string, string> }>
): void {
  const policy = response.headers()["content-security-policy"];
  expect(policy).toContain("script-src 'self' 'nonce-");
  expect(policy).toContain("style-src 'self' 'nonce-");
  expect(policy).not.toContain("'unsafe-inline'");
}
