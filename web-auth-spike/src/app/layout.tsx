import type { ReactNode } from "react";

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="en">
      <body style={{ fontFamily: "system-ui, sans-serif", margin: "2rem", maxWidth: "52rem" }}>
        <header>
          <strong>AgriInsight OIDC session spike</strong>
        </header>
        <main>{children}</main>
      </body>
    </html>
  );
}
