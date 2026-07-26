type ReviewedVisualProps = Readonly<{
  alt: string;
  filename: string;
  height: number;
  width: number;
}>;

const REVIEWED_WEBP_FILENAME = /^[a-z0-9]+(?:-[a-z0-9]+)*\.webp$/;

export function ReviewedVisual({
  alt,
  filename,
  height,
  width
}: ReviewedVisualProps) {
  if (!REVIEWED_WEBP_FILENAME.test(filename)) {
    throw new Error("Reviewed visual filename is invalid");
  }
  return (
    // Next Image emits an inline style, which the nonce-only CSP must reject.
    // eslint-disable-next-line @next/next/no-img-element
    <img
      alt={alt}
      decoding="async"
      height={height}
      loading="lazy"
      src={`/visuals/${filename}`}
      width={width}
    />
  );
}
