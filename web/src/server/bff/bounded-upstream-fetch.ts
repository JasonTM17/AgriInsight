import "server-only";

const MAX_UPSTREAM_BYTES = 2 * 1024 * 1024;
const MAX_EXPORT_BYTES = 10 * 1024 * 1024;
const UPSTREAM_TIMEOUT_MS = 5_000;
const EXPORT_TIMEOUT_MS = 30_000;

export class UpstreamResponseError extends Error {
  constructor(
    readonly status: number,
    message: string
  ) {
    super(message);
    this.name = "UpstreamResponseError";
  }
}

export async function boundedUpstreamFetch(
  input: string | URL,
  init: RequestInit
): Promise<Response> {
  const response = await fetch(input, {
    ...init,
    cache: "no-store",
    redirect: "manual",
    signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS)
  });
  if (response.status >= 300 && response.status < 400) {
    throw new UpstreamResponseError(502, "Upstream redirect was rejected");
  }
  const contentLength = readContentLength(response);
  if (contentLength > MAX_UPSTREAM_BYTES) {
    throw new UpstreamResponseError(502, "Upstream response exceeded the byte limit");
  }
  if (!response.body) return response;

  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let received = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    received += value.byteLength;
    if (received > MAX_UPSTREAM_BYTES) {
      await reader.cancel();
      throw new UpstreamResponseError(
        502,
        "Upstream response exceeded the byte limit"
      );
    }
    chunks.push(value);
  }
  const body = new Uint8Array(received);
  let offset = 0;
  for (const chunk of chunks) {
    body.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return new Response(body, {
    headers: response.headers,
    status: response.status,
    statusText: response.statusText
  });
}

export async function boundedUpstreamStreamFetch(
  input: string | URL,
  init: RequestInit
): Promise<Response> {
  const response = await fetch(input, {
    ...init,
    cache: "no-store",
    redirect: "manual",
    signal: AbortSignal.timeout(EXPORT_TIMEOUT_MS)
  });
  if (response.status >= 300 && response.status < 400) {
    throw new UpstreamResponseError(502, "Upstream redirect was rejected");
  }
  const contentLength = readContentLength(response);
  if (contentLength > MAX_EXPORT_BYTES) {
    throw new UpstreamResponseError(502, "Upstream response exceeded the export byte limit");
  }
  if (!response.body) return response;

  let received = 0;
  const boundedBody = response.body.pipeThrough(
    new TransformStream<Uint8Array, Uint8Array>({
      transform(chunk, controller) {
        received += chunk.byteLength;
        if (received > MAX_EXPORT_BYTES) {
          controller.error(
            new UpstreamResponseError(
              502,
              "Upstream response exceeded the export byte limit"
            )
          );
          return;
        }
        controller.enqueue(chunk);
      }
    })
  );
  return new Response(boundedBody, {
    headers: response.headers,
    status: response.status,
    statusText: response.statusText
  });
}

function readContentLength(response: Response): number {
  const raw = response.headers.get("content-length");
  if (raw === null) return 0;
  const contentLength = Number(raw);
  if (!Number.isSafeInteger(contentLength) || contentLength < 0) {
    throw new UpstreamResponseError(502, "Upstream content length was invalid");
  }
  return contentLength;
}
