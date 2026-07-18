from __future__ import annotations

from html import escape
from io import BytesIO
from pathlib import Path
from types import SimpleNamespace
from typing import Any, Sequence

from agriinsight.cost_report_assets import bundled_font_dir
from agriinsight.cost_report_contract import (
    CostReportMetadata,
    CostReportRequest,
    ExportUnavailable,
    PreparedCostReport,
)


_REGULAR_FONT = "AgriInsightNotoSans"
_BOLD_FONT = "AgriInsightNotoSans-Bold"


def _load_reportlab() -> SimpleNamespace:
    """Import the optional PDF stack only when a PDF is requested."""
    try:
        from reportlab.lib import colors
        from reportlab.lib.enums import TA_CENTER, TA_LEFT, TA_RIGHT
        from reportlab.lib.pagesizes import A4, landscape
        from reportlab.lib.styles import ParagraphStyle
        from reportlab.pdfbase import pdfmetrics
        from reportlab.pdfbase.ttfonts import TTFont, TTFError
        from reportlab.pdfgen import canvas
        from reportlab.platypus import (
            PageBreak,
            Paragraph,
            SimpleDocTemplate,
            Table,
            TableStyle,
        )
    except ImportError as error:
        raise ExportUnavailable(
            "PDF export requires ReportLab. Install the project reports extra with "
            '`python -m pip install -e ".[reports]"` and retry.'
        ) from error

    return SimpleNamespace(
        A4=A4,
        PageBreak=PageBreak,
        Paragraph=Paragraph,
        ParagraphStyle=ParagraphStyle,
        SimpleDocTemplate=SimpleDocTemplate,
        Table=Table,
        TableStyle=TableStyle,
        TA_CENTER=TA_CENTER,
        TA_LEFT=TA_LEFT,
        TA_RIGHT=TA_RIGHT,
        TTFont=TTFont,
        TTFError=TTFError,
        canvas=canvas,
        colors=colors,
        landscape=landscape,
        pdfmetrics=pdfmetrics,
    )


def _register_fonts(rl: SimpleNamespace, font_dir: Path) -> None:
    directory = Path(font_dir)
    assets = {
        "NotoSans-Regular.ttf": directory / "NotoSans-Regular.ttf",
        "NotoSans-Bold.ttf": directory / "NotoSans-Bold.ttf",
        "OFL.txt": directory / "OFL.txt",
    }
    missing = [name for name, path in assets.items() if not path.is_file()]
    if missing:
        raise ExportUnavailable(
            "PDF export needs the bundled Noto Sans fonts and SIL OFL license. "
            f"Restore {', '.join(missing)} under {directory} and retry."
        )

    try:
        rl.pdfmetrics.registerFont(
            rl.TTFont(_REGULAR_FONT, str(assets["NotoSans-Regular.ttf"]))
        )
        rl.pdfmetrics.registerFont(
            rl.TTFont(_BOLD_FONT, str(assets["NotoSans-Bold.ttf"]))
        )
        rl.pdfmetrics.registerFontFamily(
            _REGULAR_FONT,
            normal=_REGULAR_FONT,
            bold=_BOLD_FONT,
            italic=_REGULAR_FONT,
            boldItalic=_BOLD_FONT,
        )
    except (OSError, ValueError, rl.TTFError) as error:
        raise ExportUnavailable(
            "PDF export could not load the bundled Noto Sans font files. "
            f"Restore readable font assets under {directory} and retry."
        ) from error


