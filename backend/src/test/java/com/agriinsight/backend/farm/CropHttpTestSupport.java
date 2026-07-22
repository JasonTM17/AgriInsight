package com.agriinsight.backend.farm;

import com.agriinsight.backend.farm.application.CropRecord;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandTarget;
import java.util.Optional;
import java.util.UUID;

final class CropHttpTestSupport {

    static final UUID CROP_ID = UUID.fromString("34000000-0000-0000-0000-000000000001");
    static final UUID COMMAND_ID = UUID.fromString("33000000-0000-0000-0000-000000000008");

    private CropHttpTestSupport() {
    }

    static CropRecord crop(long version) {
        return crop(version, true);
    }

    static CropRecord crop(long version, boolean active) {
        return new CropRecord(
                CROP_ID, FarmHttpTestSupport.TENANT_ID, "COFFEE-A", "Arabica Coffee",
                Optional.of("Coffea arabica"), active, version);
    }

    static CommandExecutionResult.Completed<CropRecord> completed(
            int status,
            CropRecord crop) {
        return new CommandExecutionResult.Completed<>(
                COMMAND_ID, false, status,
                new CommandTarget("CROP", crop.id(), crop.version()), Optional.of(crop));
    }
}
