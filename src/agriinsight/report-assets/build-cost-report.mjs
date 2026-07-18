import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";


const SHEET_NAMES = [
  "Summary",
  "Monthly",
  "Cost Detail",
  "Procurement Detail",
  "Checks",
  "Metadata",
];

const COLORS = {
  darkGreen: "#184F36",
  green: "#2F7452",
  lightGreen: "#E7F1EB",
  gold: "#C89B32",
  lightGold: "#FBF4DE",
  ink: "#25332D",
  muted: "#60716A",
  line: "#D7E0DB",
  canvas: "#F7F9F8",
  white: "#FFFFFF",
  pass: "#247A4A",
  passFill: "#E2F2E8",
  fail: "#B3261E",
  failFill: "#FCE8E6",
};

const FORMULA_TEXT_PREFIX = /^[=+\-@]/;
const FORMULA_ERROR_PATTERN = /#REF!|#DIV\/0!|#VALUE!|#NAME\?|#N\/A/;
const DATE_ONLY_COLUMNS = new Set(["transaction_date", "expiry_date", "as_of_date"]);
const DATE_TIME_COLUMNS = new Set(["occurred_at"]);
const INTEGER_COLUMNS = new Set([
  "operating_activity_count",
  "procurement_transaction_count",
  "operating_detail_rows",
  "procurement_detail_rows",
  "filter_top_n",
]);


function escapeWorkbookText(value) {
  if (typeof value === "string" && FORMULA_TEXT_PREFIX.test(value)) {
    return `'${value}`;
  }
  return value;
}


function assertInsideWorkingDirectory(rawPath, label) {
  const workingDirectory = path.resolve(process.cwd());
  const resolved = path.resolve(rawPath);
  const relative = path.relative(workingDirectory, resolved);
  if (relative === "" || (!relative.startsWith("..") && !path.isAbsolute(relative))) {
    return resolved;
  }
  throw new Error(`${label} must stay inside the temporary working directory`);
}


function validateFrame(frame, label) {
  if (!frame || !Array.isArray(frame.columns) || !Array.isArray(frame.records)) {
    throw new Error(`${label} must contain columns and records arrays`);
  }
  if (!frame.columns.every((column) => typeof column === "string")) {
    throw new Error(`${label} contains an invalid column name`);
  }
  if (!frame.records.every((record) => record && typeof record === "object")) {
    throw new Error(`${label} contains an invalid record`);
  }
}


function validatePayload(payload) {
  if (!payload || payload.schemaVersion !== 1) {
    throw new Error("Unsupported cost report payload schema");
  }
  if (!payload.request || !payload.metadata || !payload.report) {
    throw new Error("Cost report payload is missing request, metadata, or report data");
  }
  for (const [name, frame] of Object.entries(payload.report)) {
    validateFrame(frame, `report.${name}`);
  }
  const requiredFrames = [
    "summary",
    "monthly",
    "costDetail",
    "procurementDetail",
    "checks",
    "metadata",
  ];
  for (const name of requiredFrames) {
    if (!(name in payload.report)) {
      throw new Error(`Cost report payload is missing report.${name}`);
    }
  }
}


function parseWorkbookDate(value, column) {
  if (value === null || value === "") {
    return null;
  }
  if (typeof value !== "string") {
    throw new Error(`${column} must be an ISO date string`);
  }
  const dateOnly = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (dateOnly) {
    return new Date(Date.UTC(
      Number(dateOnly[1]),
      Number(dateOnly[2]) - 1,
      Number(dateOnly[3]),
    ));
  }
  const normalized = value.includes("T") ? value : value.replace(" ", "T");
  const zoned = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(normalized)
    ? normalized
    : `${normalized}Z`;
  const parsed = new Date(zoned);
  if (Number.isNaN(parsed.getTime())) {
    throw new Error(`${column} contains an invalid ISO date: ${value}`);
  }
  return parsed;
}


function normalizeValue(value, column) {
  if (DATE_ONLY_COLUMNS.has(column) || DATE_TIME_COLUMNS.has(column)) {
    return parseWorkbookDate(value, column);
  }
  return escapeWorkbookText(value);
}


function frameMatrix(frame) {
  return frame.records.map((record) =>
    frame.columns.map((column) => normalizeValue(record[column] ?? null, column)),
  );
}


