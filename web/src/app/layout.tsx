import type { Metadata } from "next";
import { connection } from "next/server";
import type { ReactNode } from "react";

import { SkipLink } from "@/components/app-shell/skip-link";

import "./globals.css";

export const metadata: Metadata = {
  title: {
    default: "AgriInsight",
    template: "%s · AgriInsight"
  },
  description: "Nền tảng phân tích và vận hành dữ liệu nông nghiệp doanh nghiệp.",
  robots: {
    index: false,
    follow: false
  }
};

export default async function RootLayout({
  children
}: Readonly<{ children: ReactNode }>) {
  await connection();
  return (
    <html lang="vi">
      <body>
        <SkipLink />
        {children}
      </body>
    </html>
  );
}
