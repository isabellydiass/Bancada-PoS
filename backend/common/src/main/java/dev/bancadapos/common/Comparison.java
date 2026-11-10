package dev.bancadapos.common;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Comparison(
        UUID id,
        Instant createdAt,
        int amountCents,
        Integer firstDifference,
        Run baseline,
        Run candidate
) {
    public static final String TOPIC = "comparison.recorded.v1";

    public record Run(String firmware, Verdict verdict, List<String> authorizationIds, List<Step> steps) {}

    public record Step(int order, String code, String description) {}

    public enum Verdict {
        PASS, REGRESSION
    }
}
