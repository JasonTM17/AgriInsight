const rail = document.querySelector("#primary-rail");
const navToggle = document.querySelector("[data-nav-toggle]");
const workspace = document.querySelector(".workspace");
const railCloseButton = document.querySelector(".rail-close");
const scopeDialog = document.querySelector("#scope-dialog");
const discardDialog = document.querySelector("#discard-dialog");
const mobileNavigation = window.matchMedia("(max-width: 900px)");
const mobileWorkView = window.matchMedia("(max-width: 640px)");
let activeTaskName = "sensor";
let pendingTaskName = null;

const taskFixtures = {
  sensor: { code: "FIELD-0001 · SENSOR-CHECK", title: "Kiểm tra cảm biến S-01", location: "Khu vực 1.1 · Nông trại Cao Nguyên", due: "10:00 hôm nay", crop: "Cà phê", reading: "13/07 · 18:00", source: "field_health_status", reason: "Dữ liệu cảm biến đã trễ 5 ngày; độ ẩm đất gần nhất 41,11%.", action: "Kiểm tra cảm biến và kết nối IoT trong 4 giờ.", measureLabel: "Độ ẩm đo đối chứng", unit: "%", min: "0", max: "100" },
  pest: { code: "FIELD-0012 · PEST-SURVEY", title: "Khảo sát dấu hiệu dịch hại", location: "Khu vực 3.4 · Nông trại Miền Đông", due: "12:00 hôm nay", crop: "Cà phê", reading: "18/07 · 18:00", source: "field_health_status", reason: "Một ca dịch hại trong 90 ngày, diện tích ảnh hưởng lớn nhất 19,07%.", action: "Kiểm tra thực địa trong 24 giờ và lập phương án kiểm soát dịch hại.", measureLabel: "Diện tích ảnh hưởng ước tính", unit: "%", min: "0", max: "100" },
  soil: { code: "FIELD-0002 · SOIL-CHECK", title: "Đối chiếu độ ẩm tại ruộng", location: "Khu vực 1.2 · Nông trại Cao Nguyên", due: "13:30 hôm nay", crop: "Sầu riêng", reading: "18/07 · 18:00", source: "field_health_status", reason: "Độ ẩm gần nhất 50,22%; bằng chứng cần được đối chiếu theo lịch vận hành.", action: "Đo tại ruộng và ghi nhận sai lệch, không thay thế dữ liệu cảm biến.", measureLabel: "Độ ẩm đo đối chứng", unit: "%", min: "0", max: "100" }
};

document.querySelectorAll("svg:not([role='img'])").forEach((icon) => { icon.setAttribute("aria-hidden", "true"); icon.setAttribute("focusable", "false"); });

function setRailOpen(isOpen, restoreFocus = false) {
  const openOnMobile = mobileNavigation.matches && isOpen;
  rail?.classList.toggle("is-open", openOnMobile);
  document.body.classList.toggle("nav-open", openOnMobile);
  navToggle?.setAttribute("aria-expanded", String(openOnMobile));
  navToggle?.setAttribute("aria-label", openOnMobile ? "Đóng điều hướng" : "Mở điều hướng");
  if (rail && mobileNavigation.matches && !openOnMobile) { rail.setAttribute("inert", ""); rail.setAttribute("aria-hidden", "true"); } else { rail?.removeAttribute("inert"); rail?.removeAttribute("aria-hidden"); }
  if (workspace && openOnMobile) { workspace.setAttribute("inert", ""); workspace.setAttribute("aria-hidden", "true"); railCloseButton?.focus(); } else { workspace?.removeAttribute("inert"); workspace?.removeAttribute("aria-hidden"); if (restoreFocus) navToggle?.focus(); }
}

function setStep(stepName, moveFocus = true) {
  const selectedTab = document.querySelector(`[data-step="${stepName}"]`);
  document.querySelectorAll("[data-step]").forEach((tab) => { const selected = tab === selectedTab; tab.setAttribute("aria-selected", String(selected)); tab.tabIndex = selected ? 0 : -1; });
  document.querySelectorAll("[role='tabpanel']").forEach((panel) => { panel.hidden = panel.id !== `panel-${stepName}`; });
  if (moveFocus) selectedTab?.focus();
}

function hasUnsavedEntry() {
  const form = document.querySelector("#panel-evidence");
  if (!(form instanceof HTMLFormElement)) return false;
  const evidence = form.elements.namedItem("evidence");
  return Boolean(form.elements.namedItem("result")?.value || form.elements.namedItem("measure")?.value || form.elements.namedItem("note")?.value || (evidence instanceof HTMLInputElement && evidence.files?.length));
}

function setTask(taskName, discardDraft = false) {
  const task = taskFixtures[taskName];
  if (!task) return;
  if (taskName !== activeTaskName && hasUnsavedEntry() && !discardDraft) {
    pendingTaskName = taskName;
    if (discardDialog) discardDialog.returnValue = "";
    discardDialog?.showModal();
    return;
  }
  activeTaskName = taskName;
  document.querySelectorAll("[data-task]").forEach((row) => { const selected = row.dataset.task === taskName; row.classList.toggle("is-selected", selected); row.setAttribute("aria-pressed", String(selected)); });
  const bindings = { "[data-task-code]": task.code, "[data-task-title]": task.title, "[data-task-location]": task.location, "[data-task-due]": task.due, "[data-task-crop]": task.crop, "[data-task-reading]": task.reading, "[data-task-source]": task.source, "[data-task-reason]": task.reason, "[data-task-action]": task.action, "[data-measure-label]": task.measureLabel, "[data-measure-unit]": task.unit, "[data-review-task]": task.title };
  Object.entries(bindings).forEach(([selector, value]) => { const target = document.querySelector(selector); if (target) target.textContent = value; });
  const measure = document.querySelector("input[name='measure']");
  if (measure) { measure.min = task.min; measure.max = task.max; }
  const status = document.querySelector("[data-prototype-status]");
  if (status) status.textContent = `Đã mở ${task.title}.`;
  setStep("evidence", false);
  if (mobileWorkView.matches) {
    document.body.classList.add("mobile-detail-open");
    document.querySelector("[data-queue-back]")?.focus();
  }
}