function columnLetter(index) {
  let value = index + 1;
  let result = "";
  while (value > 0) {
    const remainder = (value - 1) % 26;
    result = String.fromCharCode(65 + remainder) + result;
    value = Math.floor((value - 1) / 26);
  }
  return result;
}


function quotedSheetName(name) {
  return `'${name.replaceAll("'", "''")}'`;
}


function boundedReference(sheetName, columnIndex, startRow, endRow) {
  const letter = columnLetter(columnIndex);
  return `${quotedSheetName(sheetName)}!$${letter}$${startRow}:$${letter}$${endRow}`;
}


function singleCellReference(sheetName, columnIndex, row) {
  const letter = columnLetter(columnIndex);
  return `=${quotedSheetName(sheetName)}!$${letter}$${row}`;
}


function requireColumn(frame, column, label) {
  const index = frame.columns.indexOf(column);
  if (index < 0) {
    throw new Error(`${label} is missing required column: ${column}`);
  }
  return index;
}


function titleCaseHeader(column) {
  const suffix = column.endsWith("_vnd")
    ? " (VND)"
    : column.endsWith("_pct")
      ? " (%)"
      : "";
  const base = suffix ? column.slice(0, column.lastIndexOf("_")) : column;
  return base
    .split("_")
    .map((word) => {
      if (["id", "vnd", "kg"].includes(word)) {
        return word.toUpperCase();
      }
      return word.charAt(0).toUpperCase() + word.slice(1);
    })
    .join(" ") + suffix;
}


function numberFormatForColumn(column) {
  if (column.endsWith("_vnd") || column === "delta_vnd" || column === "tolerance_vnd") {
    return "#,##0;[Red](#,##0);-";
  }
  if (column.endsWith("_pct")) {
    return "0.0%";
  }
  if (INTEGER_COLUMNS.has(column) || column.endsWith("_count")) {
    return "#,##0";
  }
  if (
    column.includes("quantity")
    || column.includes("hours")
    || column.includes("rating")
  ) {
    return "#,##0.00";
  }
  if (DATE_TIME_COLUMNS.has(column)) {
    return "yyyy-mm-dd hh:mm";
  }
  if (DATE_ONLY_COLUMNS.has(column)) {
    return "yyyy-mm-dd";
  }
  return null;
}


function isNumericColumn(frame, column) {
  return frame.records.some((record) => typeof record[column] === "number");
}


function estimatedColumnWidth(frame, column) {
  if (DATE_TIME_COLUMNS.has(column)) {
    return 20;
  }
  if (DATE_ONLY_COLUMNS.has(column)) {
    return 13;
  }
  const headerLength = titleCaseHeader(column).length + 2;
  const sampleLength = frame.records.slice(0, 200).reduce((maximum, record) => {
    const value = record[column];
    if (value === null || value === undefined) {
      return maximum;
    }
    return Math.max(maximum, String(value).length + 2);
  }, 0);
  const preferred = Math.max(11, headerLength, sampleLength);
  return Math.min(preferred, column === "notes" ? 34 : 24);
}


function styleTitle(sheet, title, subtitle, lastColumn) {
  const titleRange = sheet.getRange(`A1:${lastColumn}1`);
  titleRange.merge();
  titleRange.values = [[title]];
  titleRange.format = {
    fill: COLORS.darkGreen,
    font: { bold: true, color: COLORS.white },
    horizontalAlignment: "left",
    verticalAlignment: "center",
  };
  titleRange.format.rowHeight = 30;

  const subtitleRange = sheet.getRange(`A2:${lastColumn}2`);
  subtitleRange.merge();
  subtitleRange.values = [[subtitle]];
  subtitleRange.format = {
    fill: COLORS.lightGreen,
    font: { color: COLORS.muted, italic: true },
    horizontalAlignment: "left",
    verticalAlignment: "center",
  };
  subtitleRange.format.rowHeight = 22;
}


function styleHeader(range) {
  range.format = {
    fill: COLORS.green,
    font: { bold: true, color: COLORS.white },
    horizontalAlignment: "center",
    verticalAlignment: "center",
    wrapText: true,
    borders: {
      bottom: { style: "medium", color: COLORS.gold },
    },
  };
  range.format.rowHeight = 30;
}


