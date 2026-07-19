const farms = Array.isArray(window.AGRI_FARM_FIXTURES) ? window.AGRI_FARM_FIXTURES : [];
const rail = document.querySelector("#primary-rail");
const navToggle = document.querySelector("[data-nav-toggle]");
const workspace = document.querySelector(".workspace");
const railCloseButton = document.querySelector(".rail-close");
const mapDialog = document.querySelector("#farm-map-dialog");
const fieldDialog = document.querySelector("#field-dialog");
const filterDialog = document.querySelector("#farm-filter-dialog");
const mobileNavigation = window.matchMedia("(max-width: 900px)");
const mobileMapLayout = window.matchMedia("(max-width: 640px)");
let currentMetric = "profit";
let currentFarmCode = "FARM-004";
let currentFieldCode = "FIELD-0014";
let fieldEvidenceOrigin = "inline";

const metricConfig = {
  profit: { label: "Lợi nhuận", hint: "Cao xuống thấp · lợi nhuận vận hành", direction: "desc", format: (value) => `${formatNumber(value / 1e9, 2)} tỷ ₫` },
  yield: { label: "Năng suất", hint: "Cao xuống thấp · sản lượng trên diện tích thu hoạch", direction: "desc", format: (value) => `${formatNumber(value, 0)} kg/ha` },
  margin: { label: "Biên lợi nhuận", hint: "Cao xuống thấp · tỷ lệ lợi nhuận trên doanh thu", direction: "desc", format: (value) => `${formatNumber(value, 2)}%` },
  cost: { label: "Chi phí/ha", hint: "Thấp lên cao · thanh dài hơn biểu thị chi phí/ha thấp hơn", direction: "asc", format: (value) => `${formatNumber(value / 1e6, 2)} triệu ₫/ha` }
};

function formatNumber(value, digits = 2) { return new Intl.NumberFormat("vi-VN", { minimumFractionDigits: digits, maximumFractionDigits: digits }).format(value); }
function statusLabel(status) { return status === "high" ? "Rủi ro cao" : status === "watch" ? "Theo dõi" : status === "healthy" ? "Khỏe" : "Chưa xác định"; }
function formatReading(value) { const [date, time] = value.split("T"); const [year, month, day] = date.split("-"); return `${day}/${month} · ${time.slice(0, 5)}`; }
function selectedFarm() { return farms.find((farm) => farm.code === currentFarmCode) ?? farms[0]; }
function selectedField() { const farm = selectedFarm(); return farm.fields.find((field) => field.code === currentFieldCode) ?? farm.fields.find((field) => field.status !== "healthy") ?? farm.fields[0]; }
function setText(selector, value) { document.querySelectorAll(selector).forEach((element) => { element.textContent = value; }); }
function makeElement(tag, className, text) { const element = document.createElement(tag); if (className) element.className = className; if (text !== undefined) element.textContent = text; return element; }
function announce(message) { setText("[data-selection-status], [data-global-status]", message); }
function focusSelectedPlot() { const scope = mapDialog?.open ? mapDialog : document.querySelector(".farm-atlas"); requestAnimationFrame(() => scope?.querySelector(`[data-field="${currentFieldCode}"]`)?.focus()); }

function setRailOpen(isOpen, restoreFocus = false) {
  const openOnMobile = mobileNavigation.matches && isOpen;
  rail?.classList.toggle("is-open", openOnMobile);
  document.body.classList.toggle("nav-open", openOnMobile);
  navToggle?.setAttribute("aria-expanded", String(openOnMobile));
  navToggle?.setAttribute("aria-label", openOnMobile ? "Đóng điều hướng" : "Mở điều hướng");
  if (rail && mobileNavigation.matches && !openOnMobile) { rail.setAttribute("inert", ""); rail.setAttribute("aria-hidden", "true"); } else { rail?.removeAttribute("inert"); rail?.removeAttribute("aria-hidden"); }
  if (workspace && openOnMobile) { workspace.setAttribute("inert", ""); workspace.setAttribute("aria-hidden", "true"); railCloseButton?.focus(); } else { workspace?.removeAttribute("inert"); workspace?.removeAttribute("aria-hidden"); if (restoreFocus) navToggle?.focus(); }
}

