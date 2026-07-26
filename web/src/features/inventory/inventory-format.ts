const numberFormatter = new Intl.NumberFormat("vi-VN", {
  maximumFractionDigits: 2
});
const currencyFormatter = new Intl.NumberFormat("vi-VN", {
  currency: "VND",
  maximumFractionDigits: 0,
  style: "currency"
});

export function formatQuantity(value: number, unit: string): string {
  return `${numberFormatter.format(value)} ${unit}`;
}

export function formatCurrency(value: number): string {
  return currencyFormatter.format(value);
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return "Chưa có";
  const parsed = new Date(value);
  return Number.isNaN(parsed.valueOf())
    ? value
    : new Intl.DateTimeFormat("vi-VN", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric"
      }).format(parsed);
}

export function formatDateTime(value: string): string {
  const parsed = new Date(value);
  return Number.isNaN(parsed.valueOf())
    ? value
    : new Intl.DateTimeFormat("vi-VN", {
        dateStyle: "short",
        timeStyle: "short"
      }).format(parsed);
}
