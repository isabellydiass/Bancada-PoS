package dev.bancadapos.recorder;

import dev.bancadapos.common.Comparison;
import dev.bancadapos.common.Comparison.Run;
import dev.bancadapos.common.Comparison.Step;
import dev.bancadapos.common.Comparison.Verdict;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

@Component
class PaymentScenario {

    private static final int AMOUNT_CENTS = 4290;

    Comparison compare(UUID id, Instant createdAt) {
        Run baseline = run("1.4.0", Recovery.QUERY_ORIGINAL);
        Run candidate = run("1.5.0", Recovery.START_NEW);
        return new Comparison(id, createdAt, AMOUNT_CENTS, firstDifference(baseline, candidate), baseline, candidate);
    }

    private Run run(String firmware, Recovery recovery) {
        var terminal = new SimulatedAcquirer();
        var steps = new ArrayList<Step>();

        String originalAuthorization = terminal.authorize();
        add(steps, "PAYMENT_APPROVED", "A credenciadora simulada aprovou a venda de R$ 42,90.");
        add(steps, "RESPONSE_TIMEOUT", "O terminal ficou sem resposta após a aprovação.");

        switch (recovery) {
            case QUERY_ORIGINAL -> {
                add(steps, "QUERY_ORIGINAL", "O terminal consultou o status da operação original.");
                terminal.query(originalAuthorization);
                add(steps, "ORIGINAL_CONFIRMED", "A consulta confirmou a aprovação original. Uma única autorização.");
            }
            case START_NEW -> {
                add(steps, "START_NEW_PAYMENT", "O terminal iniciou uma nova venda de R$ 42,90.");
                terminal.authorize();
                add(steps, "DUPLICATE_AUTHORIZATION", "A segunda venda também foi aprovada. Duas autorizações para a mesma compra.");
            }
        }

        List<String> authorizations = List.copyOf(terminal.authorizations);
        Verdict verdict = authorizations.size() == 1 ? Verdict.PASS : Verdict.REGRESSION;
        return new Run(firmware, verdict, authorizations, List.copyOf(steps));
    }

    private static Integer firstDifference(Run baseline, Run candidate) {
        List<Step> left = baseline.steps();
        List<Step> right = candidate.steps();
        int sharedLength = Math.min(left.size(), right.size());
        return IntStream.range(0, sharedLength)
                .filter(index -> !left.get(index).code().equals(right.get(index).code()))
                .mapToObj(index -> index + 1)
                .findFirst()
                .orElseGet(() -> left.size() == right.size() ? null : sharedLength + 1);
    }

    private static void add(List<Step> steps, String code, String description) {
        steps.add(new Step(steps.size() + 1, code, description));
    }

    private enum Recovery {
        QUERY_ORIGINAL, START_NEW
    }

    private static class SimulatedAcquirer {
        private final List<String> authorizations = new ArrayList<>();

        String authorize() {
            String authorizationId = "AUT-00" + (authorizations.size() + 1);
            authorizations.add(authorizationId);
            return authorizationId;
        }

        void query(String authorizationId) {
            if (!authorizations.contains(authorizationId)) {
                throw new IllegalStateException("Autorização simulada não encontrada.");
            }
        }
    }
}
