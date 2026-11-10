package dev.bancadapos.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.bancadapos.common.Comparison;
import dev.bancadapos.common.Comparison.Run;
import dev.bancadapos.common.Comparison.Verdict;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class ComparisonConsumerTest {

    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final ComparisonProjection projection = mock(ComparisonProjection.class);

    @Test
    void distinguishesNewProjectionFromRedelivery() throws Exception {
        UUID id = UUID.randomUUID();
        var baseline = new Run("1.4.0", Verdict.PASS, List.of("AUT-001"), List.of());
        var candidate = new Run("1.5.0", Verdict.REGRESSION, List.of("AUT-001", "AUT-002"), List.of());
        var comparison = new Comparison(id, Instant.parse("2026-11-10T12:00:00Z"), 4290, 3, baseline, candidate);
        String json = mapper.writeValueAsString(comparison);
        when(projection.store(any(Comparison.class))).thenReturn(true, false);

        var meters = new SimpleMeterRegistry();
        var consumer = new ComparisonConsumer(projection, mapper, meters);
        consumer.receive(new ConsumerRecord<>(Comparison.TOPIC, 0, 0, id.toString(), json));
        consumer.receive(new ConsumerRecord<>(Comparison.TOPIC, 0, 1, id.toString(), json));

        assertThat(meters.counter("comparison.projected").count()).isEqualTo(1);
        assertThat(meters.counter("comparison.duplicate").count()).isEqualTo(1);
        assertThat(meters.timer("comparison.projection.delay").count()).isEqualTo(1);
    }

    @Test
    void recordsZeroDelayWhenTheProducerClockIsAhead() throws Exception {
        UUID id = UUID.randomUUID();
        var run = new Run("1.4.0", Verdict.PASS, List.of("AUT-001"), List.of());
        Instant future = Instant.now().plus(1, ChronoUnit.DAYS);
        var comparison = new Comparison(id, future, 4290, 3, run, run);
        when(projection.store(comparison)).thenReturn(true);

        var meters = new SimpleMeterRegistry();
        var consumer = new ComparisonConsumer(projection, mapper, meters);
        consumer.receive(new ConsumerRecord<>(Comparison.TOPIC, 0, 0, id.toString(), mapper.writeValueAsString(comparison)));

        var delay = meters.timer("comparison.projection.delay");
        assertThat(delay.count()).isEqualTo(1);
        assertThat(delay.totalTime(TimeUnit.NANOSECONDS)).isZero();
    }

    @Test
    void rejectsMalformedEventsBeforeWritingTheProjection() {
        var meters = new SimpleMeterRegistry();
        var consumer = new ComparisonConsumer(projection, mapper, meters);
        var record = new ConsumerRecord<>(Comparison.TOPIC, 0, 0, "invalid", "{}");

        assertThatThrownBy(() -> consumer.receive(record)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(projection);
    }
}
