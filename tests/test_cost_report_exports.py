from __future__ import annotations

import io

import pandas as pd
import pytest

from agriinsight.cost_report_contract import (
    MAX_DETAIL_ROWS,
    CostReportDomains,
    CostReportMetadata,
    CostReportRequest,
    ReportValidationError,
)
from agriinsight.cost_report_csv import render_cost_report_csv
from agriinsight.cost_report_data import prepare_cost_report


def _request_and_metadata(gold, manifest, raw=None):
    request = CostReportRequest.from_mapping(raw or {}, CostReportDomains.from_gold(gold))
    return request, CostReportMetadata.from_manifest(manifest, request)


def test_request_is_allowlisted_and_filename_is_deterministic(report_sources) -> None:
    gold, manifest = report_sources
    domains = CostReportDomains.from_gold(gold)
    farm = sorted(domains.farms)[0]
    first = CostReportRequest.from_mapping(
        {"scope": "all", "farm": farm, "top_n": 12}, domains
    )
    second = CostReportRequest.from_mapping(
        {"top_n": 12, "farm": farm, "scope": "all"}, domains
    )
    first_metadata = CostReportMetadata.from_manifest(manifest, first)
    second_metadata = CostReportMetadata.from_manifest(manifest, second)

    assert first.filter_hash == second.filter_hash
    assert first_metadata.filename(first, "pdf") == second_metadata.filename(second, "pdf")
    assert first_metadata.filename(first, "pdf").startswith("cost-analysis_2026-07-18_all_")
    assert "/" not in first_metadata.filename(first, "pdf")
    assert "\\" not in first_metadata.filename(first, "pdf")


def test_unselected_season_preserves_version_one_filter_hash() -> None:
    assert CostReportRequest().filter_hash == "9c2828af6f25"


def test_operating_season_is_canonical_and_changes_filter_hash(report_sources) -> None:
    gold, _ = report_sources
    domains = CostReportDomains.from_gold(gold)
    season = sorted(domains.seasons)[0]
    baseline = CostReportRequest.from_mapping({"scope": "operating"}, domains)
    selected = CostReportRequest.from_mapping(
        {"scope": "operating", "season": season}, domains
    )

    assert domains.seasons == frozenset(
        gold["cost_activity_detail"]["season_code"].dropna().astype(str).unique()
    )
    assert selected.season == season
    assert selected.canonical_dict()["season"] == season
    assert selected.filter_hash != baseline.filter_hash


@pytest.mark.parametrize(
    "raw",
    (
        {"unknown": "value"},
        {"scope": "everything"},
        {"farm": "../../escape"},
        {"scope": "operating", "season": "../escape"},
        {"scope": "operating", "season": "not-a-season"},
        {"month_from": "2026-13"},
        {"top_n": 0},
        {"top_n": True},
        {1: "non-string-key"},
    ),
)
def test_request_rejects_unknown_or_unsafe_values(report_sources, raw) -> None:
    gold, _ = report_sources
    with pytest.raises(ReportValidationError):
        CostReportRequest.from_mapping(raw, CostReportDomains.from_gold(gold))


def test_request_rejects_filters_from_the_wrong_semantic_lens(report_sources) -> None:
    gold, _ = report_sources
    domains = CostReportDomains.from_gold(gold)
    season = sorted(domains.seasons)[0]
    with pytest.raises(ReportValidationError, match="single lens"):
        CostReportRequest.from_mapping(
            {"scope": "all", "supplier": sorted(domains.suppliers)[0]}, domains
        )
    with pytest.raises(ReportValidationError, match="single lens"):
        CostReportRequest.from_mapping(
            {"scope": "all", "season": season}, domains
        )
    with pytest.raises(ReportValidationError, match="procurement"):
        CostReportRequest.from_mapping(
            {"scope": "operating", "supplier": sorted(domains.suppliers)[0]}, domains
        )
    with pytest.raises(ReportValidationError, match="operating"):
        CostReportRequest.from_mapping(
            {"scope": "procurement", "season": season}, domains
        )


def test_request_rejects_reversed_months_and_invalid_manifest_date(
    report_sources,
) -> None:
    gold, manifest = report_sources
    domains = CostReportDomains.from_gold(gold)
    months = sorted(domains.months)
    with pytest.raises(ReportValidationError, match="must not be after"):
        CostReportRequest.from_mapping(
            {"month_from": months[-1], "month_to": months[0]}, domains
        )

    request = CostReportRequest.from_mapping({}, domains)
    invalid_manifest = {**manifest, "as_of_date": "2026-02-31"}
    with pytest.raises(ReportValidationError, match="valid date"):
        CostReportMetadata.from_manifest(invalid_manifest, request)
    with pytest.raises(ReportValidationError, match="pipeline"):
        CostReportMetadata.from_manifest({**manifest, "pipeline": ""}, request)


def test_prepared_report_keeps_operating_and_procurement_separate(report_sources) -> None:
    gold, manifest = report_sources
    request, metadata = _request_and_metadata(gold, manifest)
    report = prepare_cost_report(gold, request, metadata)
    summary = report.summary.iloc[0]

    assert report.detail_row_count == len(report.cost_detail) + len(report.procurement_detail)
    assert "combined_total_vnd" not in report.summary.columns
    assert summary["operating_total_cost_vnd"] == pytest.approx(
        report.cost_detail["operating_total_cost_vnd"].sum()
    )
    assert summary["procurement_spend_vnd"] == pytest.approx(
        report.procurement_detail["procurement_spend_vnd"].sum()
    )
    assert set(report.checks["status"]) == {"PASS"}
    assert report.monthly["month"].is_monotonic_increasing