function metricValue(farm) { return farm[currentMetric]; }
function metricBar(value, sortedFarms) {
  if (currentMetric === "cost") return Math.max(18, (sortedFarms[0].cost / value) * 100);
  return Math.max(18, (value / Math.max(...sortedFarms.map(metricValue))) * 100);
}

function renderFarmRows() {
  const config = metricConfig[currentMetric];
  const sorted = [...farms].sort((left, right) => config.direction === "asc" ? metricValue(left) - metricValue(right) : metricValue(right) - metricValue(left));
  const container = document.querySelector("#farm-comparison");
  if (!container) return;
  container.replaceChildren();
  container.setAttribute("aria-labelledby", `metric-${currentMetric}`);
  sorted.forEach((farm, index) => {
    const wrapper = makeElement("div", "farm-row-wrap");
    const button = makeElement("button", `farm-row${farm.code === currentFarmCode ? " is-selected" : ""}`);
    button.type = "button"; button.dataset.farm = farm.code; button.setAttribute("aria-pressed", String(farm.code === currentFarmCode));
    const rank = makeElement("span", "farm-rank", String(index + 1).padStart(2, "0"));
    const name = makeElement("span", "farm-name"); name.append(makeElement("strong", "", farm.name), makeElement("small", "", `${farm.code} · biên ${formatNumber(farm.margin, 2)}%`));
    const bar = makeElement("span", "farm-bar"); bar.style.setProperty("--bar", `${metricBar(metricValue(farm), sorted)}%`); bar.setAttribute("aria-hidden", "true"); bar.append(makeElement("i"));
    button.append(rank, name, bar, makeElement("span", "farm-value", config.format(metricValue(farm))));
    button.setAttribute("aria-label", `${farm.name}, hạng ${index + 1}, ${config.label} ${config.format(metricValue(farm))}`);
    button.addEventListener("click", () => selectFarm(farm.code, true));
    wrapper.append(button); container.append(wrapper);
  });
  setText("[data-metric-hint]", config.hint);
}

function renderPlot(board, farm) {
  if (!board) return;
  board.replaceChildren(); board.setAttribute("aria-label", `Các khu vực của ${farm.name}`);
  farm.fields.forEach((field) => {
    const button = makeElement("button", `plot-cell ${field.status}${field.code === currentFieldCode ? " is-selected" : ""}`);
    button.type = "button"; button.dataset.field = field.code; button.setAttribute("aria-pressed", String(field.code === currentFieldCode));
    button.append(makeElement("strong", "", field.name), makeElement("small", "", `${field.crop} · ${formatNumber(field.area, 2)} ha`), makeElement("span", "", `${statusLabel(field.status)} · ${field.risk}/100`));
    button.setAttribute("aria-label", `${field.name}, ${field.crop}, ${formatNumber(field.area, 2)} hecta, ${statusLabel(field.status)}, điểm ${field.risk} trên 100`);
    button.addEventListener("click", () => selectField(field.code, true)); board.append(button);
  });
}

