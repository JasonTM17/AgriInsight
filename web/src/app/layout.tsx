import type { Metadata } from "next";
import Link from "next/link";
import type { ReactNode } from "react";

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

export default function RootLayout({
  children
}: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="vi">
      <body>
        <a className="skip-link" href="#main-content">
          Bỏ qua đến nội dung chính
        </a>
        <header className="site-header">
          <Link className="brand" href="/">
            <span aria-hidden="true" className="brand-mark" />
            AgriInsight
          </Link>
          <span className="foundation-label">Secure web foundation</span>
        </header>
        <main id="main-content">{children}</main>
      </body>
    </html>
  );
}
