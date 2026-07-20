package com.agriinsight.backend.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CanonicalCommandBodyTest {

    @Test
    void fieldsAreSortedAndValuesAreTypeFramed() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("displayName", "Admin A");
        fields.put("expectedVersion", 7L);
        fields.put("email", Optional.empty());

        assertThat(CanonicalCommandBody.of(fields))
                .isEqualTo("body-v1;11:displayName;S7:Admin A;5:email;N0:;15:expectedVersion;D1:7;");
    }

    @Test
    void insertionOrderAndOptionalPresenceAreSemanticallyStable() {
        assertThat(CanonicalCommandBody.of(Map.of("b", "two", "a", "one")))
                .isEqualTo(CanonicalCommandBody.of(Map.of("a", "one", "b", "two")));
        assertThat(CanonicalCommandBody.of(Map.of("value", Optional.empty())))
                .isNotEqualTo(CanonicalCommandBody.of(Map.of("value", "")));
    }

    @Test
    void unsupportedValuesAndUnsafeNamesFailClosed() {
        assertThatThrownBy(() -> CanonicalCommandBody.of(Map.of("unsafe key", "value")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CanonicalCommandBody.of(Map.of("value", new Object())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }
}
