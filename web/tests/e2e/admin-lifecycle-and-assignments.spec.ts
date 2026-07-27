import { expect, test, type Locator, type Page } from "@playwright/test";

import {
  createAdminDatabasePool,
  findActiveActivityAssignment,
  findUnassignedFieldWorkerActivity,
  revokeActivityAssignmentForCleanup
} from "./helpers/admin-governance-database";
import { loginWithRealOidc } from "./helpers/real-oidc-login";

const FIELD_WORKER_PROFILE_ID = "20000000-0000-4000-8000-000000000016";
const ISSUER = "http://localhost:58080/realms/agriinsight-demo";

function section(page: Page, name: string): Locator {
  return page
    .getByRole("heading", { name })
    .locator("xpath=ancestor::section");
}

async function clickAndReload(page: Page, target: Locator) {
  await target.click();
  await page.waitForLoadState("load");
}

test("@admin tenant administrator completes lifecycle and assignment commands", async ({
  page
}) => {
  test.setTimeout(240_000);
  const pool = createAdminDatabasePool();
  const unique = Date.now().toString(36);
  const displayName = `E2E Governance ${unique}`;
  const subject = `e2e-governance-${unique}`;
  let activityAssignmentId: string | undefined;

  try {
    await loginWithRealOidc(page, "tenant-admin", "/admin");
    await page.getByText("Tạo hồ sơ người dùng", { exact: true }).click();
    const createForm = page
      .getByText("Tạo trực tiếp trong tenant")
      .locator("xpath=ancestor::form");
    await createForm.locator('input[name="displayName"]').fill(displayName);
    await createForm
      .locator('input[name="email"]')
      .fill(`governance-${unique}@demo.invalid`);
    await createForm.locator('input[name="issuer"]').fill(ISSUER);
    await createForm.locator('input[name="subject"]').fill(subject);
    await clickAndReload(
      page,
      createForm.getByRole("button", { name: "Tạo người dùng" })
    );

    await page.getByLabel("Tìm theo tên hoặc liên hệ").fill(displayName);
    await page.getByRole("button", { name: "Áp dụng" }).click();
    const userRow = page.getByRole("row").filter({ hasText: displayName });
    await expect(userRow).toBeVisible();
    await userRow.getByRole("link", { name: "Mở hồ sơ" }).click();
    await expect(page.getByRole("heading", { level: 1, name: displayName }))
      .toBeVisible();

    const lifecycle = section(page, "Vòng đời & vai trò");
    await lifecycle.getByRole("checkbox").check();
    await clickAndReload(
      page,
      lifecycle.getByRole("button", { name: "Vô hiệu người dùng" })
    );
    await expect(page.getByText("Đã vô hiệu", { exact: true })).toBeVisible();
    await clickAndReload(
      page,
      section(page, "Vòng đời & vai trò").getByRole("button", {
        name: "Kích hoạt lại"
      })
    );
    await expect(page.getByText("Đang hoạt động", { exact: true })).toBeVisible();

    let roleSection = section(page, "Vòng đời & vai trò");
    await roleSection.locator('select[name="roleCode"]').selectOption(
      "DATA_ANALYST"
    );
    await clickAndReload(
      page,
      roleSection.getByRole("button", { name: "Cấp vai trò" })
    );
    roleSection = section(page, "Vòng đời & vai trò");
    const analystRole = roleSection
      .locator("li")
      .filter({ hasText: "Chuyên viên dữ liệu" });
    await expect(analystRole).toBeVisible();
    await clickAndReload(
      page,
      analystRole.getByRole("button", { name: "Thu hồi" })
    );

    let identitySection = section(page, "Liên kết đăng nhập OIDC");
    const identityCount = await identitySection
      .getByRole("button", { name: "Ngắt liên kết" })
      .count();
    await identitySection.locator('input[name="issuer"]').fill(ISSUER);
    await identitySection
      .locator('input[name="subject"]')
      .fill(`${subject}-secondary`);
    await clickAndReload(
      page,
      identitySection.getByRole("button", { name: "Liên kết" })
    );
    identitySection = section(page, "Liên kết đăng nhập OIDC");
    await expect(
      identitySection.getByRole("button", { name: "Ngắt liên kết" })
    ).toHaveCount(identityCount + 1);
    await clickAndReload(
      page,
      identitySection
        .getByRole("button", { name: "Ngắt liên kết" })
        .last()
    );

    let scopeSection = section(page, "Nông trại & kho");
    for (const label of ["Nông trại cần cấp", "Kho cần cấp"]) {
      const select = scopeSection.getByLabel(label);
      const optionLabel = await select.locator("option:checked").textContent();
      await clickAndReload(
        page,
        select.locator("xpath=ancestor::form").getByRole("button", {
          name: "Cấp phạm vi"
        })
      );
      scopeSection = section(page, "Nông trại & kho");
      const assignment = scopeSection
        .locator("li")
        .filter({ hasText: optionLabel?.trim() ?? "" });
      await expect(assignment).toBeVisible();
      await clickAndReload(
        page,
        assignment.getByRole("button", { name: "Thu hồi" })
      );
      scopeSection = section(page, "Nông trại & kho");
    }

    const activity = await findUnassignedFieldWorkerActivity(pool);

    await page.goto(`/admin/users/${FIELD_WORKER_PROFILE_ID}`);
    const activitySection = section(page, "Phân công hoạt động");
    const grantForm = activitySection.locator("form").first();
    await grantForm.locator('input[name="activityKey"]').fill(activity.activityId);
    await grantForm.locator('input[name="employeeKey"]').fill(activity.employeeId);
    await grantForm
      .locator('input[name="version"]')
      .fill(activity.activityVersion);
    await clickAndReload(
      page,
      grantForm.getByRole("button", { name: "Cấp phân công" })
    );

    const assignment = await findActiveActivityAssignment(pool, activity);
    activityAssignmentId = assignment.id;
    const revokeSection = section(page, "Phân công hoạt động");
    await revokeSection.getByText("Thu hồi theo mã phân công").click();
    const revokeForm = revokeSection.locator("form").last();
    await revokeForm.locator('input[name="activityKey"]').fill(activity.activityId);
    await revokeForm.locator('input[name="assignmentKey"]').fill(
      activityAssignmentId
    );
    await revokeForm.locator('input[name="version"]').fill(
      assignment.version
    );
    await clickAndReload(
      page,
      revokeForm.getByRole("button", { name: "Thu hồi phân công" })
    );
  } finally {
    if (activityAssignmentId) {
      await revokeActivityAssignmentForCleanup(
        pool,
        activityAssignmentId
      ).catch(() => undefined);
    }
    await pool.end();
  }
});