function renderFarmDetail() {
  const farm = selectedFarm();
  if (!farm.fields.some((field) => field.code === currentFieldCode)) currentFieldCode = farm.fields.find((field) => field.status !== "healthy")?.code ?? farm.fields[0].code;
  const field = selectedField();
  const riskyFields = farm.fields.filter((item) => item.status !== "healthy").length;
  setText("[data-farm-code]", farm.code); setText("[data-farm-title], [data-mobile-farm-title]", farm.name);
  setText("[data-farm-area]", `${formatNumber(farm.area, 2)} ha`); setText("[data-farm-harvested-area]", `${formatNumber(farm.harvestedArea, 2)} ha`); setText("[data-farm-yield]", `${formatNumber(farm.yield, 0)} kg/ha`); setText("[data-farm-alerts]", String(farm.alerts));
  setText("[data-farm-risk]", riskyFields ? `${riskyFields} khu vực cần xem` : "Không có cảnh báo khu vực");
  renderPlot(document.querySelector("[data-plot-board]"), farm); renderPlot(document.querySelector("[data-mobile-plot]"), farm);
  const riskText = `${statusLabel(field.status)} · ${field.risk}/100`;
  setText("[data-field-title], [data-mobile-field-title], [data-dialog-field-title]", `${field.name} · ${field.crop}`);
  setText("[data-field-risk], [data-mobile-field-risk], [data-dialog-risk]", riskText); setText("[data-field-reading], [data-mobile-field-reading], [data-dialog-reading]", formatReading(field.reading));
  setText("[data-field-moisture], [data-mobile-field-moisture]", `${formatNumber(field.moisture, 2)}%`); setText("[data-field-action], [data-mobile-field-action], [data-dialog-action]", field.action);
  setText("[data-dialog-field-code]", field.code); setText("[data-dialog-area]", `${formatNumber(field.area, 2)} ha`);
}

function renderMetricTabs() {
  document.querySelectorAll("[data-metric]").forEach((tab) => { const selected = tab.dataset.metric === currentMetric; tab.setAttribute("aria-selected", String(selected)); tab.tabIndex = selected ? 0 : -1; });
  const farmSelect = filterDialog?.querySelector("select[name='farm']"); const metricSelect = filterDialog?.querySelector("select[name='metric']");
  if (farmSelect) farmSelect.value = currentFarmCode; if (metricSelect) metricSelect.value = currentMetric;
}
function renderAll() { renderMetricTabs(); renderFarmRows(); renderFarmDetail(); }

function updateUrl(mode = "push") {
  const url = new URL(window.location.href); url.search = ""; url.searchParams.set("metric", currentMetric); url.searchParams.set("farm", currentFarmCode); url.searchParams.set("field", currentFieldCode);
  if (mode !== "replace" && url.href === window.location.href) return;
  history[mode === "replace" ? "replaceState" : "pushState"]({}, "", url);
}
function selectFarm(code, focusRow = false) {
  const farm = farms.find((item) => item.code === code); if (!farm) return;
  if (farm.code === currentFarmCode) { if (focusRow) document.querySelector(`[data-farm="${farm.code}"]`)?.focus(); return; }
  currentFarmCode = farm.code; currentFieldCode = farm.fields.find((field) => field.status !== "healthy")?.code ?? farm.fields[0].code; renderAll(); updateUrl(); announce(`Đã chọn ${farm.name}.`);
  if (focusRow) requestAnimationFrame(() => document.querySelector(`[data-farm="${farm.code}"]`)?.focus());
}
function selectField(code, focusField = false) {
  if (!selectedFarm().fields.some((field) => field.code === code)) return;
  if (code === currentFieldCode) { if (focusField) focusSelectedPlot(); return; }
  currentFieldCode = code; renderFarmDetail(); updateUrl(); const field = selectedField(); announce(`Đã chọn ${field.name}, ${statusLabel(field.status)}.`);
  if (focusField) focusSelectedPlot();
}
function selectMetric(metric) {
  if (!Object.hasOwn(metricConfig, metric) || metric === currentMetric) return; currentMetric = metric; renderMetricTabs(); renderFarmRows(); updateUrl(); announce(`Đã xếp hạng theo ${metricConfig[metric].label}.`);
}
function handleMetricKeydown(event) {
  if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
  const tabs = Array.from(document.querySelectorAll("[data-metric]")); const index = tabs.indexOf(event.currentTarget);
  const next = event.key === "Home" ? 0 : event.key === "End" ? tabs.length - 1 : event.key === "ArrowRight" ? (index + 1) % tabs.length : (index - 1 + tabs.length) % tabs.length;
  event.preventDefault(); selectMetric(tabs[next].dataset.metric); tabs[next].focus();
}

