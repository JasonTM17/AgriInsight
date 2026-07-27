from __future__ import annotations

import os
from pathlib import Path


def validate_repository_tmp_output(
    path: Path,
    repository_tmp: Path,
    *,
    description: str,
) -> Path:
    """Resolve an output path that stays below the repository-local temp root."""
    resolved = path.resolve()
    expected_root = repository_tmp.resolve()
    is_windows_d_local = (
        os.name != "nt"
        or (
            expected_root.drive.upper() == "D:"
            and resolved.drive.upper() == "D:"
        )
    )
    if (
        not is_windows_d_local
        or resolved == expected_root
        or not resolved.is_relative_to(expected_root)
    ):
        raise ValueError(
            f"{description} must be below the repository-local _tmp directory "
            "(D: on Windows)"
        )
    return resolved
