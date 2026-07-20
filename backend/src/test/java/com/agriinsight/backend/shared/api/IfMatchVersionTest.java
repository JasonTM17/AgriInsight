package com.agriinsight.backend.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IfMatchVersionTest {

    @Test
    void parsesAndCanonicalizesStrongNumericEtags() {
        IfMatchVersion version = IfMatchVersion.parse("\"007\"");

        assertThat(version.value()).isEqualTo(7);
        assertThat(version.canonicalHeaderValue()).isEqualTo("\"7\"");
    }

    @Test
    void rejectsWeakWildcardAndUnquotedEtags() {
        assertThatThrownBy(() -> IfMatchVersion.parse("W/\"7\""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IfMatchVersion.parse("*"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IfMatchVersion.parse("7"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
