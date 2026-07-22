package com.agriinsight.backend.cost.api;

import com.agriinsight.backend.cost.application.CostCorrectionRecord;

public record CostCorrectionResponse(
        OperatingCostResponse reversal,
        OperatingCostResponse replacement) {

    static CostCorrectionResponse from(CostCorrectionRecord correction) {
        return new CostCorrectionResponse(
                OperatingCostResponse.from(correction.reversal()),
                OperatingCostResponse.from(correction.replacement()));
    }
}
