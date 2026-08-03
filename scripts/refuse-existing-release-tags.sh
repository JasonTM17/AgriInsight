#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -lt 3 ]]; then
  echo "Usage: $0 <semantic-version> <full-sha-tag> <image> [<image>...]" >&2
  exit 64
fi

semantic_version="$1"
full_sha_tag="$2"
shift 2

for image in "$@"; do
  for tag in "$semantic_version" "$full_sha_tag"; do
    if inspection_output="$(docker buildx imagetools inspect "$image:$tag" 2>&1)"; then
      echo "Refusing to overwrite existing release tag: $image:$tag" >&2
      exit 1
    else
      inspection_status="$?"
    fi

    if grep -Eiq 'manifest (unknown|not found)|manifest for .* not found' <<<"$inspection_output"; then
      echo "Confirmed missing release tag: $image:$tag"
      continue
    fi

    echo "Could not establish immutable-tag availability: $image:$tag (inspect exit $inspection_status)" >&2
    printf '%s\n' "$inspection_output" >&2
    exit "$inspection_status"
  done
done
