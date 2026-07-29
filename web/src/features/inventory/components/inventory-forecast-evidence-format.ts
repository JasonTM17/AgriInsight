import type {
  InventoryAnalyticsEnvelope,
  InventoryForecastCoverageStatus
} from "../inventory-analytics-contract-schema";
import {
  formatCompactNumber,
  formatDate,
  formatQuantity
} from "../inventory-format";

type ForecastItem = InventoryAnalyticsEnvelope["payload"]["items"][number];
export type ForecastEvidence = ForecastItem["forecast"];

export function formatOptionalQuantity(
  value: number | null,
  unit: string
): string {
  return value === null ? "Chưa có" : formatQuantity(value, unit);
}

export function formatForecastRange(
  forecast: ForecastEvidence,
  unit: string
): string {
  if (forecast.lowerQuantity === null || forecast.upperQuantity === null) {
    return forecast.lowerQuantity === null && forecast.upperQuantity === null
      ? "Chưa có"
      : "Dải dự báo chưa đầy đủ";
  }
  return `${formatQuantity(
    forecast.lowerQuantity,
    unit
  )} – ${formatQuantity(forecast.upperQuantity, unit)}`;
}

export function formatHistory(forecast: ForecastEvidence): string {
  if (forecast.historyStartDate === null || forecast.historyEndDate === null) {
    return "Chưa có phạm vi lịch sử";
  }
  const days =
    forecast.historyDays === null
      ? "Chưa có số ngày"
      : `${formatCompactNumber(forecast.historyDays)} ngày`;
  return `${formatDate(forecast.historyStartDate)} – ${formatDate(
    forecast.historyEndDate
  )} · ${days}`;
}

export function formatOptionalCount(
  value: number | null,
  unit: string
): string {
  return value === null ? "Chưa có" : `${formatCompactNumber(value)} ${unit}`;
}

export function formatBacktest(forecast: ForecastEvidence): string {
  const values = [
    forecast.backtestWindows === null
      ? null
      : `${formatCompactNumber(forecast.backtestWindows)} cửa sổ`,
    forecast.backtestMae === null
      ? null
      : `MAE ${formatCompactNumber(forecast.backtestMae)}`,
    forecast.backtestWapePct === null
      ? null
      : `WAPE ${formatCompactNumber(forecast.backtestWapePct)}%`
  ].filter((value): value is string => value !== null);
  return values.length === 0 ? "Chưa có" : values.join(" · ");
}

export function forecastStatusContent(
  coverageStatus: InventoryForecastCoverageStatus
): Readonly<{ label: string; description: string }> {
  if (coverageStatus === "ready") {
    return {
      label: "Sẵn sàng",
      description: "Máy chủ đã cung cấp bằng chứng dự báo."
    };
  }
  if (coverageStatus === "noDemand") {
    return {
      label: "Không có nhu cầu",
      description: "Máy chủ không ghi nhận nhu cầu trong lịch sử."
    };
  }
  if (coverageStatus === "insufficientHistory") {
    return {
      label: "Thiếu lịch sử",
      description: "Chưa đủ lịch sử để đưa dự báo; không tự nội suy."
    };
  }
  return {
    label: "Không có dự báo",
    description: "Máy chủ không cung cấp bằng chứng dự báo cho SKU-location này."
  };
}

export function forecastCoverageClass(
  coverageStatus: InventoryForecastCoverageStatus
):
  | "forecastReady"
  | "forecastNoDemand"
  | "forecastInsufficientHistory"
  | "forecastUnavailable" {
  if (coverageStatus === "ready") return "forecastReady";
  if (coverageStatus === "noDemand") return "forecastNoDemand";
  if (coverageStatus === "insufficientHistory") {
    return "forecastInsufficientHistory";
  }
  return "forecastUnavailable";
}
