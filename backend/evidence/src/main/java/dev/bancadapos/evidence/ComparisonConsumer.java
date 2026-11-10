package dev.bancadapos.evidence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bancadapos.common.Comparison;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;

@Component
class ComparisonConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(ComparisonConsumer.class);

    private final ComparisonProjection projection;
    private final ObjectMapper mapper;
    private final Counter projected;
    private final Counter duplicate;
    private final Timer projectionDelay;

    ComparisonConsumer(ComparisonProjection projection, ObjectMapper mapper, MeterRegistry meters) {
        this.projection = projection;
        this.mapper = mapper;
        this.projected = meters.counter("comparison.projected");
        this.duplicate = meters.counter("comparison.duplicate");
        this.projectionDelay = meters.timer("comparison.projection.delay");
    }

    @KafkaListener(topics = Comparison.TOPIC)
    void receive(ConsumerRecord<String, String> record) {
        Comparison comparison = decode(record);
        if (projection.store(comparison)) {
            projected.increment();
            Duration delay = Duration.between(comparison.createdAt(), Instant.now());
            projectionDelay.record(delay.isNegative() ? Duration.ZERO : delay);
            LOG.info("Comparação disponível comparisonId={}", comparison.id());
        } else {
            duplicate.increment();
            LOG.info("Reentrega ignorada comparisonId={}", comparison.id());
        }
    }

    private Comparison decode(ConsumerRecord<String, String> record) {
        try {
            Comparison comparison = mapper.readValue(record.value(), Comparison.class);
            if (comparison == null || comparison.id() == null || !comparison.id().toString().equals(record.key())
                    || comparison.createdAt() == null || comparison.baseline() == null || comparison.candidate() == null) {
                throw new IllegalArgumentException("Evento de comparação inválido.");
            }
            return comparison;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Evento de comparação não pôde ser lido.", exception);
        }
    }

    @Bean
    static DefaultErrorHandler comparisonErrorHandler() {
        var handler = new DefaultErrorHandler(new FixedBackOff(1000, FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
