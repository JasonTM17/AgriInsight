(() => {
  const rawFixture = window.AGRI_COST_FIXTURE;
  const lensValues = new Set(["operating", "procurement"]);
  const unitLabels = { kg: "kg", liter: "lít", piece: "cái" };
  const rowLimits = { trend: 36, drivers: 50, comparison: 50 };
  function isRecord(value) { return Boolean(value && typeof value === "object" && !Array.isArray(value)); }
  function hasText(record, fields) { return isRecord(record) && fields.every((field) => typeof record[field] === "string" && record[field].trim().length > 0); }
  function hasNumbers(record, fields) { return isRecord(record) && fields.every((field) => Number.isFinite(record[field])); }
  function hasUniqueIds(rows) { return new Set(rows.map((row) => row.id)).size === rows.length; }
  function isIsoDate(value) { const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value ?? ""); if (!match) return false; const [, year, month, day] = match.map(Number); const date = new Date(Date.UTC(year, month - 1, day)); return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day; }
  function isIsoDateTime(value) { const match = /^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(value ?? ""); return Boolean(match && isIsoDate(match[1]) && Number(match[2]) <= 23 && Number(match[3]) <= 59 && Number(match[4] ?? 0) <= 59); }
  function packageIsRenderable(data, lens) {
    if (!hasText(data, ["title", "eyebrow", "definition"]) || typeof data.defaultSelection !== "string" || !Array.isArray(data.summary) || data.summary.length > 8) return false;
    if (!data.summary.every((row) => hasText(row, ["label", "kind", "note"]) && (typeof row.value === "string" || Number.isFinite(row.value)))) return false;
    if (!isRecord(data.trend) || !hasText(data.trend, ["title", "summary"]) || !Array.isArray(data.trend.rows) || data.trend.rows.length > rowLimits.trend) return false;
    const trendNumbers = lens === "operating" ? ["cost", "revenue", "profit"] : ["spend", "transactions"];
    if (!data.trend.rows.every((row) => hasText(row, ["month"]) && /^\d{4}-\d{2}$/.test(row.month) && hasNumbers(row, trendNumbers))) return false;
    if (!isRecord(data.reconciliation) || !hasText(data.reconciliation, ["title", "status", "note"]) || !Array.isArray(data.reconciliation.rows) || data.reconciliation.rows.length > 10) return false;
    if (!data.reconciliation.rows.every((row) => hasText(row, ["label", "kind"]) && (typeof row.value === "string" || Number.isFinite(row.value)))) return false;
    if (!Array.isArray(data.drivers) || data.drivers.length > rowLimits.drivers || !hasUniqueIds(data.drivers)) return false;
    const driverText = lens === "operating" ? ["id", "name"] : ["id", "name", "province"];
    const driverNumbers = lens === "operating" ? ["count", "material", "labor", "total", "share"] : ["quality", "transactions", "spend", "share"];
    const latestText = lens === "operating" ? ["id", "at", "farm", "field", "season", "crop", "material"] : ["id", "date", "farm", "warehouse", "material", "unit", "batch", "expiry"];
    const latestNumbers = lens === "operating" ? ["materialCost", "laborCost", "total"] : ["quantity", "unitCost", "spend"];
    if (!data.drivers.every((row) => hasText(row, driverText) && hasNumbers(row, driverNumbers) && hasText(row.latest, latestText) && hasNumbers(row.latest, latestNumbers))) return false;
    if (data.drivers.length ? !data.drivers.some((row) => row.id === data.defaultSelection) : data.defaultSelection.trim().length > 0) return false;
    if (lens === "operating" && !data.drivers.every((row) => isIsoDateTime(row.latest.at))) return false;
    if (lens === "procurement" && !data.drivers.every((row) => isIsoDate(row.latest.date) && isIsoDate(row.latest.expiry))) return false;
    const comparisonText = lens === "operating" ? ["code", "name", "province"] : ["code", "name", "category", "unit"];
    const comparisonNumbers = lens === "operating" ? ["area", "cost", "variance", "profit", "margin", "costHa", "costKg"] : ["transactions", "quantity", "spend", "share", "average"];
    if (!Array.isArray(data.comparison) || data.comparison.length > rowLimits.comparison || !data.comparison.every((row) => hasText(row, comparisonText) && hasNumbers(row, comparisonNumbers))) return false;
    return isRecord(data.export) && hasText(data.export, ["lens", "scope", "contract", "cutoff"]) && isIsoDate(data.export.cutoff) && hasNumbers(data.export, ["estimatedRows", "columns"]) && data.export.estimatedRows >= 0 && data.export.estimatedRows <= 25000 && data.export.columns >= 0;
  }
  const meta = hasText(rawFixture?.meta, ["runId", "asOf", "pipeline", "scope", "role"]) ? rawFixture.meta : { runId: "unavailable", asOf: "", pipeline: "unavailable", scope: "Phạm vi hiện tại", role: "Quyền hiện tại" };
  function unavailablePackage(lens) { const title = lens === "procurement" ? "Chi tiêu mua hàng" : "Chi phí vận hành"; return { title, eyebrow: "Dữ liệu không khả dụng", definition: "Fixture không vượt qua schema và row-budget gate; trang không hiển thị số liệu một phần.", defaultSelection: "", summary: [{ label: "Trạng thái dữ liệu", value: "Không khả dụng", kind: "text", note: "Kiểm tra fixture, cutoff và manifest" }], trend: { title: "Không có xu hướng an toàn", summary: "Không dựng biểu đồ từ package thiếu hoặc vượt giới hạn.", rows: [] }, reconciliation: { title: "Không thể đối soát", status: "Package bị từ chối", note: "Không tạo tổng hoặc số 0 thay thế.", rows: [] }, drivers: [], comparison: [], export: { lens: title, scope: meta.scope, contract: "Không khả dụng", estimatedRows: 0, columns: 0, cutoff: meta.asOf } }; }
  const packages = { operating: packageIsRenderable(rawFixture?.operating, "operating") ? rawFixture.operating : unavailablePackage("operating"), procurement: packageIsRenderable(rawFixture?.procurement, "procurement") ? rawFixture.procurement : unavailablePackage("procurement") };
  const fixture = { meta, ...packages };

  function formatNumber(value, maximumFractionDigits = 0) { return new Intl.NumberFormat("vi-VN", { maximumFractionDigits }).format(value); }
  function formatCompactVnd(value) {
    const absolute = Math.abs(value); const sign = value < 0 ? "−" : "";
    if (absolute >= 1e9) return `${sign}${formatNumber(absolute / 1e9, 2)} tỷ ₫`;
    if (absolute >= 1e6) return `${sign}${formatNumber(absolute / 1e6, 2)} triệu ₫`;
    return `${sign}${formatNumber(absolute)} ₫`;
  }
  function formatSignedVnd(value) { return `${value > 0 ? "+" : ""}${formatCompactVnd(value)}`; }
  function formatFullVnd(value) { return `${new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 0 }).format(value)} ₫`; }
  function formatPercent(value) { return `${formatNumber(value, 2)}%`; }
  function formatMetric(metric) {
    if (metric.kind === "vnd") return formatCompactVnd(metric.value);
    if (metric.kind === "signed-vnd") return formatSignedVnd(metric.value);
    if (metric.kind === "integer") return formatNumber(metric.value);
    return String(metric.value);
  }
  function formatMonth(value) { const [year, month] = value.split("-"); return `${month}/${year}`; }
  function formatDate(value) { if (!isIsoDate(value)) return "Không xác định"; const [year, month, day] = value.split("-"); return `${day}/${month}/${year}`; }
  function formatDateTime(value) { if (!isIsoDateTime(value)) return "Không xác định"; const [date, time] = value.split("T"); return `${formatDate(date)} · ${time.slice(0, 5)}`; }
  function formatQuantity(value, unit) { return `${formatNumber(value, 3)} ${unitLabels[unit] ?? unit}`; }
  function formatVariance(value) { return value <= 0 ? `Thấp hơn ${formatCompactVnd(Math.abs(value))}` : `Vượt ${formatCompactVnd(value)}`; }
  function setText(selector, value) { document.querySelectorAll(selector).forEach((element) => { element.textContent = value; }); }
  function make(tag, className, text) { const element = document.createElement(tag); if (className) element.className = className; if (text !== undefined) element.textContent = text; return element; }
  function appendCell(row, tag, text, scope) { const cell = make(tag, "", text); if (scope) cell.scope = scope; row.append(cell); return cell; }
  function packageFor(lens) { return lens === "procurement" ? packages.procurement : packages.operating; }
  function normalizeSelection(lens, selection) { const data = packageFor(lens); const drivers = data.drivers ?? []; if (drivers.some((item) => item.id === selection)) return selection; return drivers.find((item) => item.id === data.defaultSelection)?.id ?? drivers[0]?.id ?? ""; }

  function renderIdentity(state, data) {
    setText("[data-lens-eyebrow]", data.eyebrow); setText("[data-lens-title]", data.title); setText("[data-lens-definition]", data.definition);
    setText("[data-filter-summary]", `${meta.scope} · ${data.eyebrow}`); document.title = `AgriInsight — ${data.title}`;
    document.querySelectorAll("[data-lens]").forEach((tab) => { const selected = tab.dataset.lens === state.lens; tab.setAttribute("aria-selected", String(selected)); tab.tabIndex = selected ? 0 : -1; });
    document.querySelector("#cost-workspace")?.setAttribute("aria-labelledby", `lens-${state.lens}`);
  }

  function renderSummary(data) {
    const container = document.querySelector("[data-kpis]"); container?.replaceChildren();
    data.summary.forEach((metric) => { const article = document.createElement("article"); article.append(make("span", "", metric.label), make("strong", "", formatMetric(metric)), make("small", "", metric.note)); container?.append(article); });
  }

  function renderTrend(state, data) {
    setText("[data-trend-title]", data.trend.title); setText("[data-trend-summary]", data.trend.summary);
    const bars = document.querySelector("[data-trend-bars]"); const head = document.querySelector("[data-trend-head]"); const body = document.querySelector("[data-trend-body]");
    bars?.replaceChildren(); head?.replaceChildren(); body?.replaceChildren(); bars?.setAttribute("aria-label", data.trend.summary);
    let maximum = 1; data.trend.rows.forEach((row) => { const value = state.lens === "operating" ? row.cost : row.spend; if (value > maximum) maximum = value; });
    data.trend.rows.forEach((item) => { const value = state.lens === "operating" ? item.cost : item.spend; const row = make("div", "trend-row"); const time = make("time", "", formatMonth(item.month)); time.dateTime = item.month; const track = make("span", "trend-track"); track.setAttribute("aria-hidden", "true"); track.append(make("i")); track.style.setProperty("--bar-scale", String(value / maximum)); row.append(time, track, make("strong", "", formatCompactVnd(value))); bars?.append(row); });
    const headerRow = document.createElement("tr"); const headers = state.lens === "operating" ? ["Tháng", "Chi phí vận hành", "Doanh thu", "Lợi nhuận vận hành"] : ["Tháng", "Chi tiêu mua hàng", "Số giao dịch"];
    headers.forEach((label) => appendCell(headerRow, "th", label, "col")); head?.append(headerRow);
    data.trend.rows.forEach((item) => { const row = document.createElement("tr"); appendCell(row, "th", formatMonth(item.month), "row"); if (state.lens === "operating") { appendCell(row, "td", formatFullVnd(item.cost)); appendCell(row, "td", formatFullVnd(item.revenue)); appendCell(row, "td", formatFullVnd(item.profit)); } else { appendCell(row, "td", formatFullVnd(item.spend)); appendCell(row, "td", formatNumber(item.transactions)); } body?.append(row); });
  }

  function renderReconciliation(data) {
    setText("[data-reconciliation-title]", data.reconciliation.title); setText("[data-reconciliation-status]", data.reconciliation.status); setText("[data-reconciliation-note]", data.reconciliation.note);
    const list = document.querySelector("[data-reconciliation-rows]"); list?.replaceChildren();
    data.reconciliation.rows.forEach((item) => { const row = make("div", item.emphasis ? "is-emphasis" : ""); row.append(make("dt", "", item.label), make("dd", "", item.kind === "vnd" ? formatCompactVnd(item.value) : String(item.value))); list?.append(row); });
  }

  function renderDrivers(state, data, onSelect) {
    const operating = state.lens === "operating"; setText("[data-driver-eyebrow]", operating ? "Activity cost drivers" : "Supplier spend drivers"); setText("[data-driver-title]", operating ? "Chi phí theo hoạt động" : "Chi tiêu theo nhà cung cấp");
    setText("[data-driver-summary]", operating ? `${data.drivers.length} nhóm · sắp theo chi phí giảm dần` : `${data.drivers.length} nhà cung cấp · sắp theo chi tiêu giảm dần`);
    const container = document.querySelector("[data-driver-list]"); container?.replaceChildren();
    if (!data.drivers.length) { const empty = make("div", "driver-empty"); empty.append(make("strong", "", "Không có dữ liệu trong phạm vi"), make("span", "", `Giữ nguyên ${meta.scope}; kiểm tra cutoff hoặc quyền dữ liệu trước khi đổi lens.`)); container?.append(empty); return; }
    data.drivers.forEach((item, index) => { const selected = item.id === state.selectedId; const button = make("button", "driver-row"); button.type = "button"; button.dataset.selectionId = item.id; button.setAttribute("aria-pressed", String(selected)); const meta = operating ? `${item.count} hoạt động · ${formatPercent(item.share)}` : `${item.transactions} phiếu · chất lượng ${formatNumber(item.quality, 1)}/5`; const value = operating ? item.total : item.spend; button.setAttribute("aria-label", `${item.name}, ${meta}, ${formatCompactVnd(value)}`); const main = make("span", "driver-main"); main.append(make("strong", "", item.name), make("span", "", `${item.id} · ${meta}`)); const amount = make("span", "driver-value"); amount.append(make("strong", "", formatCompactVnd(value)), make("span", "", operating ? "chi phí vận hành" : "procurement spend")); button.append(make("span", "driver-rank", String(index + 1).padStart(2, "0")), main, amount); button.addEventListener("click", () => onSelect(item.id)); container?.append(button); });
  }

  function appendFacts(container, facts) { container?.replaceChildren(); facts.forEach(([label, value]) => { const row = document.createElement("div"); row.append(make("dt", "", label), make("dd", "", value)); container?.append(row); }); }
  function renderEvidence(state, data) {
    const item = data.drivers.find((driver) => driver.id === state.selectedId) ?? data.drivers[0]; const operating = state.lens === "operating"; const latestPanel = document.querySelector(".latest-evidence");
    latestPanel?.toggleAttribute("hidden", !item);
    if (!item) { setText("[data-evidence-code]", "NO-DATA"); setText("[data-evidence-title]", "Không có bằng chứng trong phạm vi"); setText("[data-evidence-badge]", "0"); appendFacts(document.querySelector("[data-evidence-facts]"), [["Phạm vi", meta.scope], ["Cutoff", formatDate(meta.asOf)], ["Trạng thái", "Không có driver phù hợp"]]); setText("[data-evidence-boundary]", "Giữ nguyên lens và phạm vi; không hiển thị số 0 thay cho dữ liệu thiếu."); return; }
    const latest = item.latest;
    setText("[data-evidence-code]", operating ? `ACTIVITY · ${item.id}` : item.id); setText("[data-evidence-title]", item.name); setText("[data-evidence-badge]", operating ? formatPercent(item.share) : `${formatNumber(item.quality, 1)}/5`);
    const facts = operating ? [["Số hoạt động", formatNumber(item.count)], ["Tỷ trọng", formatPercent(item.share)], ["Vật tư", formatCompactVnd(item.material)], ["Nhân công", formatCompactVnd(item.labor)], ["Tổng vận hành", formatCompactVnd(item.total)]] : [["Tỉnh/TP", item.province], ["Chất lượng", `${formatNumber(item.quality, 1)}/5`], ["Số phiếu nhập", formatNumber(item.transactions)], ["Tỷ trọng", formatPercent(item.share)], ["Chi tiêu", formatCompactVnd(item.spend)]];
    appendFacts(document.querySelector("[data-evidence-facts]"), facts); setText("[data-latest-label]", operating ? "Hoạt động gần nhất" : "Phiếu nhập gần nhất"); setText("[data-latest-id]", latest.id);
    const latestFacts = operating ? [["Thời điểm", formatDateTime(latest.at)], ["Nông trại", latest.farm], ["Khu vực", latest.field], ["Mùa vụ", latest.season], ["Cây trồng", latest.crop], ["Vật tư", latest.material], ["Chi phí vật tư", formatFullVnd(latest.materialCost)], ["Chi phí nhân công", formatFullVnd(latest.laborCost)], ["Tổng hoạt động", formatFullVnd(latest.total)]] : [["Ngày nhập", formatDate(latest.date)], ["Nông trại", latest.farm], ["Kho", latest.warehouse], ["Vật tư", latest.material], ["Số lượng", formatQuantity(latest.quantity, latest.unit)], ["Đơn giá", formatFullVnd(latest.unitCost)], ["Chi tiêu", formatFullVnd(latest.spend)], ["Lô", latest.batch], ["Hạn dùng", formatDate(latest.expiry)]];
    appendFacts(document.querySelector("[data-latest-facts]"), latestFacts); setText("[data-evidence-boundary]", operating ? "Gold activity detail · chỉ đọc; ledger mutation chờ backend Phase 6." : "Gold procurement detail · chỉ đọc; không suy diễn giá trị tồn kho hay P&L.");
  }

  function renderComparison(state, data) {
    const operating = state.lens === "operating"; setText("[data-comparison-title]", operating ? "Chi phí vận hành theo nông trại" : "Chi tiêu mua hàng theo vật tư"); setText("[data-comparison-summary]", operating ? `${data.comparison.length} nông trại · sắp theo chi phí giảm dần` : `${data.comparison.length} vật tư · sắp theo chi tiêu giảm dần`); setText("[data-comparison-unit]", operating ? "VND · ha · kg" : "VND · đơn vị gốc");
    const head = document.querySelector("[data-comparison-head]"); const body = document.querySelector("[data-comparison-body]"); head?.replaceChildren(); body?.replaceChildren(); const headerRow = document.createElement("tr"); const headers = operating ? ["Nông trại", "Tỉnh", "Diện tích", "Chi phí", "Chênh lệch ngân sách", "Lợi nhuận", "Chi phí/ha", "Chi phí/kg"] : ["Vật tư", "Nhóm", "Giao dịch", "Số lượng", "Chi tiêu", "Tỷ trọng", "Đơn giá bình quân"];
    headers.forEach((label) => { const cell = appendCell(headerRow, "th", label, "col"); if (label === "Chi phí" || label === "Chi tiêu") cell.setAttribute("aria-sort", "descending"); }); head?.append(headerRow);
    data.comparison.forEach((item) => { const row = document.createElement("tr"); appendCell(row, "th", `${item.name} · ${item.code}`, "row"); if (operating) { appendCell(row, "td", item.province); appendCell(row, "td", `${formatNumber(item.area, 2)} ha`); appendCell(row, "td", formatFullVnd(item.cost)); appendCell(row, "td", formatVariance(item.variance)); appendCell(row, "td", `${formatFullVnd(item.profit)} · ${formatPercent(item.margin)}`); appendCell(row, "td", formatFullVnd(item.costHa)); appendCell(row, "td", formatFullVnd(item.costKg)); } else { appendCell(row, "td", item.category); appendCell(row, "td", formatNumber(item.transactions)); appendCell(row, "td", formatQuantity(item.quantity, item.unit)); appendCell(row, "td", formatFullVnd(item.spend)); appendCell(row, "td", formatPercent(item.share)); appendCell(row, "td", `${formatFullVnd(item.average)}/${unitLabels[item.unit] ?? item.unit}`); } body?.append(row); });
  }

  function renderExport(data) { const contract = data.export; setText("[data-export-lens]", contract.lens); setText("[data-export-scope]", contract.scope); setText("[data-export-cutoff]", formatDate(contract.cutoff)); setText("[data-export-contract]", `${contract.contract} · ${contract.columns} cột`); setText("[data-export-estimate]", `${formatNumber(contract.estimatedRows)} dòng dữ liệu`); }
  function render(state, onSelect) { const data = packageFor(state.lens); state.selectedId = normalizeSelection(state.lens, state.selectedId); renderIdentity(state, data); renderSummary(data); renderTrend(state, data); renderReconciliation(data); renderDrivers(state, data, onSelect); renderEvidence(state, data); renderComparison(state, data); renderExport(data); return state.selectedId; }

  window.AGRI_COST_VIEW = { fixture, lensValues, packageFor, normalizeSelection, render, renderEvidence, renderDrivers, renderExport, formatCompactVnd };
})();
