package dev.bancadapos.recorder;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/comparisons")
class RecorderController {

    private final ComparisonRecorder recorder;
    private final Counter recorded;

    RecorderController(ComparisonRecorder recorder, MeterRegistry meters) {
        this.recorder = recorder;
        this.recorded = meters.counter("comparison.recorded");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    AcceptedComparison record() {
        UUID id = recorder.record();
        recorded.increment();
        return new AcceptedComparison(id);
    }

    record AcceptedComparison(UUID id) {}
}
