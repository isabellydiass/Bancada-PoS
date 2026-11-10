package dev.bancadapos.recorder;

import dev.bancadapos.common.Comparison;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class OutboxPublisher {
    private static final Logger LOG = LoggerFactory.getLogger(OutboxPublisher.class);

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final Counter failed;
    private final Counter published;

    OutboxPublisher(JdbcClient jdbc, KafkaTemplate<String, String> kafka, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.failed = meters.counter("comparison.outbox.failed");
        this.published = meters.counter("comparison.outbox.published");
    }

    @Scheduled(fixedDelayString = "${outbox.poll-delay-ms:300}")
    void publishNext() {
        UUID token = UUID.randomUUID();
        claim(token).ifPresent(event -> publish(event, token));
    }

    private Optional<Event> claim(UUID token) {
        return jdbc.sql("""
                WITH next_event AS (
                    SELECT id
                    FROM comparison_outbox
                    WHERE published_at IS NULL AND next_attempt_at <= now()
                    ORDER BY next_attempt_at, created_at, id
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE comparison_outbox AS outbox
                SET next_attempt_at = now() + interval '20 seconds',
                    claim_token = :token
                FROM next_event
                JOIN recorded_comparisons AS comparison ON comparison.id = next_event.id
                WHERE outbox.id = next_event.id
                RETURNING outbox.id, comparison.payload::text AS payload
                """)
                .param("token", token)
                .query((row, rowNumber) -> new Event(row.getObject("id", UUID.class), row.getString("payload")))
                .optional();
    }

    private void publish(Event event, UUID token) {
        try {
            kafka.send(Comparison.TOPIC, event.id().toString(), event.payload()).get(8, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return;
        } catch (ExecutionException | TimeoutException | KafkaException exception) {
            failed.increment();
            LOG.warn("Falha ao publicar comparisonId={}; a outbox tentará novamente.", event.id(), exception);
            return;
        }

        int updated = jdbc.sql("""
                UPDATE comparison_outbox
                SET published_at = now(), claim_token = NULL
                WHERE id = :id AND claim_token = :token
                """)
                .param("id", event.id())
                .param("token", token)
                .update();

        if (updated == 1) {
            published.increment();
            LOG.info("Comparação publicada comparisonId={}", event.id());
        }
    }

    private record Event(UUID id, String payload) {}
}
