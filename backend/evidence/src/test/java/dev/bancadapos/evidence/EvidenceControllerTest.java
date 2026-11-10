package dev.bancadapos.evidence;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.bancadapos.common.Comparison;
import dev.bancadapos.common.Comparison.Run;
import dev.bancadapos.common.Comparison.Step;
import dev.bancadapos.common.Comparison.Verdict;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EvidenceControllerTest {

    private final ComparisonProjection projection = mock(ComparisonProjection.class);
    private final ComparisonReports reports = mock(ComparisonReports.class);
    private final JsonMapper mapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new EvidenceController(projection, reports))
            .setMessageConverters(new ByteArrayHttpMessageConverter(),
                    new MappingJackson2HttpMessageConverter(mapper))
            .build();

    @Test
    void returnsNotFoundWhileTheComparisonOrItsReportIsUnavailable() throws Exception {
        UUID id = UUID.randomUUID();
        when(projection.find(id)).thenReturn(Optional.empty());
        when(reports.load(id)).thenReturn(Optional.empty());

        mvc.perform(get("/api/comparisons/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(post("/api/comparisons/{id}/report", id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/comparisons/{id}/report", id)).andExpect(status().isNotFound());
    }

    @Test
    void returnsComparisonThenSavesAndDownloadsReport() throws Exception {
        UUID id = UUID.randomUUID();
        var baseline = new Run("1.4.0", Verdict.PASS, List.of("AUT-001"),
                List.of(new Step(3, "QUERY_ORIGINAL", "Consultou a operação original.")));
        var candidate = new Run("1.5.0", Verdict.REGRESSION, List.of("AUT-001", "AUT-002"),
                List.of(new Step(3, "START_NEW_PAYMENT", "Iniciou uma nova venda.")));
        var comparison = new Comparison(id, Instant.parse("2026-11-10T12:00:00Z"), 4290, 3, baseline, candidate);
        var report = mapper.writeValueAsString(comparison);
        when(projection.find(id)).thenReturn(Optional.of(comparison));
        when(reports.load(id)).thenReturn(Optional.of(report.getBytes(StandardCharsets.UTF_8)));

        mvc.perform(get("/api/comparisons/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().json(report));

        mvc.perform(post("/api/comparisons/{id}/report", id)).andExpect(status().isNoContent());
        verify(reports).save(comparison);

        mvc.perform(get("/api/comparisons/{id}/report", id))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"comparacao-" + id + ".json\""))
                .andExpect(content().json(report));
    }
}