function writeFrameSheet(sheet, frame, options) {
  const {
    title,
    subtitle,
    freezeColumns = 1,
    typedMetadata = false,
  } = options;
  const columnCount = Math.max(frame.columns.length, 1);
  const lastColumn = columnLetter(Math.max(columnCount, 4) - 1);
  styleTitle(sheet, title, subtitle, lastColumn);
  sheet.showGridLines = false;

  const headerRange = sheet.getRangeByIndexes(3, 0, 1, columnCount);
  headerRange.values = [frame.columns.map((column) =>
    escapeWorkbookText(titleCaseHeader(column))
  )];
  styleHeader(headerRange);

  let matrix = frameMatrix(frame);
  if (typedMetadata) {
    matrix = frame.records.map((record) => {
      const item = String(record.item ?? "");
      let value = record.value ?? "";
      if (DATE_ONLY_COLUMNS.has(item) || DATE_TIME_COLUMNS.has(item)) {
        value = parseWorkbookDate(value, item);
      } else if (INTEGER_COLUMNS.has(item) && value !== "") {
        value = Number(value);
      } else {
        value = escapeWorkbookText(value);
      }
      return [escapeWorkbookText(item), value];
    });
  }

  if (matrix.length > 0) {
    const dataRange = sheet.getRangeByIndexes(4, 0, matrix.length, columnCount);
    dataRange.values = matrix;
    dataRange.format = {
      font: { color: COLORS.ink },
      verticalAlignment: "center",
      borders: {
        insideHorizontal: { style: "thin", color: COLORS.line },
      },
    };
    for (let index = 0; index < frame.columns.length; index += 1) {
      const column = frame.columns[index];
      const columnRange = sheet.getRangeByIndexes(4, index, matrix.length, 1);
      const numberFormat = numberFormatForColumn(column);
      if (numberFormat) {
        columnRange.format.numberFormat = numberFormat;
      }
      columnRange.format.horizontalAlignment = isNumericColumn(frame, column)
        ? "right"
        : "left";
      if (column === "notes") {
        columnRange.format.wrapText = true;
      }
    }
    if (typedMetadata) {
      frame.records.forEach((record, index) => {
        const item = String(record.item ?? "");
        const valueCell = sheet.getRangeByIndexes(4 + index, 1, 1, 1);
        if (DATE_ONLY_COLUMNS.has(item)) {
          valueCell.format.numberFormat = "yyyy-mm-dd";
        } else if (DATE_TIME_COLUMNS.has(item)) {
          valueCell.format.numberFormat = "yyyy-mm-dd hh:mm";
        }
      });
    }
  } else {
    const emptyNote = sheet.getRange(`A3:${lastColumn}3`);
    emptyNote.merge();
    emptyNote.values = [["No rows for the selected report scope."]];
    emptyNote.format = {
      fill: COLORS.canvas,
      font: { color: COLORS.muted, italic: true },
    };
  }

  for (let index = 0; index < frame.columns.length; index += 1) {
    const widthRange = sheet.getRangeByIndexes(
      0,
      index,
      Math.max(matrix.length + 4, 5),
      1,
    );
    widthRange.format.columnWidth = estimatedColumnWidth(frame, frame.columns[index]);
  }
  sheet.freezePanes.freezeRows(4);
  if (freezeColumns > 0) {
    sheet.freezePanes.freezeColumns(freezeColumns);
  }
}


function addKpiCard(sheet, startColumn, startRow, label, formula, numberFormat, accent) {
  const labelRange = sheet.getRangeByIndexes(startRow - 1, startColumn, 1, 3);
  labelRange.merge();
  labelRange.values = [[label]];
  labelRange.format = {
    fill: accent,
    font: { bold: true, color: COLORS.white },
    horizontalAlignment: "left",
    verticalAlignment: "center",
  };

  const valueRange = sheet.getRangeByIndexes(startRow, startColumn, 2, 3);
  valueRange.merge();
  valueRange.formulas = [[formula]];
  valueRange.format = {
    fill: COLORS.white,
    font: { bold: true, color: COLORS.ink },
    horizontalAlignment: "right",
    verticalAlignment: "center",
    numberFormat,
    borders: { preset: "outside", style: "thin", color: COLORS.line },
  };
}


