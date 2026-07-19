const costView = window.AGRI_COST_VIEW;
const state = { lens: "operating", selectedId: "bon-phan" };
const rail = document.querySelector("#primary-rail");
const navToggle = document.querySelector("[data-nav-toggle]");
const railClose = document.querySelector(".rail-close");
const workspace = document.querySelector(".workspace");
const exportDialog = document.querySelector("#cost-export-dialog");
const exportTrigger = document.querySelector("[data-open-export]");
const mobileNavigation = window.matchMedia("(max-width: 960px)");
let railFocusFrame = 0;

function announce(message) { const region = document.querySelector("[data-global-status]"); if (!region) return; region.textContent = ""; window.requestAnimationFrame(() => { region.textContent = message; }); }
function driverExists(lens, id) { return Boolean(id && costView.packageFor(lens).drivers?.some((item) => item.id === id)); }
function stateFromUrl() {
  const params = new URLSearchParams(window.location.search); const requestedLens = params.get("lens");
  let lens = costView.lensValues.has(requestedLens) ? requestedLens : "operating";
  if (!costView.lensValues.has(requestedLens) && driverExists("procurement", params.get("supplier"))) lens = "procurement";
  const parameter = lens === "operating" ? "activity" : "supplier"; const selectedId = costView.normalizeSelection(lens, params.get(parameter));
  return { lens, selectedId };
}
function writeUrl(method = "pushState") {
  const url = new URL(window.location.href); url.search = ""; url.searchParams.set("lens", state.lens);
  if (state.selectedId) url.searchParams.set(state.lens === "operating" ? "activity" : "supplier", state.selectedId); window.history[method]({}, "", url);
}
function renderAll() { state.selectedId = costView.render(state, selectDriver); }
function selectDriver(id, userInitiated = true) {
  if (!driverExists(state.lens, id) || id === state.selectedId) return; state.selectedId = id; renderAll(); [...document.querySelectorAll("[data-selection-id]")].find((button) => button.dataset.selectionId === id)?.focus();
  if (userInitiated) { writeUrl(); const item = costView.packageFor(state.lens).drivers.find((driver) => driver.id === id); announce(`Đã mở bằng chứng ${item.name}.`); }
}
function selectLens(lens, userInitiated = true) {
  if (!costView.lensValues.has(lens)) return; const changed = lens !== state.lens; state.lens = lens;
  if (changed) state.selectedId = costView.packageFor(lens).defaultSelection; renderAll();
  if (userInitiated && changed) { writeUrl(); announce(`Đã chuyển sang lens ${costView.packageFor(lens).title}. Hai lens không được cộng gộp.`); }
}
function restoreFromUrl() { const restored = stateFromUrl(); state.lens = restored.lens; state.selectedId = restored.selectedId; renderAll(); writeUrl("replaceState"); }
function setRailOpen(open) {
  const mobile = mobileNavigation.matches; const active = Boolean(open && mobile); const wasOpen = document.body.classList.contains("nav-open"); const focusWasInRail = Boolean(rail?.contains(document.activeElement));
  if (railFocusFrame) { window.cancelAnimationFrame(railFocusFrame); railFocusFrame = 0; }
  document.body.classList.toggle("nav-open", active); navToggle?.setAttribute("aria-expanded", String(active)); navToggle?.setAttribute("aria-label", active ? "Đóng điều hướng" : "Mở điều hướng");
  if (rail && mobile && !active) { rail.setAttribute("inert", ""); rail.setAttribute("aria-hidden", "true"); } else { rail?.removeAttribute("inert"); rail?.removeAttribute("aria-hidden"); }
  if (workspace && active) { workspace.setAttribute("inert", ""); workspace.setAttribute("aria-hidden", "true"); } else { workspace?.removeAttribute("inert"); workspace?.removeAttribute("aria-hidden"); }
  if (active) railFocusFrame = window.requestAnimationFrame(() => { railFocusFrame = 0; if (document.body.classList.contains("nav-open") && mobileNavigation.matches) railClose?.focus(); });
  else if (wasOpen || (mobile && focusWasInRail)) (mobile ? navToggle : document.querySelector("#main-content"))?.focus();
}
function handleLensKeydown(event) {
  const tabs = [...document.querySelectorAll("[data-lens]")]; const index = tabs.indexOf(event.currentTarget); let nextIndex;
  if (["ArrowRight", "ArrowDown"].includes(event.key)) nextIndex = (index + 1) % tabs.length;
  if (["ArrowLeft", "ArrowUp"].includes(event.key)) nextIndex = (index - 1 + tabs.length) % tabs.length;
  if (event.key === "Home") nextIndex = 0; if (event.key === "End") nextIndex = tabs.length - 1; if (nextIndex === undefined) return;
  event.preventDefault(); const next = tabs[nextIndex]; selectLens(next.dataset.lens); next.focus();
}

document.querySelectorAll("[data-lens]").forEach((tab) => { tab.addEventListener("click", () => selectLens(tab.dataset.lens)); tab.addEventListener("keydown", handleLensKeydown); });
navToggle?.addEventListener("click", () => setRailOpen(!document.body.classList.contains("nav-open")));
document.querySelectorAll("[data-nav-close]").forEach((button) => button.addEventListener("click", () => setRailOpen(false)));
exportTrigger?.addEventListener("click", () => { costView.renderExport(costView.packageFor(state.lens)); exportDialog?.showModal(); });
document.querySelectorAll("[data-close-dialog]").forEach((button) => button.addEventListener("click", () => button.closest("dialog")?.close()));
exportDialog?.addEventListener("click", (event) => { if (event.target === exportDialog) exportDialog.close(); });
exportDialog?.addEventListener("close", () => exportTrigger?.focus());
document.addEventListener("keydown", (event) => { if (event.key === "Escape" && document.body.classList.contains("nav-open")) setRailOpen(false); });
mobileNavigation.addEventListener("change", () => setRailOpen(false));
window.addEventListener("popstate", () => { const restored = stateFromUrl(); state.lens = restored.lens; state.selectedId = restored.selectedId; renderAll(); writeUrl("replaceState"); announce(`Đã khôi phục ${costView.packageFor(state.lens).title} từ lịch sử điều hướng.`); });

setRailOpen(false); restoreFromUrl();
