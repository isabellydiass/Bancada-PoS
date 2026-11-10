package dev.bancadapos.recorder;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RecorderControllerTest {

    @Test
    void acceptsAnEmptyPostAndReturnsItsComparisonId() throws Exception {
        UUID id = UUID.randomUUID();
        ComparisonRecorder recorder = org.mockito.Mockito.mock(ComparisonRecorder.class);
        given(recorder.record()).willReturn(id);

        var meters = new SimpleMeterRegistry();
        MockMvcBuilders.standaloneSetup(new RecorderController(recorder, meters)).build()
                .perform(post("/api/comparisons"))
                .andExpect(status().isAccepted())
                .andExpect(content().json("{\"id\": \"" + id + "\"}"));
    }
}
