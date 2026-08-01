import type {
  YieldForecastEnvelope,
  YieldForecastItem
} from "@/features/farms/yield-forecast-contract-schema";

const number = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 });
const date = new Intl.DateTimeFormat("vi-VN", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  timeZone: "UTC"
});
const dateTime = new Intl.DateTimeFormat("vi-VN", {
  dateStyle: "short",
  timeStyle: "short",
  timeZone: "UTC"
});
const naiveDateTimePattern = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?$/;

export function formatForecastPoint(item: YieldForecastItem): string {
  if (item.forecastQuantityKg === null || item.forecastYieldKgPerHa === null) {
    return "Chưa có";
  }
  return `${formatQuantity(item.forecastQuantityKg, "kg")} · ${formatQuantity(item.forecastYieldKgPerHa, "kg/ha")}`;
}

export function formatObservedSpan(item: YieldForecastItem): string {
  if (
    item.observedMinQuantityKg === null ||
    item.observedMaxQuantityKg === null ||
    item.observedMinYieldKgPerHa === null ||
    item.observedMaxYieldKgPerHa === null
  ) {
    return "Chưa có";
  }
  return `${formatQuantity(item.observedMinQuantityKg, "kg")} – ${formatQuantity(item.observedMaxQuantityKg, "kg")} · ${formatQuantity(item.observedMinYieldKgPerHa, "kg/ha")} – ${formatQuantity(item.observedMaxYieldKgPerHa, "kg/ha")}`;
}

export function formatOptionalQuantity(value: number | null, unit: string): string {
  return value === null ? "Chưa có" : formatQuantity(value, unit);
}

export function formatQuantity(value: number, unit: string): string {
  return `${number.format(value)} ${unit}`;
}

export function formatDate(value: string): string {
  const parsed = new Date(`${value}T00:00:00Z`);
  return Number.isNaN(parsed.valueOf()) ? value : date.format(parsed);
}

export function formatDateTime(value: string | null): string {
  if (value === null) return "Chưa có";
  const naiveMatch = naiveDateTimePattern.exec(value);
  if (naiveMatch) {
    const [, year, month, day, hour, minute] = naiveMatch;
    return `${hour}:${minute} ${day}/${month}/${year}`;
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.valueOf()) ? value : dateTime.format(parsed);
}

export function formatHistory(item: YieldForecastItem): string {
  if (item.historyStartAt === null || item.historyEndAt === null) {
    return "Chưa có phạm vi lịch sử";
  }
  return `${formatDateTime(item.historyStartAt)} – ${formatDateTime(item.historyEndAt)}`;
}

export function formatBacktest(item: YieldForecastItem): string {
  const evaluated = `${number.format(item.backtestOrigins)} mốc, ${number.format(item.backtestSeasons)} mùa`;
  if (item.backtestMaeKgPerHa === null || item.backtestWapePct === null) {
    return `${evaluated} · Chưa có MAE/WAPE`;
  }
  return `${evaluated} · MAE ${formatQuantity(item.backtestMaeKgPerHa, "kg/ha")} · WAPE ${number.format(item.backtestWapePct)}%`;
}

export function formatDataStatus(
  value: YieldForecastEnvelope["freshness"]["dataStatus"]
): string {
  if (value === "current") return "Hiện hành";
  if (value === "stale") return "Đã cũ";
  if (value === "partial") return "Một phần";
  return "Thiếu dữ liệu";
}

export { number };
