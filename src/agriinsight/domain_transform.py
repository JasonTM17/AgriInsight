from __future__ import annotations

from dataclasses import dataclass

import pandas as pd


@dataclass
class DomainTransformResult:
    silver: dict[str, pd.DataFrame]
    quarantine: dict[str, pd.DataFrame]
    actions: dict[str, int]