function prepareReview() {
  const form = document.querySelector("#panel-evidence");
  if (!(form instanceof HTMLFormElement) || !form.reportValidity()) return;
  const data = new FormData(form);
  const resultLabel = form.querySelector(`option[value="${data.get("result")}"]`)?.textContent ?? "Chưa chọn";
  const unit = document.querySelector("[data-measure-unit]")?.textContent ?? "";
  const measure = Number(String(data.get("measure")).replace(",", "."));
  const formattedMeasure = Number.isFinite(measure) ? new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 }).format(measure) : "Chưa nhập";
  document.querySelector("[data-review-result]").textContent = resultLabel;
  document.querySelector("[data-review-measure]").textContent = `${formattedMeasure}${unit === "%" ? "" : " "}${unit}`;
  document.querySelector("[data-review-time]").textContent = String(data.get("observedAt") || "Chưa nhập").replace("T", " · ");
  setStep("review");
}

function handleStepKeydown(event) {
  if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
  const tabs = Array.from(document.querySelectorAll("[data-step]"));
  const currentIndex = tabs.indexOf(event.currentTarget);
  const nextIndex = event.key === "Home" ? 0 : event.key === "End" ? tabs.length - 1 : event.key === "ArrowRight" ? (currentIndex + 1) % tabs.length : (currentIndex - 1 + tabs.length) % tabs.length;
  event.preventDefault();
  setStep(tabs[nextIndex].dataset.step);
}

function applyFixtureState() {
  const requested = new URLSearchParams(window.location.search).get("state") ?? "normal";
  const state = ["normal", "offline", "conflict", "revoked"].includes(requested) ? requested : "normal";
  document.body.dataset.fixtureState = state;
  document.querySelectorAll("[data-state-banner]").forEach((banner) => { banner.hidden = banner.dataset.stateBanner !== state; });
  const conflict = document.querySelector("[data-conflict-comparison]");
  if (conflict) conflict.hidden = state !== "conflict";
  const submit = document.querySelector("[data-submit-entry]");
  const fieldset = document.querySelector("[data-entry-fields]");
  if (submit) { submit.disabled = state === "revoked"; submit.textContent = state === "offline" ? "Lưu vào hàng chờ" : state === "conflict" ? "Đối chiếu rồi gửi" : "Gửi bản ghi"; }
  if (fieldset instanceof HTMLFieldSetElement) fieldset.disabled = state === "revoked";
  const syncText = document.querySelector("[data-sync-state] span");
  if (syncText && state === "offline") syncText.textContent = "Ngoại tuyến · chưa đồng bộ";
}

navToggle?.addEventListener("click", () => setRailOpen(navToggle.getAttribute("aria-expanded") !== "true"));
document.querySelectorAll("[data-nav-close]").forEach((element) => element.addEventListener("click", () => setRailOpen(false, true)));
document.querySelectorAll(".rail-nav a").forEach((link) => link.addEventListener("click", () => setRailOpen(false, true)));
mobileNavigation.addEventListener("change", () => setRailOpen(false));
mobileWorkView.addEventListener("change", (event) => { if (!event.matches) document.body.classList.remove("mobile-detail-open"); });
document.addEventListener("keydown", (event) => { if (event.key === "Escape" && navToggle?.getAttribute("aria-expanded") === "true") setRailOpen(false, true); });
document.querySelectorAll("[data-open-scope]").forEach((button) => button.addEventListener("click", () => scopeDialog?.showModal()));
scopeDialog?.addEventListener("click", (event) => { if (event.target === scopeDialog) scopeDialog.close("cancel"); });
discardDialog?.addEventListener("click", (event) => { if (event.target === discardDialog) discardDialog.close("cancel"); });
discardDialog?.addEventListener("close", () => {
  if (discardDialog.returnValue === "discard" && pendingTaskName) {
    document.querySelector("#panel-evidence")?.reset();
    const nextTask = pendingTaskName;
    pendingTaskName = null;
    setTask(nextTask, true);
    return;
  }
  pendingTaskName = null;
});
document.querySelectorAll("[data-task]").forEach((row) => row.addEventListener("click", () => setTask(row.dataset.task)));
document.querySelector("[data-queue-back]")?.addEventListener("click", () => { document.body.classList.remove("mobile-detail-open"); document.querySelector("[data-task][aria-pressed='true']")?.focus(); });
document.querySelectorAll("[data-step]").forEach((tab) => { tab.addEventListener("click", () => setStep(tab.dataset.step)); tab.addEventListener("keydown", handleStepKeydown); });
document.querySelectorAll("[data-step-target]").forEach((button) => button.addEventListener("click", () => setStep(button.dataset.stepTarget)));
document.querySelector("[data-review-entry]")?.addEventListener("click", prepareReview);
document.querySelector("[data-submit-entry]")?.addEventListener("click", () => { const status = document.querySelector("[data-prototype-status]"); if (status) status.textContent = document.body.dataset.fixtureState === "offline" ? "Prototype: bản ghi được minh họa ở trạng thái chờ, chưa gửi máy chủ." : "Prototype thiết kế không gửi dữ liệu. Luồng xác nhận sẽ dùng API Phase 4."; });

setRailOpen(false);
setStep("evidence", false);
applyFixtureState();