def _styles(rl: SimpleNamespace) -> dict[str, Any]:
    navy = rl.colors.HexColor("#17324D")
    blue = rl.colors.HexColor("#235D7D")
    slate = rl.colors.HexColor("#455A64")
    green = rl.colors.HexColor("#1F6D4A")
    red = rl.colors.HexColor("#A13B35")
    return {
        "title": rl.ParagraphStyle(
            "Title",
            fontName=_BOLD_FONT,
            fontSize=20,
            leading=24,
            textColor=navy,
            spaceAfter=5,
        ),
        "subtitle": rl.ParagraphStyle(
            "Subtitle",
            fontName=_REGULAR_FONT,
            fontSize=9,
            leading=13,
            textColor=slate,
            spaceAfter=14,
        ),
        "section": rl.ParagraphStyle(
            "Section",
            fontName=_BOLD_FONT,
            fontSize=11,
            leading=14,
            textColor=blue,
            spaceBefore=10,
            spaceAfter=6,
            keepWithNext=True,
        ),
        "body": rl.ParagraphStyle(
            "Body",
            fontName=_REGULAR_FONT,
            fontSize=7.5,
            leading=10,
            textColor=navy,
            alignment=rl.TA_LEFT,
        ),
        "right": rl.ParagraphStyle(
            "Right",
            fontName=_REGULAR_FONT,
            fontSize=7.5,
            leading=10,
            textColor=navy,
            alignment=rl.TA_RIGHT,
        ),
        "header": rl.ParagraphStyle(
            "Header",
            fontName=_BOLD_FONT,
            fontSize=7,
            leading=9,
            textColor=rl.colors.white,
            alignment=rl.TA_CENTER,
        ),
        "note": rl.ParagraphStyle(
            "Note",
            fontName=_REGULAR_FONT,
            fontSize=7.5,
            leading=11,
            textColor=slate,
            spaceBefore=7,
        ),
        "empty": rl.ParagraphStyle(
            "Empty",
            fontName=_REGULAR_FONT,
            fontSize=8,
            leading=11,
            textColor=slate,
            leftIndent=8,
        ),
        "model_pass": rl.ParagraphStyle(
            "ModelPass",
            fontName=_BOLD_FONT,
            fontSize=10,
            leading=13,
            textColor=green,
            spaceBefore=10,
            spaceAfter=6,
            keepWithNext=True,
        ),
        "model_fail": rl.ParagraphStyle(
            "ModelFail",
            fontName=_BOLD_FONT,
            fontSize=10,
            leading=13,
            textColor=red,
            spaceBefore=10,
            spaceAfter=6,
            keepWithNext=True,
        ),
    }


def _text(value: object) -> str:
    return escape(str(value), quote=False).replace("\n", "<br/>")


def _number(value: object, decimals: int = 0) -> str:
    return f"{float(value):,.{decimals}f}"


def _vnd(value: object) -> str:
    return f"{_number(value)} VND"


def _table(
    rl: SimpleNamespace,
    styles: dict[str, Any],
    headers: Sequence[object],
    rows: Sequence[Sequence[object]],
    widths: Sequence[float],
    *,
    right_columns: frozenset[int] = frozenset(),
    extra_commands: Sequence[tuple[Any, ...]] = (),
) -> Any:
    data = [
        [rl.Paragraph(_text(value), styles["header"]) for value in headers],
        *[
            [
                rl.Paragraph(
                    _text(value),
                    styles["right"] if index in right_columns else styles["body"],
                )
                for index, value in enumerate(row)
            ]
            for row in rows
        ],
    ]
    commands: list[tuple[Any, ...]] = [
        ("BACKGROUND", (0, 0), (-1, 0), rl.colors.HexColor("#235D7D")),
        ("GRID", (0, 0), (-1, -1), 0.35, rl.colors.HexColor("#C9D4DA")),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("LEFTPADDING", (0, 0), (-1, -1), 5),
        ("RIGHTPADDING", (0, 0), (-1, -1), 5),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
    ]
    for row_index in range(1, len(data)):
        if row_index % 2 == 0:
            commands.append(
                (
                    "BACKGROUND",
                    (0, row_index),
                    (-1, row_index),
                    rl.colors.HexColor("#F3F7F8"),
                )
            )
    commands.extend(extra_commands)
    return rl.Table(
        data,
        colWidths=list(widths),
        repeatRows=1,
        splitByRow=1,
        hAlign="LEFT",
        style=rl.TableStyle(commands),
    )


def _operating_rows(report: PreparedCostReport, top_n: int) -> list[list[str]]:
    detail = report.cost_detail
    if detail.empty:
        return []
    grouped = (
        detail.groupby("activity_type", as_index=False, dropna=False, sort=True)
        .agg(
            activity_count=("activity_id", "count"),
            material_cost=("operating_material_cost_vnd", "sum"),
            labor_cost=("operating_labor_cost_vnd", "sum"),
            total_cost=("operating_total_cost_vnd", "sum"),
        )
        .sort_values(
            ["total_cost", "activity_type"],
            ascending=[False, True],
            kind="stable",
            ignore_index=True,
        )
        .head(top_n)
    )
    denominator = float(detail["operating_total_cost_vnd"].sum())
    return [
        [
            row.activity_type,
            _number(row.activity_count),
            _vnd(row.material_cost),
            _vnd(row.labor_cost),
            _vnd(row.total_cost),
            f"{100 * float(row.total_cost) / denominator:.1f}%" if denominator else "0.0%",
        ]
        for row in grouped.itertuples(index=False)
    ]


