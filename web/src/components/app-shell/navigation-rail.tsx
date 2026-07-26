"use client";

import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";

import { getActiveNavigationKey, type NavigationItem } from "@/lib/permission-navigation";

import { Icon } from "@/components/ui/icon";

export function NavigationRail({
  items,
  tenantCode
}: {
  items: readonly NavigationItem[];
  tenantCode: string;
}) {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const [open, setOpen] = useState(false);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const railRef = useRef<HTMLElement>(null);
  const activeKey = getActiveNavigationKey(pathname, searchParams);

  useEffect(() => {
    const rail = railRef.current;
    const workspace = document.querySelector<HTMLElement>("[data-workspace]");
    if (!rail || !workspace) return;
    const mediaQuery = window.matchMedia("(max-width: 47.999rem)");
    const updateMobileState = () => {
      rail.toggleAttribute("inert", mediaQuery.matches && !open);
      workspace.toggleAttribute("inert", mediaQuery.matches && open);
    };
    updateMobileState();
    mediaQuery.addEventListener("change", updateMobileState);
    return () => {
      mediaQuery.removeEventListener("change", updateMobileState);
      rail.removeAttribute("inert");
      workspace.removeAttribute("inert");
    };
  }, [open]);

  useEffect(() => {
    if (open) {
      railRef.current?.querySelector<HTMLElement>("a, button")?.focus();
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (!open) return;
      if (event.key === "Escape") {
        setOpen(false);
        menuButtonRef.current?.focus();
        return;
      }
      if (event.key !== "Tab") return;
      const focusable = Array.from(
        railRef.current?.querySelectorAll<HTMLElement>("a, button, input") ?? []
      ).filter((element) => !element.hasAttribute("disabled"));
      if (focusable.length === 0) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [open]);

  return (
    <>
      <button
        ref={menuButtonRef}
        aria-controls="primary-navigation"
        aria-expanded={open}
        aria-label={open ? "Đóng điều hướng" : "Mở điều hướng"}
        className="mobile-menu-button"
        onClick={() => setOpen((value) => !value)}
        type="button"
      >
        <Icon name={open ? "x" : "menu"} size={22} />
      </button>
      <aside
        ref={railRef}
        aria-label="Điều hướng chính"
        className={`navigation-rail${open ? " navigation-rail--open" : ""}`}
        id="primary-navigation"
      >
        <div className="navigation-rail__brand">
          <Link className="brand" href="/protected" onClick={() => setOpen(false)}>
            <span aria-hidden="true" className="brand-mark" />
            <span>AgriInsight</span>
          </Link>
          <span className="navigation-rail__descriptor">Vận hành nông nghiệp</span>
        </div>
        <div className="navigation-rail__scope">
          <span>Tenant hiện hành</span>
          <strong translate="no">{tenantCode}</strong>
        </div>
        <nav aria-label="Khu vực sản phẩm">
          <p className="navigation-rail__label">Khu vực</p>
          <ul className="navigation-list">
            {items.map((item) => {
              const active = item.key === activeKey;
              return (
                <li key={item.key}>
                  <Link
                    aria-current={active ? "page" : undefined}
                    className={`navigation-link${active ? " navigation-link--active" : ""}`}
                    href={item.href}
                    onClick={() => setOpen(false)}
                    title={item.description}
                  >
                    <Icon name={item.icon} size={19} />
                    <span>{item.label}</span>
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>
        <div className="navigation-rail__footer">
          <p>Phiên làm việc được xác minh từ máy chủ.</p>
          <Link href="/login">Đăng xuất</Link>
        </div>
      </aside>
    </>
  );
}
