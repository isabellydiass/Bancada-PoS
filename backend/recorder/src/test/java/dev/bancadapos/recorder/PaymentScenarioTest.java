package dev.bancadapos.recorder;

import static org.assertj.core.api.Assertions.assertThat;

import dev.bancadapos.common.Comparison.Step;
import dev.bancadapos.common.Comparison.Verdict;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentScenarioTest {

    @Test
    void identifiesTheFirstDifferentStepAndTheDuplicateAuthorization() {
        UUID id = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-11-10T12:00:00Z");

        var comparison = new PaymentScenario().compare(id, startedAt);

        assertThat(comparison.id()).isEqualTo(id);
        assertThat(comparison.createdAt()).isEqualTo(startedAt);
        assertThat(comparison.amountCents()).isEqualTo(4290);
        assertThat(comparison.firstDifference()).isEqualTo(3);
        assertThat(comparison.baseline().firmware()).isEqualTo("1.4.0");
        assertThat(comparison.baseline().verdict()).isEqualTo(Verdict.PASS);
        assertThat(comparison.baseline().authorizationIds()).containsExactly("AUT-001");
        assertThat(comparison.baseline().steps()).extracting(Step::order).containsExactly(1, 2, 3, 4);
        assertThat(comparison.baseline().steps()).extracting(Step::code)
                .containsExactly("PAYMENT_APPROVED", "RESPONSE_TIMEOUT", "QUERY_ORIGINAL", "ORIGINAL_CONFIRMED");
        assertThat(comparison.candidate().firmware()).isEqualTo("1.5.0");
        assertThat(comparison.candidate().verdict()).isEqualTo(Verdict.REGRESSION);
        assertThat(comparison.candidate().authorizationIds()).containsExactly("AUT-001", "AUT-002");
        assertThat(comparison.candidate().steps()).extracting(Step::order).containsExactly(1, 2, 3, 4);
        assertThat(comparison.candidate().steps()).extracting(Step::code)
                .containsExactly("PAYMENT_APPROVED", "RESPONSE_TIMEOUT", "START_NEW_PAYMENT", "DUPLICATE_AUTHORIZATION");
    }
}