def _procurement_rows(report: PreparedCostReport, top_n: int) -> list[list[str]]:
    detail = report.procurement_detail
    if detail.empty:
        return []
    grouped = (
        detail.groupby(
            ["supplier_name", "material_name", "base_unit"],
            as_index=False,
            dropna=False,
            sort=True,
        )
        .agg(
            transaction_count=("transaction_id", "count"),
            quantity=("procurement_quantity_base_unit", "sum"),
            spend=("procurement_spend_vnd", "sum"),
        )
        .sort_values(
            ["spend", "supplier_name", "material_name", "base_unit"],
            ascending=[False, True, True, True],
            kind="stable",
            ignore_index=True,
        )
        .head(top_n)
    )
    denominator = float(detail["procurement_spend_vnd"].sum())
    return [
        [
            row.supplier_name,
            row.material_name,
            row.base_unit,
            _number(row.transaction_count),
            _number(row.quantity, 2),
            _vnd(row.spend),
            f"{100 * float(row.spend) / denominator:.1f}%" if denominator else "0.0%",
        ]
        for row in grouped.itertuples(index=False)
    ]


def render_cost_report_pdf(
    report: PreparedCostReport,
    request: CostReportRequest,
    metadata: CostReportMetadata,
    font_dir: Path | None = None,
) -> bytes:
    """Render a deterministic, bounded Vietnamese management report as PDF bytes."""
    rl = _load_reportlab()
    _register_fonts(rl, font_dir or bundled_font_dir())
    styles = _styles(rl)
    page_size = rl.landscape(rl.A4)
    page_width, page_height = page_size
    left_margin = right_margin = 36
    content_width = page_width - left_margin - right_margin
    buffer = BytesIO()

    document = rl.SimpleDocTemplate(
        buffer,
        pagesize=page_size,
        leftMargin=left_margin,
        rightMargin=right_margin,
        topMargin=42,
        bottomMargin=34,
        pageCompression=1,
        invariant=1,
        allowSplitting=1,
        title=f"AgriInsight Cost Analysis {metadata.as_of_date}",
        author="AgriInsight",
        subject="Deterministic cost analysis management report",
        creator="AgriInsight report export",
    )

    def deterministic_canvas(filename: Any, **kwargs: Any) -> Any:
        kwargs["invariant"] = 1
        kwargs["pageCompression"] = 1
        return rl.canvas.Canvas(filename, **kwargs)

    def decorate_page(canvas: Any, _document: Any) -> None:
        canvas.saveState()
        canvas.setTitle(f"AgriInsight Cost Analysis {metadata.as_of_date}")
        canvas.setAuthor("AgriInsight")
        canvas.setSubject("Deterministic cost analysis management report")
        canvas.setCreator("AgriInsight report export")
        canvas.setKeywords("AgriInsight, cost analysis, management report")
        canvas.setStrokeColor(rl.colors.HexColor("#B8C8D0"))
        canvas.setLineWidth(0.5)
        canvas.line(left_margin, page_height - 29, page_width - right_margin, page_height - 29)
        canvas.setFont(_BOLD_FONT, 7.5)
        canvas.setFillColor(rl.colors.HexColor("#17324D"))
        canvas.drawString(left_margin, page_height - 22, "AgriInsight - Báo cáo phân tích chi phí")
        canvas.setFont(_REGULAR_FONT, 7)
        canvas.drawRightString(
            page_width - right_margin,
            page_height - 22,
            f"Run: {metadata.run_id[:64]}",
        )
        canvas.line(left_margin, 25, page_width - right_margin, 25)
        canvas.setFillColor(rl.colors.HexColor("#455A64"))
        canvas.drawString(
            left_margin,
            14,
            f"Ngày dữ liệu: {metadata.as_of_date} | Mã bộ lọc: {metadata.filter_hash}",
        )
        canvas.drawRightString(
            page_width - right_margin,
            14,
            f"Trang {canvas.getPageNumber()}",
        )
        canvas.restoreState()

    scope_labels = {
        "all": "Tất cả lăng kính",
        "operating": "Chi phí vận hành",
        "procurement": "Chi tiêu mua hàng",
    }
    filter_rows = [
        ["Phạm vi", scope_labels[request.scope], "Nông trại", request.farm or "Tất cả"],
        ["Cây trồng", request.crop or "Tất cả", "Hoạt động", request.activity or "Tất cả"],
        ["Nhà cung cấp", request.supplier or "Tất cả", "Từ tháng", request.month_from or "Đầu kỳ"],
        ["Đến tháng", request.month_to or "Cuối kỳ", "Giới hạn xếp hạng", request.top_n],
    ]
    metadata_rows = [
        ["Run ID", metadata.run_id, "Ngày dữ liệu", metadata.as_of_date],
        ["Nguồn dữ liệu", metadata.source_pipeline, "Mã bộ lọc", metadata.filter_hash],
        ["Phiên bản", metadata.export_version, "Dòng chi tiết", report.detail_row_count],
    ]
    summary = report.summary.iloc[0]
    semantic_rows = [
        [
            "Chi phí vận hành",
            _vnd(summary["operating_total_cost_vnd"]),
            (
                f"{_number(summary['operating_activity_count'])} hoạt động; "
                f"vật tư {_vnd(summary['operating_material_cost_vnd'])}; "
                f"nhân công {_vnd(summary['operating_labor_cost_vnd'])}."
            ),
        ],
        [
            "Chi tiêu mua hàng",
            _vnd(summary["procurement_spend_vnd"]),
            (
                f"{_number(summary['procurement_transaction_count'])} giao dịch nhập; "
                f"số lượng {_number(summary['procurement_quantity_base_unit'], 2)} theo đơn vị cơ sở."
            ),
        ],
        [
            "Giá trị tồn kho",
            "Không tính trong báo cáo",
            "Ảnh chụp giá trị tài sản tại một thời điểm, không phải chi phí của kỳ.",
        ],
    ]

    story: list[Any] = [
        rl.Paragraph("BÁO CÁO PHÂN TÍCH CHI PHÍ", styles["title"]),
        rl.Paragraph(
            "Báo cáo quản trị từ dữ liệu Gold đã lọc; số liệu tiền tệ trình bày bằng VND.",
            styles["subtitle"],
        ),
        rl.Paragraph("Bộ lọc đã áp dụng", styles["section"]),
        _table(
            rl,
            styles,
            ["Thuộc tính", "Giá trị", "Thuộc tính", "Giá trị"],
            filter_rows,
            [105, 280, 105, content_width - 490],
        ),
        rl.Paragraph("Dấu vết chạy dữ liệu", styles["section"]),
        _table(
            rl,
            styles,
            ["Thuộc tính", "Giá trị", "Thuộc tính", "Giá trị"],
            metadata_rows,
            [105, 280, 105, content_width - 490],
        ),
        rl.Paragraph("Các thước đo phải đọc riêng", styles["section"]),
        _table(
            rl,
            styles,
            ["Thước đo", "Giá trị", "Diễn giải"],
            semantic_rows,
            [140, 170, content_width - 310],
            right_columns=frozenset({1}),
        ),
        rl.Paragraph(
            "Lưu ý ngữ nghĩa: Không cộng chi phí vận hành với chi tiêu mua hàng. "
            "Chi tiêu mua hàng chưa phải chi phí hoạt động nếu chưa có sổ phân bổ; "
            "giá trị tồn kho là số dư tài sản và cũng không được cộng vào hai thước đo trên.",
            styles["note"],
        ),
        rl.PageBreak(),
        rl.Paragraph("Xu hướng theo tháng", styles["section"]),
    ]

    monthly_rows = [
        [
            row.month,
            _vnd(row.operating_material_cost_vnd),
            _vnd(row.operating_labor_cost_vnd),
            _vnd(row.operating_total_cost_vnd),
            _number(row.procurement_quantity_base_unit, 2),
            _vnd(row.procurement_spend_vnd),
        ]
        for row in report.monthly.itertuples(index=False)
    ]
    story.append(
        _table(
            rl,
            styles,
            [
                "Tháng",
                "Vật tư vận hành",
                "Nhân công vận hành",
                "Tổng vận hành",
                "Số lượng mua",
                "Chi tiêu mua hàng",
            ],
            monthly_rows,
            [55, 135, 125, 135, 120, content_width - 570],
            right_columns=frozenset({1, 2, 3, 4, 5}),
        )
    )

    operating_rows = _operating_rows(report, request.top_n)
    story.append(rl.Paragraph(f"Top {request.top_n} nhóm hoạt động theo chi phí", styles["section"]))
    if operating_rows:
        story.append(
            _table(
                rl,
                styles,
                ["Hoạt động", "Số lượt", "Vật tư", "Nhân công", "Tổng vận hành", "Tỷ trọng"],
                operating_rows,
                [145, 65, 130, 130, 145, content_width - 615],
                right_columns=frozenset({1, 2, 3, 4, 5}),
            )
        )
    else:
        story.append(rl.Paragraph("Không có dữ liệu vận hành trong phạm vi đã chọn.", styles["empty"]))

    procurement_rows = _procurement_rows(report, request.top_n)
    story.append(rl.Paragraph(f"Top {request.top_n} nhóm mua hàng theo chi tiêu", styles["section"]))
    if procurement_rows:
        story.append(
            _table(
                rl,
                styles,
                [
                    "Nhà cung cấp",
                    "Vật tư",
                    "Đơn vị",
                    "Giao dịch",
                    "Số lượng",
                    "Chi tiêu",
                    "Tỷ trọng",
                ],
                procurement_rows,
                [130, 135, 55, 65, 95, 145, content_width - 625],
                right_columns=frozenset({3, 4, 5, 6}),
            )
        )
    else:
        story.append(rl.Paragraph("Không có dữ liệu mua hàng trong phạm vi đã chọn.", styles["empty"]))

    checks_pass = not report.checks.empty and report.checks["status"].astype(str).eq("PASS").all()
    model_status = "PASS" if checks_pass else "FAIL"
    story.append(
        rl.Paragraph(
            f"MODEL STATUS: {model_status}",
            styles["model_pass" if checks_pass else "model_fail"],
        )
    )
    check_labels = {
        "Operating components tie": "Thành phần chi phí vận hành",
        "Operating summary ties to detail": "Tổng hợp = chi tiết vận hành",
        "Procurement summary ties to detail": "Tổng hợp = chi tiết mua hàng",
    }
    check_notes = {
        "Operating components tie": "Tổng = vật tư + nhân công.",
        "Operating summary ties to detail": "Tổng hợp từ hoạt động đã lọc.",
        "Procurement summary ties to detail": "Chỉ gồm giao dịch nhập kho.",
    }
    fix_locations = {
        "Cost Detail": "Chi tiết",
        "Summary": "Tổng hợp",
    }
    check_rows = [
        [
            check_labels.get(row.check_name, row.check_name),
            _vnd(row.delta_vnd),
            _vnd(row.tolerance_vnd),
            row.status,
            fix_locations.get(row.where_to_fix, row.where_to_fix),
            check_notes.get(row.check_name, row.notes),
        ]
        for row in report.checks.itertuples(index=False)
    ]
    check_commands: list[tuple[Any, ...]] = []
    for index, row in enumerate(check_rows, start=1):
        color = "#E6F4EC" if row[3] == "PASS" else "#FCE8E6"
        check_commands.append(("BACKGROUND", (3, index), (3, index), rl.colors.HexColor(color)))
    story.append(
        _table(
            rl,
            styles,
            ["Kiểm tra", "Chênh lệch", "Ngưỡng", "Trạng thái", "Nơi xử lý", "Ghi chú"],
            check_rows,
            [145, 90, 80, 65, 105, content_width - 485],
            right_columns=frozenset({1, 2}),
            extra_commands=check_commands,
        )
    )
    document.build(
        story,
        onFirstPage=decorate_page,
        onLaterPages=decorate_page,
        canvasmaker=deterministic_canvas,
    )
    return buffer.getvalue()


__all__ = ["render_cost_report_pdf"]
