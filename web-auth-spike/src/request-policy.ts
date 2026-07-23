export const ALLOWED_RETURN_PATHS = new Set(["/", "/protected"]);

export function allowlistedReturnPath(candidate: string | null | undefined): string {
  if (!candidate) return "/protected";
  if (candidate.includes("\\") || candidate.startsWith("//")) return "/protected";
  try {
    const parsed = new URL(candidate, "http://localhost");
    if (parsed.origin !== "http://localhost" || !ALLOWED_RETURN_PATHS.has(parsed.pathname)) {
      return "/protected";
    }
    return `${parsed.pathname}${parsed.search}`;
  } catch {
    return "/protected";
  }
}