function writeSourceSummaryControls(sheet, summaryFrame) {
  const columns = [
    "operating_activity_count",
    "operating_material_cost_vnd",
    "operating_labor_cost_vnd",
    "operating_total_cost_vnd",
    "procurement_transaction_count",
    "procurement_quantity_base_unit",
    "procurement_spend_vnd",
  ];
  if (summaryFrame.records.length !== 1) {
    throw new Error("Summary source data must contain exactly one row");
  }
  columns.forEach((column) => requireColumn(summaryFrame, column, "Summary"));
  const source = summaryFrame.records[0];
  const values = columns.map((column) => {
    const value = source[column];
    if (typeof value !== "number" || !Number.isFinite(value)) {
      throw new Error(`Summary source contains an invalid number: ${column}`);
    }
    return value;
  });

  sheet.getRange("N1:T1").values = [[
    "Source activity count",
    "Source material cost",
    "Source labor cost",
    "Source operating cost",
    "Source transaction count",
    "Source procurement quantity",
    "Source procurement spend",
  ]];
  styleHeader(sheet.getRange("N1:T1"));
  sheet.getRange("N2:T2").values = [values];
  sheet.getRange("N2:T2").format = {
    fill: COLORS.canvas,
    font: { color: COLORS.muted },
    horizontalAlignment: "right",
    numberFormat: "#,##0.00;[Red](#,##0.00);-",
  };
  for (let index = 13; index <= 19; index += 1) {
    sheet.getRangeByIndexes(0, index, 2, 1).format.columnWidth = 18;
  }
  return {
    operatingMaterial: "$O$2",
    operatingLabor: "$P$2",
    operatingTotal: "$Q$2",
    procurementSpend: "$T$2",
  };
}


