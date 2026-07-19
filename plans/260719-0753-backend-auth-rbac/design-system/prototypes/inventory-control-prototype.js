const inventoryView = window.AGRI_INVENTORY_VIEW;
const rail = document.querySelector("#primary-rail");
const navToggle = document.querySelector("[data-nav-toggle]");
const workspace = document.querySelector(".workspace");
const mainContent = document.querySelector("#main-content");
const railCloseButton = document.querySelector(".rail-close");
const filterDialog = document.querySelector("#inventory-filter-dialog");
const evidenceDialog = document.querySelector("#inventory-evidence-dialog");
const mobileNavigation = window.matchMedia("(max-width: 900px)");
const mobileEvidenceLayout = window.matchMedia("(max-width: 640px)");
const state = { severity: "all", type: "all", alertId: "MAT-NPK-low_stock" };
let evidenceTrigger = null;
let railFocusFrame = 0;

function announce(message) { document.querySelectorAll("[data-global-status]").forEach((element) => { element.textContent = message; }); }
function visible(element) { return Boolean(element && element.getClientRects().length); }
function focusable(element) { return Boolean(visible(element) && !element.disabled && !element.closest("[inert]")); }

function setRailOpen(isOpen, restoreFocus = false) {
  const mobile = mobileNavigation.matches;
  const openOnMobile = mobile && isOpen;
  const wasOpen = document.body.classList.contains("nav-open");
  const focusWasInRail = Boolean(rail?.contains(document.activeElement));
  const focusWasOnToggle = document.activeElement === navToggle;
  if (railFocusFrame) { cancelAnimationFrame(railFocusFrame); railFocusFrame = 0; }
  rail?.classList.toggle("is-open", openOnMobile);
  document.body.classList.toggle("nav-open", openOnMobile);
  navToggle?.setAttribute("aria-expanded", String(openOnMobile));
  navToggle?.setAttribute("aria-label", openOnMobile ? "Đóng điều hướng" : "Mở điều hướng");
  if (rail && mobile && !openOnMobile) { rail.setAttribute("inert", ""); rail.setAttribute("aria-hidden", "true"); } else { rail?.removeAttribute("inert"); rail?.removeAttribute("aria-hidden"); }
  if (workspace && openOnMobile) { workspace.setAttribute("inert", ""); workspace.setAttribute("aria-hidden", "true"); } else { workspace?.removeAttribute("inert"); workspace?.removeAttribute("aria-hidden"); }
  if (openOnMobile) railFocusFrame = requestAnimationFrame(() => { railFocusFrame = 0; if (mobileNavigation.matches && document.body.classList.contains("nav-open")) railCloseButton?.focus(); });
  else if (restoreFocus || wasOpen || (mobile && focusWasInRail) || (!mobile && focusWasOnToggle)) {
    railFocusFrame = requestAnimationFrame(() => { railFocusFrame = 0; (mobile ? navToggle : mainContent)?.focus(); });
  }
}

function normalizeSelection() {
  const alerts = inventoryView.alertsFor(state);
  if (!alerts.some((alert) => alert.id === state.alertId)) state.alertId = alerts[0]?.id ?? "";
  return alerts;
}

function renderAll() {
  normalizeSelection();
  const alerts = inventoryView.render(state, selectAlert);
  if (!state.alertId && evidenceDialog?.open) evidenceDialog.close();
  return alerts;
}

function updateUrl(mode = "push") {
  const url = new URL(window.location.href);
  url.search = "";
  url.searchParams.set("severity", state.severity);
  url.searchParams.set("type", state.type);
  const selected = inventoryView.fixture.alerts.find((alert) => alert.id === state.alertId);
  if (selected) { url.searchParams.set("material", selected.materialCode); url.searchParams.set("alert", selected.id); }
  if (mode !== "replace" && url.href === window.location.href) return;
  history[mode === "replace" ? "replaceState" : "pushState"]({}, "", url);
}

function restoreFromUrl() {
  const params = new URLSearchParams(window.location.search);
  const severity = params.get("severity"); const type = params.get("type");
  state.severity = severity && inventoryView.severityValues.has(severity) ? severity : "all";
  state.type = type && inventoryView.typeValues.has(type) ? type : "all";
  const filtered = inventoryView.alertsFor(state); const alertId = params.get("alert"); const materialCode = params.get("material");
  const candidate = inventoryView.alertForUrl(state, alertId, materialCode);
  state.alertId = candidate?.id ?? filtered[0]?.id ?? "";
  renderAll(); updateUrl("replace");
}

function alertRowFor(alertId) { return Array.from(document.querySelectorAll("[data-alert-id]")).find((row) => row.dataset.alertId === alertId); }
function focusAlert(alertId) { requestAnimationFrame(() => alertRowFor(alertId)?.focus()); }

function focusSelectionOrActiveTab(alerts) {
  requestAnimationFrame(() => {
    const selectedRow = alerts.length && state.alertId ? alertRowFor(state.alertId) : null;
    const activeTab = document.querySelector("[data-severity][aria-selected='true']");
    (focusable(selectedRow) ? selectedRow : activeTab ?? mainContent)?.focus();
  });
}

