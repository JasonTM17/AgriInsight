import type { SVGProps } from "react";

import type { NavigationIconName } from "@/lib/permission-navigation";

export type IconName =
  | NavigationIconName
  | "search"
  | "bell"
  | "menu"
  | "x"
  | "arrow-right"
  | "refresh"
  | "alert-triangle"
  | "alert-octagon";

const paths: Record<IconName, string> = {
  grid: "M4 4h6v6H4V4Zm10 0h6v6h-6V4ZM4 14h6v6H4v-6Zm10 0h6v6h-6v-6Z",
  farm: "M3 20h18M5 20V9l7-5 7 5v11M9 20v-5h6v5M8 9h.01M12 9h.01M16 9h.01",
  clipboard: "M9 5h6m-7 3h8m-8 4h8m-8 4h5M8 3h8a2 2 0 0 1 2 2v16H6V5a2 2 0 0 1 2-2Z",
  boxes: "m4 7 8-4 8 4-8 4-8-4Zm0 0v10l8 4 8-4V7M12 11v10M8 5l8 4",
  receipt: "M6 3h12v18l-3-2-3 2-3-2-3 2V3Zm3 5h6m-6 4h6m-6 4h4",
  sprout: "M12 21V11m0 0c-5 0-8-3-8-8 5 0 8 3 8 8Zm0 0c0-5 3-8 8-8 0 5-3 8-8 8Z",
  "shield-check": "M12 3 20 6v5c0 5-3.5 8.5-8 10-4.5-1.5-8-5-8-10V6l8-3Zm-4 9 2.5 2.5L16 9",
  "message-spark": "M5 5h10a4 4 0 0 1 4 4v3a4 4 0 0 1-4 4H9l-4 3v-3a3 3 0 0 1-2-3V7a2 2 0 0 1 2-2Zm8 3v4m-2-2h4",
  users: "M16 20v-1.5a4.5 4.5 0 0 0-9 0V20m4.5-7a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm5-5a2.5 2.5 0 0 1 2 4m1 8v-1.5a4 4 0 0 0-2.5-3.7",
  search: "m20 20-4.5-4.5M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z",
  bell: "M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9m-4 13h-2",
  menu: "M4 6h16M4 12h16M4 18h16",
  x: "m6 6 12 12M18 6 6 18",
  "arrow-right": "M4 12h16m-6-6 6 6-6 6",
  refresh: "M20 11a8 8 0 1 0 1 4m-1-10v6h-6",
  "alert-triangle": "m10.3 3.4-7.5 13.1A2 2 0 0 0 4.5 20h15a2 2 0 0 0 1.7-3.5L13.7 3.4a2 2 0 0 0-3.4 0ZM12 9v4m0 3h.01",
  "alert-octagon": "M7.3 3h9.4L21 7.3v9.4L16.7 21H7.3L3 16.7V7.3L7.3 3ZM12 8v5m0 3h.01"
};

export function Icon({
  name,
  size = 20,
  ...props
}: { name: IconName; size?: number } & Omit<SVGProps<SVGSVGElement>, "name">) {
  return (
    <svg
      aria-hidden="true"
      fill="none"
      height={size}
      viewBox="0 0 24 24"
      width={size}
      stroke="currentColor"
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="1.8"
      {...props}
    >
      <path d={paths[name]} />
    </svg>
  );
}