function writeSummary(sheet, payload, frames) {
  const { summary, monthly, costDetail, procurementDetail } = frames;
  const costEndRow = Math.max(5, costDetail.records.length + 4);
  const procurementEndRow = Math.max(5, procurementDetail.records.length + 4);
  const costId = requireColumn(costDetail, "activity_id", "Cost Detail");
  const materialCost = requireColumn(
    costDetail,
    "operating_material_cost_vnd",
    "Cost Detail",
  );
  const operatingCost = requireColumn(
    costDetail,
    "operating_total_cost_vnd",
    "Cost Detail",
  );
  const procurementId = requireColumn(
    procurementDetail,
    "transaction_id",
    "Procurement Detail",
  );
  const procurementQuantity = requireColumn(
    procurementDetail,
    "procurement_quantity_base_unit",
    "Procurement Detail",
  );
  const procurementSpend = requireColumn(
    procurementDetail,
    "procurement_spend_vnd",
    "Procurement Detail",
  );

  styleTitle(
    sheet,
    "Cost Analysis Report",
    "Decision-ready operating cost and procurement view | Values in VND unless noted",
    "M",
  );
  sheet.showGridLines = false;
  sheet.getRange("A3:M3").format = {
    fill: COLORS.canvas,
    font: { color: COLORS.ink },
    verticalAlignment: "center",
  };
  sheet.getRange("A3").values = [["As of"]];
  sheet.getRange("B3:C3").merge();
  sheet.getRange("B3:C3").values = [[
    parseWorkbookDate(payload.metadata.as_of_date, "as_of_date"),
  ]];
  sheet.getRange("E3").values = [["Scope"]];
  sheet.getRange("F3:G3").merge();
  sheet.getRange("F3:G3").values = [[escapeWorkbookText(payload.request.scope)]];
  sheet.getRange("I3").values = [["Run ID"]];
  sheet.getRange("J3:M3").merge();
  sheet.getRange("J3:M3").values = [[escapeWorkbookText(payload.metadata.run_id)]];
  sheet.getRange("A3").format.font = { bold: true, color: COLORS.darkGreen };
  sheet.getRange("E3").format.font = { bold: true, color: COLORS.darkGreen };
  sheet.getRange("I3").format.font = { bold: true, color: COLORS.darkGreen };
  sheet.getRange("B3:C3").format.numberFormat = "yyyy-mm-dd";
  const sourceCells = writeSourceSummaryControls(sheet, summary);

  addKpiCard(
    sheet,
    0,
    5,
    "Operating activities",
    `=COUNTA(${boundedReference("Cost Detail", costId, 5, costEndRow)})`,
    "#,##0",
    COLORS.green,
  );
  addKpiCard(
    sheet,
    4,
    5,
    "Operating material cost",
    `=SUM(${boundedReference("Cost Detail", materialCost, 5, costEndRow)})`,
    "#,##0;[Red](#,##0);-",
    COLORS.gold,
  );
  addKpiCard(
    sheet,
    8,
    5,
    "Operating total cost",
    `=SUM(${boundedReference("Cost Detail", operatingCost, 5, costEndRow)})`,
    "#,##0;[Red](#,##0);-",
    COLORS.green,
  );
  addKpiCard(
    sheet,
    0,
    9,
    "Procurement transactions",
    `=COUNTA(${boundedReference(
      "Procurement Detail",
      procurementId,
      5,
      procurementEndRow,
    )})`,
    "#,##0",
    COLORS.gold,
  );
  addKpiCard(
    sheet,
    4,
    9,
    "Procurement quantity",
    `=SUM(${boundedReference(
      "Procurement Detail",
      procurementQuantity,
      5,
      procurementEndRow,
    )})`,
    "#,##0.00",
    COLORS.green,
  );
  addKpiCard(
    sheet,
    8,
    9,
    "Procurement spend",
    `=SUM(${boundedReference(
      "Procurement Detail",
      procurementSpend,
      5,
      procurementEndRow,
    )})`,
    "#,##0;[Red](#,##0);-",
    COLORS.gold,
  );

  const helperTitle = sheet.getRange("A14:C14");
  helperTitle.merge();
  helperTitle.values = [["Monthly cost trend"]];
  helperTitle.format = {
    fill: COLORS.darkGreen,
    font: { bold: true, color: COLORS.white },
  };
  sheet.getRange("A15:C15").values = [[
    "Month",
    "Operating cost (VND)",
    "Procurement spend (VND)",
  ]];
  styleHeader(sheet.getRange("A15:C15"));

  const monthColumn = requireColumn(monthly, "month", "Monthly");
  const monthlyOperating = requireColumn(
    monthly,
    "operating_total_cost_vnd",
    "Monthly",
  );
  const monthlyProcurement = requireColumn(
    monthly,
    "procurement_spend_vnd",
    "Monthly",
  );
  const helperFormulas = monthly.records.map((_, index) => {
    const sourceRow = index + 5;
    return [
      singleCellReference("Monthly", monthColumn, sourceRow),
      singleCellReference("Monthly", monthlyOperating, sourceRow),
      singleCellReference("Monthly", monthlyProcurement, sourceRow),
    ];
  });
  if (helperFormulas.length === 0) {
    throw new Error("Monthly helper range cannot be empty");
  }
  const helperData = sheet.getRangeByIndexes(15, 0, helperFormulas.length, 3);
  helperData.formulas = helperFormulas;
  helperData.format.borders = {
    insideHorizontal: { style: "thin", color: COLORS.line },
  };
  sheet.getRange(`B16:C${helperFormulas.length + 15}`).format.numberFormat =
    "#,##0;[Red](#,##0);-";

  const chartRange = sheet.getRange(`A15:C${helperFormulas.length + 15}`);
  const chart = sheet.charts.add("line", chartRange);
  chart.title = "Monthly Operating Cost and Procurement Spend (VND)";
  chart.titleTextStyle.fontSize = 12;
  chart.hasLegend = true;
  chart.xAxis = { axisType: "textAxis", textStyle: { fontSize: 9 } };
  chart.yAxis = { numberFormatCode: "#,##0", textStyle: { fontSize: 9 } };
  const chartSeries = chart.series.items;
  if (chartSeries[0]) {
    chartSeries[0].fill = COLORS.green;
  }
  if (chartSeries[1]) {
    chartSeries[1].fill = COLORS.gold;
  }
  chart.setPosition("E14", "M30");

  const summaryUsedRows = Math.max(30, helperFormulas.length + 15);
  for (const index of [0, 1, 2]) {
    sheet.getRangeByIndexes(0, index, summaryUsedRows, 1).format.columnWidth =
      index === 0 ? 17 : 19;
  }
  for (const index of [4, 5, 6, 8, 9, 10]) {
    sheet.getRangeByIndexes(0, index, summaryUsedRows, 1).format.columnWidth = 17;
  }
  sheet.getRangeByIndexes(0, 3, summaryUsedRows, 1).format.columnWidth = 3;
  sheet.getRangeByIndexes(0, 7, summaryUsedRows, 1).format.columnWidth = 3;
  sheet.freezePanes.freezeRows(3);

  return {
    costEndRow,
    procurementEndRow,
    operatingCost,
    procurementSpend,
    sourceCells,
  };
}


