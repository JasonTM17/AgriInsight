const ALLOWED_RETURN_PATHS = new Set([
  "/",
  "/protected",
  "/overview",
  "/farms",
  "/work",
  "/inventory",
  "/costs",
  "/crop-health",
  "/data-quality",
  "/administration"
]);

export function allowlistedReturnPath(candidate: string | null | undefined): string {
  if (!candidate || candidate.includes("\\") || candidate.startsWith("//")) {
    return "/protected";
  }
  try {
    const parsed = new URL(candidate, "https://return-path.invalid");
    if (
      parsed.origin !== "https://return-path.invalid" ||
      !ALLOWED_RETURN_PATHS.has(parsed.pathname)
    ) {
      return "/protected";
    }
    return `${parsed.pathname}${parsed.search}`;
  } catch {
    return "/protected";
  }
}