function selectAlert(alertId, focusRow = false) {
  if (!inventoryView.alertsFor(state).some((alert) => alert.id === alertId)) return;
  if (state.alertId === alertId) { if (focusRow) focusAlert(alertId); return; }
  state.alertId = alertId; renderAll(); updateUrl();
  const selected = inventoryView.fixture.alerts.find((alert) => alert.id === alertId);
  announce(`Đã chọn ${selected.materialName}, ${inventoryView.alertTypeLabels[selected.alertType]}.`);
  if (focusRow) focusAlert(alertId);
}

function selectSeverity(severity) {
  if (!inventoryView.severityValues.has(severity) || severity === state.severity) return;
  state.severity = severity; renderAll(); updateUrl();
  announce(`Đã lọc mức ${severity === "all" ? "tất cả" : inventoryView.severityLabels[severity]}.`);
}

function handleSeverityKeydown(event) {
  if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
  const tabs = Array.from(document.querySelectorAll("[data-severity]")); const index = tabs.indexOf(event.currentTarget);
  const next = event.key === "Home" ? 0 : event.key === "End" ? tabs.length - 1 : event.key === "ArrowRight" ? (index + 1) % tabs.length : (index - 1 + tabs.length) % tabs.length;
  event.preventDefault(); selectSeverity(tabs[next].dataset.severity); tabs[next].focus();
}

function openEvidence(event) {
  const selected = inventoryView.fixture.alerts.find((alert) => alert.id === state.alertId);
  if (!selected || !evidenceDialog) return;
  evidenceTrigger = event.currentTarget; evidenceDialog.showModal();
}

function restoreEvidenceFocus() {
  const inlineTrigger = document.querySelector(".inventory-evidence [data-open-evidence]");
  const mobileTrigger = document.querySelector(".mobile-evidence-button");
  const layoutTrigger = mobileEvidenceLayout.matches ? mobileTrigger : inlineTrigger;
  const activeTab = document.querySelector("[data-severity][aria-selected='true']");
  const target = focusable(evidenceTrigger) ? evidenceTrigger : focusable(layoutTrigger) ? layoutTrigger : activeTab ?? mainContent;
  requestAnimationFrame(() => target?.focus()); evidenceTrigger = null;
}

function preparePrintDetails() {
  document.querySelectorAll(".queue-table, .abc-table").forEach((details) => {
    if (!details.hasAttribute("data-print-was-open")) details.dataset.printWasOpen = String(details.open);
    details.open = true;
  });
}

function restorePrintDetails() {
  document.querySelectorAll("[data-print-was-open]").forEach((details) => {
    details.open = details.dataset.printWasOpen === "true";
    delete details.dataset.printWasOpen;
  });
}

document.querySelectorAll("svg:not([role='img'])").forEach((icon) => { icon.setAttribute("aria-hidden", "true"); icon.setAttribute("focusable", "false"); });
navToggle?.addEventListener("click", () => setRailOpen(navToggle.getAttribute("aria-expanded") !== "true"));
document.querySelectorAll("[data-nav-close]").forEach((element) => element.addEventListener("click", () => setRailOpen(false, true)));
document.querySelectorAll(".rail-nav a").forEach((link) => link.addEventListener("click", () => setRailOpen(false, true)));
mobileNavigation.addEventListener("change", () => setRailOpen(false));
document.addEventListener("keydown", (event) => { if (event.key === "Escape" && !document.querySelector("dialog[open]") && navToggle?.getAttribute("aria-expanded") === "true") setRailOpen(false, true); });
document.querySelectorAll("[data-severity]").forEach((tab) => { tab.addEventListener("click", () => selectSeverity(tab.dataset.severity)); tab.addEventListener("keydown", handleSeverityKeydown); });
document.querySelectorAll("[data-open-filter]").forEach((button) => button.addEventListener("click", () => { if (!filterDialog) return; inventoryView.syncFilterControls(state); filterDialog.returnValue = ""; filterDialog.showModal(); }));
document.querySelectorAll("[data-open-evidence]").forEach((button) => button.addEventListener("click", openEvidence));
document.querySelector("[data-clear-filter]")?.addEventListener("click", () => { state.severity = "all"; state.type = "all"; const alerts = renderAll(); updateUrl(); announce("Đã xóa bộ lọc cảnh báo."); focusSelectionOrActiveTab(alerts); });
document.querySelectorAll("[data-close-dialog]").forEach((button) => button.addEventListener("click", () => button.closest("dialog")?.close()));
document.querySelectorAll("dialog").forEach((dialog) => dialog.addEventListener("click", (event) => { if (event.target === dialog) dialog.close(); }));
evidenceDialog?.addEventListener("close", restoreEvidenceFocus);
filterDialog?.addEventListener("close", () => {
  if (filterDialog.returnValue !== "apply") return;
  const form = new FormData(filterDialog.querySelector("form")); const severity = String(form.get("severity")); const type = String(form.get("type"));
  if (!inventoryView.severityValues.has(severity) || !inventoryView.typeValues.has(type)) return;
  state.severity = severity; state.type = type; const alerts = renderAll(); updateUrl(); announce(`Đã áp dụng bộ lọc, còn ${alerts.length} cảnh báo.`);
});
window.addEventListener("popstate", restoreFromUrl);
window.addEventListener("beforeprint", preparePrintDetails);
window.addEventListener("afterprint", restorePrintDetails);

setRailOpen(false); restoreFromUrl();