function writeChecks(sheet, checksFrame, summaryReferences, frames) {
  if (checksFrame.records.length !== 3) {
    throw new Error("Checks data must contain exactly three required controls");
  }
  const { costDetail, procurementDetail } = frames;
  const detailTotalReference = boundedReference(
    "Cost Detail",
    summaryReferences.operatingCost,
    5,
    summaryReferences.costEndRow,
  );
  const procurementReference = boundedReference(
    "Procurement Detail",
    summaryReferences.procurementSpend,
    5,
    summaryReferences.procurementEndRow,
  );
  const sourceSheet = quotedSheetName("Summary");

  styleTitle(
    sheet,
    "Model Checks",
    "All controls must pass before the workbook is used for management decisions.",
    "F",
  );
  sheet.showGridLines = false;
  const modelStatus = sheet.getRange("A3:F3");
  modelStatus.merge();
  modelStatus.formulas = [[
    '=IF(COUNTIF($D$6:$D$8,"FAIL")=0,"MODEL STATUS: PASS","MODEL STATUS: FAIL")',
  ]];
  modelStatus.format = {
    fill: COLORS.passFill,
    font: { bold: true, color: COLORS.pass },
    horizontalAlignment: "center",
    verticalAlignment: "center",
    borders: { preset: "outside", style: "medium", color: COLORS.pass },
  };
  modelStatus.format.rowHeight = 28;
  modelStatus.conditionalFormats.add("containsText", {
    text: "FAIL",
    format: { fill: COLORS.failFill, font: { bold: true, color: COLORS.fail } },
  });

  sheet.getRange("A5:F5").values = [[
    "Check",
    "Delta (VND)",
    "Tolerance (VND)",
    "Status",
    "Where to fix",
    "Notes",
  ]];
  styleHeader(sheet.getRange("A5:F5"));

  const deltaFormulas = [
    `=${sourceSheet}!${summaryReferences.sourceCells.operatingTotal}`
      + `-${sourceSheet}!${summaryReferences.sourceCells.operatingMaterial}`
      + `-${sourceSheet}!${summaryReferences.sourceCells.operatingLabor}`,
    `=${sourceSheet}!${summaryReferences.sourceCells.operatingTotal}`
      + `-SUM(${detailTotalReference})`,
    `=${sourceSheet}!${summaryReferences.sourceCells.procurementSpend}`
      + `-SUM(${procurementReference})`,
  ];
  checksFrame.records.forEach((record, index) => {
    const row = index + 6;
    sheet.getRange(`A${row}:F${row}`).values = [[
      escapeWorkbookText(record.check_name ?? ""),
      null,
      Number(record.tolerance_vnd),
      null,
      escapeWorkbookText(record.where_to_fix ?? ""),
      escapeWorkbookText(record.notes ?? ""),
    ]];
    sheet.getRange(`B${row}`).formulas = [[deltaFormulas[index]]];
    sheet.getRange(`D${row}`).formulas = [[
      `=IF(ABS(B${row})<=C${row},"PASS","FAIL")`,
    ]];
  });
  sheet.getRange("A6:F8").format = {
    font: { color: COLORS.ink },
    verticalAlignment: "center",
    borders: {
      insideHorizontal: { style: "thin", color: COLORS.line },
    },
  };
  sheet.getRange("B6:C8").format.numberFormat = "#,##0.00;[Red](#,##0.00);-";
  sheet.getRange("D6:D8").format.horizontalAlignment = "center";
  sheet.getRange("D6:D8").conditionalFormats.add("containsText", {
    text: "PASS",
    format: { fill: COLORS.passFill, font: { bold: true, color: COLORS.pass } },
  });
  sheet.getRange("D6:D8").conditionalFormats.add("containsText", {
    text: "FAIL",
    format: { fill: COLORS.failFill, font: { bold: true, color: COLORS.fail } },
  });
  sheet.getRange("F6:F8").format.wrapText = true;

  const widths = [28, 17, 18, 12, 19, 38];
  widths.forEach((width, index) => {
    sheet.getRangeByIndexes(0, index, 8, 1).format.columnWidth = width;
  });
  sheet.freezePanes.freezeRows(5);
}