def test_prepared_operating_report_filters_by_season(report_sources) -> None:
    gold, manifest = report_sources
    domains = CostReportDomains.from_gold(gold)
    season = sorted(domains.seasons)[0]
    request, metadata = _request_and_metadata(
        gold,
        manifest,
        {"scope": "operating", "season": season},
    )
    report = prepare_cost_report(gold, request, metadata)
    expected = gold["cost_activity_detail"].loc[
        gold["cost_activity_detail"]["season_code"].eq(season)
    ]
    metadata_values = report.metadata.set_index("item")["value"]

    assert not report.cost_detail.empty
    assert report.procurement_detail.empty
    assert report.cost_detail["season_code"].eq(season).all()
    assert len(report.cost_detail) == len(expected)
    assert report.summary.iloc[0]["operating_activity_count"] == len(expected)
    assert metadata_values["filter_season"] == season


def test_csv_is_deterministic_round_trippable_and_formula_safe(report_sources) -> None:
    original_gold, manifest = report_sources
    gold = dict(original_gold)
    cost = original_gold["cost_activity_detail"].copy()
    record_id = cost.loc[0, "activity_id"]
    cost.loc[0, "notes"] = "=SUM(A1:A2)"
    gold["cost_activity_detail"] = cost
    request, metadata = _request_and_metadata(gold, manifest)
    report = prepare_cost_report(gold, request, metadata)

    first = render_cost_report_csv(report)
    second = render_cost_report_csv(report)
    round_trip = pd.read_csv(io.BytesIO(first), encoding="utf-8-sig")

    assert first == second
    assert set(round_trip["lens"]) == {"operating", "procurement"}
    expected_metadata = {
        "export_version": metadata.export_version,
        "run_id": metadata.run_id,
        "as_of_date": metadata.as_of_date,
        "source_pipeline": metadata.source_pipeline,
        "filter_hash": metadata.filter_hash,
    }
    for column, expected in expected_metadata.items():
        assert round_trip[column].nunique() == 1
        assert round_trip[column].iloc[0] == expected
    note = round_trip.loc[round_trip["record_id"] == record_id, "notes"].iloc[0]
    assert note == "'=SUM(A1:A2)"
    assert len(round_trip) == report.detail_row_count


def test_detail_row_cap_and_empty_results_fail_closed(
    report_sources, monkeypatch
) -> None:
    original_gold, manifest = report_sources
    domains = CostReportDomains.from_gold(original_gold)
    operating_request = CostReportRequest.from_mapping({"scope": "operating"}, domains)
    metadata = CostReportMetadata.from_manifest(manifest, operating_request)

    oversized_gold = dict(original_gold)
    cost = original_gold["cost_activity_detail"]
    copies = MAX_DETAIL_ROWS // len(cost) + 1

    class GuardedOversizedFrame(pd.DataFrame):
        @property
        def loc(self):
            pytest.fail("row caps must run before filtered rows are materialized")

    oversized_gold["cost_activity_detail"] = GuardedOversizedFrame(
        pd.concat([cost] * copies, ignore_index=True).iloc[: MAX_DETAIL_ROWS + 1]
    )
    monkeypatch.setattr(
        pd.DataFrame,
        "sort_values",
        lambda *args, **kwargs: pytest.fail("row caps must run before sorting"),
    )
    with pytest.raises(ReportValidationError, match="25,000"):
        prepare_cost_report(oversized_gold, operating_request, metadata)

    monkeypatch.undo()

    empty_gold = dict(original_gold)
    farm = sorted(domains.farms)[0]
    farm_request = CostReportRequest.from_mapping(
        {"scope": "operating", "farm": farm}, domains
    )
    empty_gold["cost_activity_detail"] = cost.loc[cost["farm_code"] != farm]
    with pytest.raises(ReportValidationError, match="empty report"):
        prepare_cost_report(
            empty_gold,
            farm_request,
            CostReportMetadata.from_manifest(manifest, farm_request),
        )


def test_combined_detail_row_cap_fails_before_sorting(report_sources, monkeypatch) -> None:
    original_gold, manifest = report_sources
    domains = CostReportDomains.from_gold(original_gold)
    request = CostReportRequest.from_mapping({}, domains)
    metadata = CostReportMetadata.from_manifest(manifest, request)
    operating_count = MAX_DETAIL_ROWS // 2 + 1
    procurement_count = MAX_DETAIL_ROWS - operating_count + 1

    def repeated_rows(frame: pd.DataFrame, count: int) -> pd.DataFrame:
        copies = count // len(frame) + 1
        return pd.concat([frame] * copies, ignore_index=True).iloc[:count]

    oversized_gold = {
        **original_gold,
        "cost_activity_detail": repeated_rows(
            original_gold["cost_activity_detail"], operating_count
        ),
        "procurement_detail": repeated_rows(
            original_gold["procurement_detail"], procurement_count
        ),
    }
    monkeypatch.setattr(
        pd.DataFrame,
        "sort_values",
        lambda *args, **kwargs: pytest.fail("combined cap must run before sorting"),
    )

    with pytest.raises(ReportValidationError, match="complete detail bundle"):
        prepare_cost_report(oversized_gold, request, metadata)
