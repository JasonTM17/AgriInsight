import { Slot } from "@radix-ui/react-slot";
import type { ButtonHTMLAttributes, ReactNode } from "react";

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  asChild?: boolean;
  children: ReactNode;
  variant?: "primary" | "quiet" | "outline";
};

export function Button({
  asChild = false,
  children,
  className = "",
  variant = "primary",
  ...props
}: ButtonProps) {
  const Component = asChild ? Slot : "button";
  return (
    <Component
      className={`button button--${variant} ${className}`.trim()}
      {...props}
    >
      {children}
    </Component>
  );
}
