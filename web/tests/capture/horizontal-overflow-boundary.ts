export type HorizontalBoundary = Readonly<{
  explicitlyAllowsNonInteractiveClip: boolean;
  isBounded: boolean;
  left: number;
  overflowX: string;
  right: number;
}>;

export type HorizontalOverflowCandidate = Readonly<{
  boundaries: readonly HorizontalBoundary[];
  containsInteractiveContent: boolean;
  offender: Readonly<{
    className: string;
    rect: Readonly<{
      bottom: number;
      height: number;
      left: number;
      right: number;
      top: number;
      width: number;
      x: number;
      y: number;
    }>;
    tagName: string;
  }>;
}>;

export type HorizontalOverflowSnapshot = Readonly<{
  bodyClientWidth: number;
  bodyScrollWidth: number;
  candidates: readonly HorizontalOverflowCandidate[];
  clientWidth: number;
  scrollWidth: number;
}>;

export function collectHorizontalOverflowSnapshot(): HorizontalOverflowSnapshot {
  const root = document.documentElement;
  const body = document.body;
  const interactiveSelector = [
    "a",
    "button",
    "input",
    "select",
    "textarea",
    "[tabindex]:not([tabindex='-1'])"
  ].join(",");
  const candidates = [...document.querySelectorAll<HTMLElement>("body *")]
    .filter((element) => {
      const rect = element.getBoundingClientRect();
      return rect.left < -1 || rect.right > root.clientWidth + 1;
    })
    .map((element) => {
      const rect = element.getBoundingClientRect();
      const boundaries: HorizontalBoundary[] = [];
      let ancestor = element.parentElement;
      while (ancestor && ancestor !== document.body) {
        const ancestorRect = ancestor.getBoundingClientRect();
        boundaries.push({
          explicitlyAllowsNonInteractiveClip:
            ancestor.dataset.portfolioCaptureClip === "non-interactive",
          isBounded:
            ancestorRect.left >= -1 &&
            ancestorRect.right <= root.clientWidth + 1,
          left: ancestorRect.left,
          overflowX: getComputedStyle(ancestor).overflowX,
          right: ancestorRect.right
        });
        ancestor = ancestor.parentElement;
      }
      return {
        boundaries,
        containsInteractiveContent: Boolean(
          element.closest(interactiveSelector) ||
          element.querySelector(interactiveSelector)
        ),
        offender: {
          className: element.className,
          rect: {
            bottom: rect.bottom,
            height: rect.height,
            left: rect.left,
            right: rect.right,
            top: rect.top,
            width: rect.width,
            x: rect.x,
            y: rect.y
          },
          tagName: element.tagName
        }
      };
    });
  return {
    bodyClientWidth: body.clientWidth,
    bodyScrollWidth: body.scrollWidth,
    candidates,
    clientWidth: root.clientWidth,
    scrollWidth: root.scrollWidth
  };
}

export function hasContainingHorizontalBoundary(
  candidate: HorizontalOverflowCandidate
): boolean {
  let acceptedScrollport: Pick<HorizontalBoundary, "left" | "right"> | null = null;
  let hasReviewedBoundary = false;
  for (const boundary of candidate.boundaries) {
    if (boundary.isBounded && ["auto", "scroll"].includes(boundary.overflowX)) {
      acceptedScrollport ??= boundary;
      hasReviewedBoundary = true;
      continue;
    }
    if (!["hidden", "clip"].includes(boundary.overflowX)) continue;

    const containsAcceptedScrollport =
      acceptedScrollport !== null &&
      boundary.isBounded &&
      boundary.left <= acceptedScrollport.left + 1 &&
      boundary.right >= acceptedScrollport.right - 1;
    if (containsAcceptedScrollport) continue;
    if (
      !boundary.isBounded ||
      candidate.containsInteractiveContent ||
      !boundary.explicitlyAllowsNonInteractiveClip
    ) {
      return false;
    }
    hasReviewedBoundary = true;
  }
  return hasReviewedBoundary;
}
