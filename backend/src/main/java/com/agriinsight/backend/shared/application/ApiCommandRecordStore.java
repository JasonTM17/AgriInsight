package com.agriinsight.backend.shared.application;

import com.agriinsight.backend.shared.domain.ApiCommandRecord;
import java.util.Objects;

public interface ApiCommandRecordStore {

    Claim claim(ApiCommandRecord reservation);

    ApiCommandRecord complete(ApiCommandRecord completedRecord);

    record Claim(boolean claimed, ApiCommandRecord record) {

        public Claim {
            Objects.requireNonNull(record, "record is required");
            if (claimed && record.state() != ApiCommandRecord.State.IN_PROGRESS) {
                throw new IllegalArgumentException("A newly claimed command must be in progress");
            }
        }

        public static Claim claimed(ApiCommandRecord record) {
            return new Claim(true, record);
        }

        public static Claim existing(ApiCommandRecord record) {
            return new Claim(false, record);
        }
    }
}
