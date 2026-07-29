"use client";

import dynamic from "next/dynamic";
import { usePathname, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";

import { Icon } from "@/components/ui/icon";

import styles from "./realtime-alert-panel.module.css";

const PANEL_ID = "realtime-operational-alert-panel";
const RealtimeAlertPanel = dynamic(
  () => import("./realtime-alert-panel").then((module) => module.RealtimeAlertPanel),
  { ssr: false }
);

export function RealtimeAlertEntry({
  canAcknowledge
}: Readonly<{ canAcknowledge: boolean }>) {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const routeKey = `${pathname}?${searchParams.toString()}`;

  return (
    <RealtimeAlertEntryControl
      canAcknowledge={canAcknowledge}
      key={routeKey}
    />
  );
}

function RealtimeAlertEntryControl({
  canAcknowledge
}: Readonly<{ canAcknowledge: boolean }>) {
  const [open, setOpen] = useState(false);
  const entryRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);

  const closeAndRestoreFocus = useCallback(() => {
    setOpen(false);
    window.requestAnimationFrame(() => triggerRef.current?.focus());
  }, []);

  useEffect(() => {
    if (!open) return;

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      event.preventDefault();
      closeAndRestoreFocus();
    };
    const onPointerDown = (event: PointerEvent) => {
      const target = event.target;
      if (!(target instanceof Node) || entryRef.current?.contains(target)) return;

      setOpen(false);
      if (!isFocusableTarget(target)) {
        window.requestAnimationFrame(() => triggerRef.current?.focus());
      }
    };

    document.addEventListener("keydown", onKeyDown);
    document.addEventListener("pointerdown", onPointerDown, true);
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      document.removeEventListener("pointerdown", onPointerDown, true);
    };
  }, [closeAndRestoreFocus, open]);

  return (
    <div className={styles.entry} ref={entryRef}>
      <button
        ref={triggerRef}
        aria-controls={PANEL_ID}
        aria-expanded={open}
        aria-haspopup="dialog"
        aria-label={open ? "Đóng thông báo" : "Mở thông báo"}
        className="icon-button"
        onClick={() => setOpen((current) => !current)}
        type="button"
      >
        <Icon name="bell" size={20} />
      </button>
      {open ? (
        <RealtimeAlertPanel
          canAcknowledge={canAcknowledge}
          id={PANEL_ID}
          onClose={closeAndRestoreFocus}
        />
      ) : null}
    </div>
  );
}

function isFocusableTarget(target: Node): boolean {
  return target instanceof Element && Boolean(target.closest(
    'a[href], button, input, select, textarea, [tabindex]:not([tabindex="-1"])'
  ));
}
