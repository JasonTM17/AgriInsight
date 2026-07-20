package com.agriinsight.backend.shared.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class IdempotencyKeyTest {

    @Test
    void rawKeyIsImmediatelyReducedToAFixedDigest() {
        IdempotencyKey key = IdempotencyKey.parse("request-00000001");

        assertThat(key.digest())
                .isEqualTo("20a35b2f7aae3c08045de53379816d8986808605640e89482747e0384e31b03b");
        assertThat(key.toString()).doesNotContain("request-00000001");
        assertThat(Arrays.stream(key.getClass().getDeclaredFields()).map(field -> field.getName()))
                .containsExactly("digest");
        assertThat(key.getClass().getRecordComponents()).isNullOrEmpty();
    }

    @Test
    void keyMustBeBoundedVisibleAscii() {
        assertThatThrownBy(() -> IdempotencyKey.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdempotencyKey.parse("contains space"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdempotencyKey.parse("a".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
