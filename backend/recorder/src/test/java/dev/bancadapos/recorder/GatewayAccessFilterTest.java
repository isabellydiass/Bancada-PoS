package dev.bancadapos.recorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.bancadapos.common.GatewayAccessFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class GatewayAccessFilterTest {

    @Test
    void blocksDirectApiAccessAndAllowsTheGateway() throws Exception {
        var filter = new GatewayAccessFilter(true, "demo-secret");

        var direct = request("/api/comparisons");
        var denied = new MockHttpServletResponse();
        filter.doFilter(direct, denied, new MockFilterChain());
        assertThat(denied.getStatus()).isEqualTo(403);

        var proxied = request("/api/comparisons");
        proxied.addHeader("X-Bancada-Gateway", "demo-secret");
        var chain = new MockFilterChain();
        filter.doFilter(proxied, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isSameAs(proxied);

        var healthCheck = request("/actuator/health");
        var healthChain = new MockFilterChain();
        filter.doFilter(healthCheck, new MockHttpServletResponse(), healthChain);
        assertThat(healthChain.getRequest()).isSameAs(healthCheck);
    }

    @Test
    void requiresTheConfiguredSecretOnCloudDeployments() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GatewayAccessFilter(true, ""));
    }

    private static MockHttpServletRequest request(String path) {
        var request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        return request;
    }
}