function openFieldEvidence() {
  fieldEvidenceOrigin = mapDialog?.open ? "map" : "inline";
  if (fieldEvidenceOrigin === "map") mapDialog.close();
  fieldDialog?.showModal();
}
function restoreFromUrl() {
  const params = new URLSearchParams(window.location.search); const metric = params.get("metric"); const farm = farms.find((item) => item.code === params.get("farm"));
  currentMetric = metric && Object.hasOwn(metricConfig, metric) ? metric : "profit"; currentFarmCode = farm?.code ?? farms[0].code;
  const field = selectedFarm().fields.find((item) => item.code === params.get("field")); currentFieldCode = field?.code ?? selectedFarm().fields.find((item) => item.status !== "healthy")?.code ?? selectedFarm().fields[0].code; renderAll(); updateUrl("replace");
}

document.querySelectorAll("svg:not([role='img'])").forEach((icon) => { icon.setAttribute("aria-hidden", "true"); icon.setAttribute("focusable", "false"); });
navToggle?.addEventListener("click", () => setRailOpen(navToggle.getAttribute("aria-expanded") !== "true"));
document.querySelectorAll("[data-nav-close]").forEach((element) => element.addEventListener("click", () => setRailOpen(false, true)));
document.querySelectorAll(".rail-nav a").forEach((link) => link.addEventListener("click", () => setRailOpen(false, true)));
mobileNavigation.addEventListener("change", () => setRailOpen(false));
mobileMapLayout.addEventListener("change", () => { if (!mobileMapLayout.matches && mapDialog?.open) { mapDialog.close(); focusSelectedPlot(); } });
document.addEventListener("keydown", (event) => { if (event.key === "Escape" && !document.querySelector("dialog[open]") && navToggle?.getAttribute("aria-expanded") === "true") setRailOpen(false, true); });
document.querySelectorAll("[data-metric]").forEach((tab) => { tab.addEventListener("click", () => selectMetric(tab.dataset.metric)); tab.addEventListener("keydown", handleMetricKeydown); });
document.querySelectorAll("[data-open-filter]").forEach((button) => button.addEventListener("click", () => {
  if (!filterDialog) return;
  renderMetricTabs();
  filterDialog.returnValue = "";
  filterDialog.showModal();
}));
document.querySelector("[data-open-map]")?.addEventListener("click", () => { if (mobileMapLayout.matches) mapDialog?.showModal(); });
document.querySelectorAll("[data-open-field]").forEach((button) => button.addEventListener("click", openFieldEvidence));
document.querySelectorAll("[data-close-dialog]").forEach((button) => button.addEventListener("click", () => button.closest("dialog")?.close()));
document.querySelectorAll("dialog").forEach((dialog) => dialog.addEventListener("click", (event) => { if (event.target === dialog) dialog.close(); }));
fieldDialog?.addEventListener("close", () => {
  const shouldReturnToMap = fieldEvidenceOrigin === "map" && mobileMapLayout.matches;
  fieldEvidenceOrigin = "inline";
  if (shouldReturnToMap) { mapDialog?.showModal(); focusSelectedPlot(); return; }
  const focusTarget = mobileMapLayout.matches ? "[data-open-map]" : ".farm-atlas [data-open-field]";
  requestAnimationFrame(() => document.querySelector(focusTarget)?.focus());
});
filterDialog?.addEventListener("close", () => { if (filterDialog.returnValue !== "apply") return; const form = new FormData(filterDialog.querySelector("form")); const metric = String(form.get("metric")); const farm = farms.find((item) => item.code === String(form.get("farm"))); if (!Object.hasOwn(metricConfig, metric) || !farm) return; const farmChanged = farm.code !== currentFarmCode; currentMetric = metric; currentFarmCode = farm.code; if (farmChanged) currentFieldCode = farm.fields.find((field) => field.status !== "healthy")?.code ?? farm.fields[0].code; renderAll(); updateUrl(); announce(`Đã áp dụng ${metricConfig[currentMetric].label} cho ${selectedFarm().name}.`); });
window.addEventListener("popstate", restoreFromUrl);

setRailOpen(false); restoreFromUrl();
