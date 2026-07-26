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

const FARM_DETAIL_RETURN_PATH =
  /^\/farms\/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function allowlistedReturnPath(candidate: string | null | undefined): string {
  if (!candidate || candidate.includes("\\") || candidate.startsWith("//")) {
    return "/overview";
  }
  try {
    const parsed = new URL(candidate, "https://return-path.invalid");
    if (
      parsed.origin !== "https://return-path.invalid" ||
      (!ALLOWED_RETURN_PATHS.has(parsed.pathname) &&
        !FARM_DETAIL_RETURN_PATH.test(parsed.pathname))
    ) {
      return "/overview";
    }
    return `${parsed.pathname}${parsed.search}`;
  } catch {
    return "/overview";
  }
}