function metadataFrameForWorkbook(frame) {
  if (frame.columns.length !== 2 || !frame.columns.includes("item") || !frame.columns.includes("value")) {
    throw new Error("Metadata frame must contain item and value columns");
  }
  return frame;
}


function countFormulaErrorMatches(ndjson) {
  return String(ndjson)
    .split(/\r?\n/)
    .filter(Boolean)
    .filter((line) => {
      try {
        const record = JSON.parse(line);
        if (record.kind === "summary" || record.type === "summary") {
          return false;
        }
      } catch {
        // A non-JSON result line is still checked for an actual formula error token.
      }
      return FORMULA_ERROR_PATTERN.test(line);
    }).length;
}


function verifySheetOrder(sheetInspection) {
  const text = String(sheetInspection.ndjson);
  let cursor = -1;
  for (const name of SHEET_NAMES) {
    const compact = `"name":"${name}"`;
    const spaced = `"name": "${name}"`;
    const compactIndex = text.indexOf(compact, cursor + 1);
    const spacedIndex = text.indexOf(spaced, cursor + 1);
    const nextIndex = [compactIndex, spacedIndex]
      .filter((index) => index >= 0)
      .sort((left, right) => left - right)[0];
    if (nextIndex === undefined) {
      throw new Error(`Workbook sheet verification failed at ${name}`);
    }
    cursor = nextIndex;
  }
}


function previewRange(frame, maxRow) {
  const lastColumn = columnLetter(Math.max(frame.columns.length, 4) - 1);
  const lastRow = Math.min(Math.max(frame.records.length + 4, 5), maxRow);
  return `A1:${lastColumn}${lastRow}`;
}


async function inspectAndRender(workbook, previewDirectory, summaryEndRow, frames) {
  const sheetInspection = await workbook.inspect({
    kind: "sheet",
    include: "id,name",
    maxChars: 4_000,
  });
  verifySheetOrder(sheetInspection);

  const summaryInspection = await workbook.inspect({
    kind: "table",
    range: `Summary!A1:M${Math.max(30, summaryEndRow)}`,
    include: "values",
    tableMaxRows: Math.max(30, summaryEndRow),
    tableMaxCols: 13,
    maxChars: 16_000,
  });
  if (!String(summaryInspection.ndjson).includes("Cost Analysis Report")) {
    throw new Error("Summary value inspection did not find the report title");
  }

  const formulaInspection = await workbook.inspect({
    kind: "match",
    searchTerm: "SUM\\(|IF\\(|'Monthly'!",
    options: {
      useRegex: true,
      matchFormulas: true,
      maxResults: 500,
    },
    summary: "bounded workbook formula inspection",
    maxChars: 32_000,
  });
  const formulaInspectionText = String(formulaInspection.ndjson);
  const requiredFormulaEvidence = ["Summary", "Checks", "SUM(", "IF(", "'Monthly'!"];
  const missingFormulaEvidence = requiredFormulaEvidence.filter(
    (value) => !formulaInspectionText.includes(value),
  );
  if (missingFormulaEvidence.length > 0) {
    throw new Error(
      `Workbook formula inspection is missing: ${missingFormulaEvidence.join(", ")}`,
    );
  }

  const checksInspection = await workbook.inspect({
    kind: "table",
    range: "Checks!A1:F8",
    include: "values",
    tableMaxRows: 8,
    tableMaxCols: 6,
    maxChars: 8_000,
  });
  const checksText = String(checksInspection.ndjson);
  if (!checksText.includes("MODEL STATUS: PASS")) {
    throw new Error("Workbook model status did not evaluate to MODEL STATUS: PASS");
  }

  const formulaErrors = await workbook.inspect({
    kind: "match",
    searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
    options: { useRegex: true, maxResults: 300 },
    summary: "final formula error scan",
    maxChars: 12_000,
  });
  const formulaErrorMatches = countFormulaErrorMatches(formulaErrors.ndjson);
  if (formulaErrorMatches > 0) {
    throw new Error(`Workbook contains ${formulaErrorMatches} formula error match(es)`);
  }

  await fs.mkdir(previewDirectory, { recursive: true });
  const previewRanges = {
    Summary: `A1:M${Math.max(30, summaryEndRow)}`,
    Monthly: previewRange(frames.monthly, 40),
    "Cost Detail": previewRange(frames.costDetail, 35),
    "Procurement Detail": previewRange(frames.procurementDetail, 35),
    Checks: "A1:F8",
    Metadata: previewRange(frames.metadata, 30),
  };
  let previewCount = 0;
  for (const sheetName of SHEET_NAMES) {
    const preview = await workbook.render({
      sheetName,
      range: previewRanges[sheetName],
      scale: 1,
      format: "png",
    });
    const bytes = new Uint8Array(await preview.arrayBuffer());
    if (bytes.length === 0) {
      throw new Error(`Rendered preview is empty for ${sheetName}`);
    }
    const previewName = `${sheetName.toLowerCase().replaceAll(" ", "-")}.png`;
    await fs.writeFile(path.join(previewDirectory, previewName), bytes);
    previewCount += 1;
  }
  return { formulaErrorMatches, previewCount };
}


