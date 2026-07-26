"use client";

import { useEffect, useState } from "react";

export function useLocalWorkTime() {
  const [value, setValue] = useState("");
  useEffect(() => {
    const frame = requestAnimationFrame(() => {
      setValue(toLocalDateTimeInput(new Date()));
    });
    return () => cancelAnimationFrame(frame);
  }, []);
  return [value, setValue] as const;
}

export function toLocalDateTimeInput(date: Date): string {
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}
