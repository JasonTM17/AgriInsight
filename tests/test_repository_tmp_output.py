from __future__ import annotations

from pathlib import Path

import pytest

from agriinsight.repository_tmp_output import validate_repository_tmp_output


def test_repository_tmp_output_accepts_only_a_descendant() -> None:
    repository_tmp = Path(__file__).resolve().parents[1] / "_tmp"
    expected = (repository_tmp / "safe-validation-only").resolve()

    assert (
        validate_repository_tmp_output(
            expected,
            repository_tmp,
            description="Test output",
        )
        == expected
    )


@pytest.mark.parametrize(
    "candidate",
    [
        Path(__file__).resolve().parents[1] / "_tmp",
        Path(__file__).resolve().parents[1] / "_tmp-sibling" / "output",
        Path(__file__).resolve().parents[1] / "artifacts" / "output",
    ],
)
def test_repository_tmp_output_rejects_root_and_escape(candidate: Path) -> None:
    repository_tmp = Path(__file__).resolve().parents[1] / "_tmp"

    with pytest.raises(ValueError, match="repository-local"):
        validate_repository_tmp_output(
            candidate,
            repository_tmp,
            description="Test output",
        )
