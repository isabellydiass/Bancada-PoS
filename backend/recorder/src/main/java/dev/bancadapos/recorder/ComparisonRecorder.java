package dev.bancadapos.recorder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bancadapos.common.Comparison;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ComparisonRecorder {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final PaymentScenario scenario;

    ComparisonRecorder(JdbcClient jdbc, ObjectMapper mapper, PaymentScenario scenario) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.scenario = scenario;
    }

    @Transactional
    public UUID record() {
        Comparison comparison = scenario.compare(UUID.randomUUID(), Instant.now().truncatedTo(ChronoUnit.MILLIS));
        var createdAt = comparison.createdAt().atOffset(ZoneOffset.UTC);

        jdbc.sql("""
                INSERT INTO recorded_comparisons (id, created_at, payload)
                VALUES (:id, :createdAt, CAST(:payload AS jsonb))
                """)
                .param("id", comparison.id())
                .param("createdAt", createdAt)
                .param("payload", serialize(comparison))
                .update();

        jdbc.sql("""
                INSERT INTO comparison_outbox (id, created_at)
                VALUES (:id, :createdAt)
                """)
                .param("id", comparison.id())
                .param("createdAt", createdAt)
                .update();

        return comparison.id();
    }

    private String serialize(Comparison comparison) {
        try {
            return mapper.writeValueAsString(comparison);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Falha ao serializar a comparação.", exception);
        }
    }
}