async function buildWorkbook(payload, Workbook) {
  const workbook = Workbook.create();
  const sheets = Object.fromEntries(
    SHEET_NAMES.map((name) => [name, workbook.worksheets.add(name)]),
  );
  const frames = payload.report;

  writeFrameSheet(sheets.Monthly, frames.monthly, {
    title: "Monthly Cost View",
    subtitle: `${frames.monthly.records.length} reporting period(s) | Operating and procurement lenses remain separate`,
    freezeColumns: 1,
  });
  writeFrameSheet(sheets["Cost Detail"], frames.costDetail, {
    title: "Operating Cost Detail",
    subtitle: `${frames.costDetail.records.length} filtered activity row(s) | Source: cost_activity_detail Gold contract`,
    freezeColumns: 3,
  });
  writeFrameSheet(sheets["Procurement Detail"], frames.procurementDetail, {
    title: "Procurement Detail",
    subtitle: `${frames.procurementDetail.records.length} filtered inbound transaction row(s) | Non-P&L procurement lens`,
    freezeColumns: 3,
  });
  writeFrameSheet(sheets.Metadata, metadataFrameForWorkbook(frames.metadata), {
    title: "Report Metadata",
    subtitle: "Version, lineage, normalized filters, and row-count controls",
    freezeColumns: 1,
    typedMetadata: true,
  });

  const summaryReferences = writeSummary(sheets.Summary, payload, frames);
  writeChecks(sheets.Checks, frames.checks, summaryReferences, frames);
  return workbook;
}


async function main() {
  const [payloadArgument, outputArgument, previewArgument, ...extraArguments] =
    process.argv.slice(2);
  if (!payloadArgument || !outputArgument || !previewArgument || extraArguments.length > 0) {
    throw new Error("Expected payload, XLSX output, and preview directory arguments");
  }
  const payloadPath = assertInsideWorkingDirectory(payloadArgument, "Payload path");
  const outputPath = assertInsideWorkingDirectory(outputArgument, "XLSX output path");
  const previewDirectory = assertInsideWorkingDirectory(
    previewArgument,
    "Preview directory",
  );

  const requireFromWorkingDirectory = createRequire(
    path.join(process.cwd(), "artifact-tool-loader.cjs"),
  );
  const { SpreadsheetFile, Workbook } = requireFromWorkingDirectory("@oai/artifact-tool");
  const payload = JSON.parse(await fs.readFile(payloadPath, "utf8"));
  validatePayload(payload);

  const workbook = await buildWorkbook(payload, Workbook);
  const summaryEndRow = payload.report.monthly.records.length + 15;
  const qa = await inspectAndRender(
    workbook,
    previewDirectory,
    summaryEndRow,
    payload.report,
  );

  const exported = await SpreadsheetFile.exportXlsx(workbook);
  await exported.save(outputPath);
  const outputStats = await fs.stat(outputPath);
  if (!outputStats.isFile() || outputStats.size === 0) {
    throw new Error("artifact-tool exported an empty XLSX file");
  }
  process.stdout.write(JSON.stringify({
    ok: true,
    sheets: SHEET_NAMES,
    modelStatus: "PASS",
    formulaErrorMatches: qa.formulaErrorMatches,
    previews: qa.previewCount,
    outputBytes: outputStats.size,
  }));
}


main().catch((error) => {
  const message = error instanceof Error ? error.message : String(error);
  process.stderr.write(JSON.stringify({ ok: false, error: message.slice(0, 2_000) }));
  process.exitCode = 1;
});
