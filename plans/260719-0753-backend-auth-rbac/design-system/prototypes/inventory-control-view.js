(() => {
  const rawFixture = window.AGRI_INVENTORY_FIXTURE;
  const emptyFixture = { scope: {}, alerts: [], abc: [] };
  const severityValues = new Set(["all", "critical", "warning", "watch"]);
  const typeValues = new Set(["all", "stockout", "low_stock", "expiring_soon", "overstock"]);
  const unitLabels = { kg: "kg", liter: "lít", piece: "cái" };
  const severityLabels = { critical: "Nghiêm trọng", warning: "Cảnh báo", watch: "Theo dõi" };
  const alertTypeLabels = { stockout: "Hết hàng", low_stock: "Thiếu hàng", expiring_soon: "Sắp hết hạn", overstock: "Tồn dư" };
  const unitValues = new Set(Object.keys(unitLabels));
  const abcValues = new Set(["A", "B", "C"]);
  const alertNonNegativeFields = ["reorderPoint", "targetStock", "inventoryValue", "recommendedOrder", "predictedNeed30d"];
  const abcNumberFields = ["inventoryValue", "scopeValueShare", "scopeCumulativeShare"];

  function isRecord(value) { return Boolean(value && typeof value === "object" && !Array.isArray(value)); }
  function hasText(value) { return typeof value === "string" && value.trim().length > 0; }
  function hasFiniteNumbers(value, fields) { return fields.every((field) => Number.isFinite(value[field])); }
  function isNonNegative(value) { return Number.isFinite(value) && value >= 0; }
  function isIsoDate(value) { return typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value); }
  function hasUniqueValue(items, field) { return new Set(items.map((item) => item[field])).size === items.length; }
  function validBatch(batch) { return batch == null || (isRecord(batch) && ["batchCode", "supplierName", "transactionId"].every((field) => hasText(batch[field]))); }
  function validMovement(movement) { return movement == null || (isRecord(movement) && isIsoDate(movement.date) && ["IN", "OUT"].includes(movement.type) && unitValues.has(movement.unit) && Number.isFinite(movement.quantity)); }
  function validAlert(alert) {
    const textFields = ["id", "materialCode", "materialName", "category", "abcClass", "unit", "alertType", "severity", "message", "action"];
    return isRecord(alert) && textFields.every((field) => hasText(alert[field])) && severityValues.has(alert.severity) && alert.severity !== "all" && typeValues.has(alert.alertType) && alert.alertType !== "all" && abcValues.has(alert.abcClass) && unitValues.has(alert.unit) && Number.isFinite(alert.quantity) && alertNonNegativeFields.every((field) => isNonNegative(alert[field])) && (alert.daysSupply == null || isNonNegative(alert.daysSupply)) && (alert.nearestExpiry == null || isIsoDate(alert.nearestExpiry)) && validBatch(alert.batchEvidence) && validMovement(alert.lastMovement);
  }
  function validAbcRow(row) { return isRecord(row) && ["materialCode", "materialName", "category", "abcClass"].every((field) => hasText(row[field])) && abcValues.has(row.abcClass) && hasFiniteNumbers(row, abcNumberFields) && abcNumberFields.every((field) => isNonNegative(row[field])) && row.scopeValueShare <= 100 && row.scopeCumulativeShare <= 100; }
  function validFixture(value) {
    if (!isRecord(value) || !isRecord(value.scope) || !Array.isArray(value.alerts) || !Array.isArray(value.abc) || value.alerts.length > 50 || value.abc.length > 100) return false;
    const counts = ["alerts", "locations", "critical", "stockout", "expiring"];
    if (value.scope.code !== "WH-001" || !hasText(value.scope.name) || !isNonNegative(value.scope.inventoryValue) || !counts.every((field) => Number.isInteger(value.scope[field]) && value.scope[field] >= 0)) return false;
    if (!value.alerts.every(validAlert) || !value.abc.every(validAbcRow) || !hasUniqueValue(value.alerts, "id") || !hasUniqueValue(value.abc, "materialCode")) return false;
    const abcCodes = new Set(value.abc.map((row) => row.materialCode)); const inventoryValue = value.abc.reduce((total, row) => total + row.inventoryValue, 0);
    return value.scope.alerts === value.alerts.length && value.scope.locations === value.abc.length && value.scope.critical === value.alerts.filter((alert) => alert.severity === "critical").length && value.scope.stockout === value.alerts.filter((alert) => alert.alertType === "stockout").length && value.scope.expiring === value.alerts.filter((alert) => alert.alertType === "expiring_soon").length && value.alerts.every((alert) => abcCodes.has(alert.materialCode)) && Math.abs(value.scope.inventoryValue - inventoryValue) < 0.01;
  }

  const fixtureAvailable = validFixture(rawFixture);
  const fixture = fixtureAvailable ? rawFixture : emptyFixture;

  function formatNumber(value, digits = 2) { return new Intl.NumberFormat("vi-VN", { minimumFractionDigits: digits, maximumFractionDigits: digits }).format(value); }
  function formatDate(value) { if (!value) return "Chưa xác định"; const [year, month, day] = value.split("-"); return `${day}/${month}/${year}`; }
  function formatQuantity(value, unit) { return `${formatNumber(value, 2)} ${unitLabels[unit] ?? unit}`; }
  function formatVnd(value) { return value >= 1e9 ? `${formatNumber(value / 1e9, 2)} tỷ ₫` : `${formatNumber(value / 1e6, 2)} triệu ₫`; }
  function daysSupplyLabel(value) { return value === null || value === undefined ? "Không xác định" : `${formatNumber(value, 2)} ngày`; }
  function movementLabel(movement) { if (!movement) return "Chưa có movement"; const type = movement.type === "IN" ? "Nhập" : movement.type === "OUT" ? "Xuất" : movement.type; return `${type} · ${formatDate(movement.date)} · ${formatQuantity(movement.quantity, movement.unit)}`; }
  function setText(selector, value) { document.querySelectorAll(selector).forEach((element) => { element.textContent = value; }); }
  function makeElement(tag, className, text) { const element = document.createElement(tag); if (className) element.className = className; if (text !== undefined) element.textContent = text; return element; }
  function appendCell(row, tag, text, scope) { const cell = makeElement(tag, "", text); if (scope) cell.scope = scope; row.append(cell); }

  function alertsFor(state) {
    return fixture.alerts.filter((alert) => (state.severity === "all" || alert.severity === state.severity) && (state.type === "all" || alert.alertType === state.type));
  }

  function alertForUrl(state, alertId, materialCode) {
    const filtered = alertsFor(state);
    if (alertId) return filtered.find((alert) => alert.id === alertId && (!materialCode || alert.materialCode === materialCode));
    return materialCode ? filtered.find((alert) => alert.materialCode === materialCode) : undefined;
  }

  function renderSummary() {
    setText("[data-scope-value]", formatVnd(fixture.scope.inventoryValue ?? 0));
    setText("[data-scope-critical]", String(fixture.scope.critical ?? 0));
    setText("[data-scope-stockout]", String(fixture.scope.stockout ?? 0));
    setText("[data-scope-expiring]", String(fixture.scope.expiring ?? 0));
  }

  function renderTabs(state) {
    document.querySelectorAll("[data-severity]").forEach((tab) => {
      const selected = tab.dataset.severity === state.severity;
      tab.setAttribute("aria-selected", String(selected));
      tab.tabIndex = selected ? 0 : -1;
    });
    document.querySelector("#inventory-queue")?.setAttribute("aria-labelledby", `severity-${state.severity}`);
  }

  function renderQueueTable(alerts) {
    const body = document.querySelector("[data-queue-table-body]");
    if (!body) return;
    body.replaceChildren();
    alerts.forEach((alert) => {
      const row = document.createElement("tr");
      appendCell(row, "td", severityLabels[alert.severity] ?? alert.severity);
      appendCell(row, "th", `${alert.materialName} · ${alert.materialCode}`, "row");
      appendCell(row, "td", alertTypeLabels[alert.alertType] ?? alert.alertType);
      appendCell(row, "td", formatQuantity(alert.quantity, alert.unit));
      appendCell(row, "td", daysSupplyLabel(alert.daysSupply));
      appendCell(row, "td", formatDate(alert.nearestExpiry));
      appendCell(row, "td", alert.abcClass);
      body.append(row);
    });
  }

  function renderQueue(state, onSelect) {
    const alerts = alertsFor(state);
    const container = document.querySelector("#inventory-queue");
    const empty = document.querySelector("[data-queue-empty]");
    if (container) container.replaceChildren();
    empty?.toggleAttribute("hidden", alerts.length > 0);
    container?.toggleAttribute("hidden", alerts.length === 0);
    const clearButton = document.querySelector("[data-clear-filter]");
    if (clearButton) { clearButton.hidden = false; clearButton.disabled = false; }
    setText("[data-result-count]", `${alerts.length} cảnh báo trong bộ lọc`);
    setText("[data-filter-count]", String(Number(state.severity !== "all") + Number(state.type !== "all")));
    alerts.forEach((alert) => {
      const wrapper = makeElement("div", "alert-row-wrap");
      const selected = alert.id === state.alertId;
      const button = makeElement("button", `alert-row${selected ? " is-selected" : ""}`);
      button.type = "button"; button.dataset.alertId = alert.id; button.setAttribute("aria-pressed", String(selected));
      button.setAttribute("aria-label", `${severityLabels[alert.severity]}, ${alert.materialName}, ${alertTypeLabels[alert.alertType]}, số dư ${formatQuantity(alert.quantity, alert.unit)}`);
      const symbol = makeElement("span", `alert-symbol ${alert.severity}`, alert.severity === "watch" ? "i" : "!"); symbol.setAttribute("aria-hidden", "true");
      const main = makeElement("span", "alert-main"); main.append(makeElement("strong", "", alert.materialName), makeElement("span", "", `${severityLabels[alert.severity]} · ${alertTypeLabels[alert.alertType]}`), makeElement("small", "", `${alert.materialCode} · ABC ${alert.abcClass} · ${alert.category}`));
      const balance = makeElement("span", "alert-balance"); balance.append(makeElement("strong", "", formatQuantity(alert.quantity, alert.unit)), makeElement("span", "", `${daysSupplyLabel(alert.daysSupply)} cung ứng`));
      button.append(symbol, main, balance, makeElement("span", "alert-action", alert.action));
      button.addEventListener("click", () => onSelect(alert.id, true)); wrapper.append(button); container?.append(wrapper);
    });
    renderQueueTable(alerts);
    return alerts;
  }

  function renderEvidence(alert) {
    const panel = document.querySelector(".inventory-evidence");
    const heading = document.querySelector(".inventory-evidence > header");
    if (heading) heading.hidden = !alert;
    if (panel && alert) { panel.setAttribute("aria-labelledby", "evidence-title"); panel.removeAttribute("aria-label"); }
    if (panel && !alert) { panel.removeAttribute("aria-labelledby"); panel.setAttribute("aria-label", "Bằng chứng số dư"); }
    document.querySelectorAll("[data-evidence-empty]").forEach((element) => { element.hidden = Boolean(alert); });
    document.querySelectorAll("[data-evidence-content]").forEach((element) => { element.hidden = !alert; });
    document.querySelectorAll("[data-open-evidence]").forEach((button) => { button.disabled = !alert; });
    if (!alert) return;
    const severity = severityLabels[alert.severity] ?? alert.severity;
    const batch = alert.batchEvidence;
    const batchCode = batch?.batchCode ?? "Không có batch khớp";
    const supplier = batch?.supplierName ?? "Chưa xác định";
    const expiry = formatDate(alert.nearestExpiry);
    const movement = movementLabel(alert.lastMovement);
    setText("[data-material-code], [data-dialog-material-code]", alert.materialCode);
    setText("[data-material-title], [data-dialog-material-title]", alert.materialName);
    setText("[data-severity-badge]", severity); setText("[data-stock-quantity], [data-dialog-stock]", formatQuantity(alert.quantity, alert.unit));
    setText("[data-days-supply]", daysSupplyLabel(alert.daysSupply)); setText("[data-predicted-need]", formatQuantity(alert.predictedNeed30d, alert.unit));
    setText("[data-order-quantity]", formatQuantity(alert.recommendedOrder, alert.unit)); setText("[data-inventory-value]", formatVnd(alert.inventoryValue));
    setText("[data-alert-type]", alertTypeLabels[alert.alertType] ?? alert.alertType); setText("[data-alert-message]", alert.message); setText("[data-alert-action], [data-dialog-action]", alert.action);
    setText("[data-reorder-label]", `Đặt hàng ${formatQuantity(alert.reorderPoint, alert.unit)}`); setText("[data-target-label]", `Mục tiêu ${formatQuantity(alert.targetStock, alert.unit)}`);
    setText("[data-dialog-reorder]", formatQuantity(alert.reorderPoint, alert.unit)); setText("[data-batch-code]", batchCode); setText("[data-expiry-label]", `Hạn ${expiry}`);
    setText("[data-supplier-name], [data-dialog-supplier]", supplier); setText("[data-receipt-id]", batch?.transactionId ?? "Không có phiếu khớp");
    setText("[data-last-movement], [data-dialog-movement]", movement); setText("[data-dialog-batch]", `${batchCode} · ${expiry}`);
    document.querySelectorAll("[data-severity-badge]").forEach((badge) => { badge.className = `severity-badge ${alert.severity}`; });
    const target = Math.max(alert.targetStock, 1); const targetRatio = (alert.quantity / target) * 100;
    const stockContext = alert.quantity <= 0 ? "Số dư âm hoặc bằng 0" : alert.quantity < alert.reorderPoint ? "Dưới điểm đặt hàng" : alert.quantity > alert.targetStock ? "Vượt mục tiêu" : "Trong ngưỡng";
    setText("[data-stock-context]", `${stockContext} · ${formatNumber(targetRatio, 0)}% mục tiêu`);
    const stockScale = Math.min(1, Math.max(0, alert.quantity / target)); const reorder = Math.min(100, Math.max(0, (alert.reorderPoint / target) * 100));
    document.querySelectorAll("[data-stock-gauge]").forEach((gauge) => { gauge.style.setProperty("--stock-scale", String(stockScale)); gauge.style.setProperty("--reorder", `${reorder}%`); gauge.setAttribute("aria-label", `Số dư ${formatQuantity(alert.quantity, alert.unit)}; điểm đặt hàng ${formatQuantity(alert.reorderPoint, alert.unit)}; mục tiêu ${formatQuantity(alert.targetStock, alert.unit)}`); });
  }

  function renderAbc() {
    const bars = document.querySelector("[data-abc-bars]"); const body = document.querySelector("[data-abc-table-body]");
    bars?.replaceChildren(); body?.replaceChildren(); let maximum = 1;
    fixture.abc.forEach((row) => { maximum = Math.max(maximum, row.scopeValueShare); });
    fixture.abc.slice(0, 5).forEach((item) => {
      const row = makeElement("div", "abc-row"); const name = makeElement("span", "abc-name"); name.append(makeElement("strong", "", item.materialName), makeElement("span", "", `${item.materialCode} · ABC ${item.abcClass}`));
      const track = makeElement("span", "abc-track"); track.style.setProperty("--share", `${(item.scopeValueShare / maximum) * 100}%`); track.setAttribute("aria-hidden", "true"); track.append(makeElement("i"));
      row.append(name, track, makeElement("span", "abc-value", `${formatVnd(item.inventoryValue)} · ${formatNumber(item.scopeValueShare, 2)}%`)); bars?.append(row);
    });
    fixture.abc.forEach((item) => { const row = document.createElement("tr"); appendCell(row, "th", `${item.materialName} · ${item.materialCode}`, "row"); appendCell(row, "td", item.category); appendCell(row, "td", item.abcClass); appendCell(row, "td", formatVnd(item.inventoryValue)); appendCell(row, "td", `${formatNumber(item.scopeValueShare, 2)}%`); appendCell(row, "td", `${formatNumber(item.scopeCumulativeShare, 2)}%`); body?.append(row); });
  }

  function syncFilterControls(state) { const severity = document.querySelector("select[name='severity']"); const type = document.querySelector("select[name='type']"); if (severity) severity.value = state.severity; if (type) type.value = state.type; }
  function renderUnavailable(state) {
    renderTabs(state); setText("[data-scope-value], [data-scope-critical], [data-scope-stockout], [data-scope-expiring]", "—");
    setText("[data-result-count]", "Dữ liệu tồn kho không khả dụng"); setText("[data-filter-count]", "0");
    setText("[data-queue-empty] strong", "Không thể xác minh dữ liệu tồn kho"); setText("[data-queue-empty] span", "Fixture không đạt hợp đồng dữ liệu; hàng đợi đã dừng để tránh hiển thị sai.");
    setText(".inventory-heading > div:first-child > span", "WH-001 · fixture không khả dụng · không hiển thị số liệu"); setText(".inventory-kpis article:first-child > small", "Không thể xác minh SKU-location");
    setText(".quality-banner strong", "Dữ liệu tồn kho không khả dụng"); setText(".quality-banner div > span", "Fixture không đạt kiểm tra cấu trúc, số hữu hạn và khóa duy nhất.");
    setText(".abc-panel > header div > span, .abc-table summary", "Không thể xác minh phân bổ ABC từ fixture hiện tại."); setText(".abc-note", "Không khả dụng");
    const container = document.querySelector("#inventory-queue"); const empty = document.querySelector("[data-queue-empty]"); const clearButton = document.querySelector("[data-clear-filter]");
    container?.replaceChildren(); container?.setAttribute("hidden", ""); empty?.removeAttribute("hidden"); if (clearButton) { clearButton.hidden = true; clearButton.disabled = true; }
    renderQueueTable([]); renderEvidence(); const bars = document.querySelector("[data-abc-bars]"); bars?.replaceChildren(makeElement("p", "data-unavailable", "Không thể tải phân bổ ABC.")); document.querySelector("[data-abc-table-body]")?.replaceChildren(); syncFilterControls(state); return [];
  }
  function render(state, onSelect) { if (!fixtureAvailable) return renderUnavailable(state); renderSummary(); renderTabs(state); const alerts = renderQueue(state, onSelect); renderEvidence(alerts.find((alert) => alert.id === state.alertId)); renderAbc(); syncFilterControls(state); return alerts; }

  window.AGRI_INVENTORY_VIEW = { fixture, fixtureAvailable, severityValues, typeValues, severityLabels, alertTypeLabels, alertsFor, alertForUrl, render, renderEvidence, syncFilterControls };
})();
