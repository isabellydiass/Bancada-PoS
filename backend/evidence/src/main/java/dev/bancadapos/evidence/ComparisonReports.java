package dev.bancadapos.evidence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bancadapos.common.Comparison;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
class ComparisonReports {
    private static final Logger LOG = LoggerFactory.getLogger(ComparisonReports.class);

    private final S3Client storage;
    private final ObjectMapper mapper;
    private final String bucket;
    private final Counter stored;

    ComparisonReports(
            S3Client storage,
            ObjectMapper mapper,
            @Value("${report.bucket}") String bucket,
            MeterRegistry meters
    ) {
        this.storage = storage;
        this.mapper = mapper;
        this.bucket = bucket;
        this.stored = meters.counter("comparison.report.stored");
    }

    void save(Comparison comparison) {
        byte[] json;
        try {
            json = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(comparison);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Falha ao serializar o relatório.", exception);
        }

        var request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key(comparison.id()))
                .contentType("application/json")
                .build();
        try {
            storage.putObject(request, RequestBody.fromBytes(json));
            stored.increment();
            LOG.info("Relatório salvo comparisonId={}", comparison.id());
        } catch (SdkException exception) {
            throw unavailable(comparison.id(), exception);
        }
    }

    Optional<byte[]> load(UUID id) {
        var request = GetObjectRequest.builder().bucket(bucket).key(key(id)).build();
        try {
            return Optional.of(storage.getObjectAsBytes(request).asByteArray());
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404 && exception.awsErrorDetails() != null
                    && "NoSuchKey".equals(exception.awsErrorDetails().errorCode())) {
                return Optional.empty();
            }
            throw unavailable(id, exception);
        } catch (SdkException exception) {
            throw unavailable(id, exception);
        }
    }

    private static String key(UUID id) {
        return "comparisons/" + id + ".json";
    }

    private static ResponseStatusException unavailable(UUID id, SdkException exception) {
        LOG.warn("Armazenamento de relatório indisponível comparisonId={}", id, exception);
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Armazenamento de relatórios indisponível.");
    }
}
