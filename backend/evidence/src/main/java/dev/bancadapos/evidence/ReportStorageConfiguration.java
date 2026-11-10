package dev.bancadapos.evidence;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration(proxyBeanMethods = false)
class ReportStorageConfiguration {

    @Bean(destroyMethod = "close")
    S3Client reportStorageClient(
            @Value("${report.region}") String region,
            @Value("${report.s3-endpoint}") String endpoint,
            @Value("${report.s3-mock}") boolean mock
    ) {
        if (mock && endpoint.isBlank()) {
            throw new IllegalArgumentException("REPORT_S3_ENDPOINT deve ser definido no modo simulado.");
        }

        var builder = S3Client.builder()
                .region(Region.of(region))
                .overrideConfiguration(config -> config
                        .apiCallTimeout(Duration.ofSeconds(8))
                        .apiCallAttemptTimeout(Duration.ofSeconds(3)));
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .chunkedEncodingEnabled(false)
                            .build())
                    .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED);
        }
        if (mock) {
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")));
        }

        return builder.build();
    }
}
