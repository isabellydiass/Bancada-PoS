package dev.bancadapos.evidence;

import dev.bancadapos.common.Comparison;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/comparisons/{id}")
class EvidenceController {

    private final ComparisonProjection projection;
    private final ComparisonReports reports;

    EvidenceController(ComparisonProjection projection, ComparisonReports reports) {
        this.projection = projection;
        this.reports = reports;
    }

    @GetMapping
    Comparison get(@PathVariable UUID id) {
        return projection.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comparação ainda indisponível."));
    }

    @PostMapping("/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void createReport(@PathVariable UUID id) {
        reports.save(get(id));
    }

    @GetMapping("/report")
    ResponseEntity<byte[]> download(@PathVariable UUID id) {
        byte[] content = reports.load(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Relatório ainda não gerado."));
        var disposition = ContentDisposition.attachment().filename("comparacao-" + id + ".json").build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(content);
    }
}
