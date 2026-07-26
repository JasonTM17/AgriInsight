import type {
  WorkActivity,
  WorkActivityLog
} from "./work-generated-client-adapter";

const STATUS_LABELS: Readonly<Record<WorkActivity["status"], string>> = {
  PLANNED: "Đã lên lịch",
  STARTED: "Đang thực hiện",
  COMPLETED: "Đã hoàn tất",
  CANCELLED: "Đã hủy"
};

const TYPE_LABELS: Readonly<Record<WorkActivity["activityType"], string>> = {
  PLANTING: "Gieo trồng",
  IRRIGATION: "Tưới tiêu",
  FERTILIZATION: "Bón phân",
  PEST_CONTROL: "Kiểm soát dịch hại",
  WEEDING: "Làm cỏ",
  PEST_INSPECTION: "Khảo sát dịch hại",
  HARVEST: "Thu hoạch",
  TRANSPORT: "Vận chuyển"
};

const UNIT_LABELS: Readonly<
  Record<NonNullable<WorkActivityLog["unit"]>, string>
> = {
  KG: "kg",
  TONNE: "tấn",
  LITRE: "lít",
  HOUR: "giờ",
  HECTARE: "ha",
  UNIT: "đơn vị"
};

const dateTime = new Intl.DateTimeFormat("vi-VN", {
  dateStyle: "short",
  timeStyle: "short",
  timeZone: "Asia/Ho_Chi_Minh"
});

export function workStatusLabel(status: WorkActivity["status"]): string {
  return STATUS_LABELS[status];
}

export function workTypeLabel(type: WorkActivity["activityType"]): string {
  return TYPE_LABELS[type];
}

export function formatWorkInstant(value: string): string {
  return dateTime.format(new Date(value));
}

export function formatWorkQuantity(log: WorkActivityLog): string | null {
  if (log.quantity === undefined || !log.unit) return null;
  return `${new Intl.NumberFormat("vi-VN", {
    maximumFractionDigits: 4
  }).format(log.quantity)} ${UNIT_LABELS[log.unit]}`;
}

export function shortWorkId(value: string): string {
  return value.slice(0, 8).toUpperCase();
}
