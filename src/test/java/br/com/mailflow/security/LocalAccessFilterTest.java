package br.com.mailflow.security;

import br.com.mailflow.config.AppRuntimeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAccessFilterTest {

    @Test
    void cloudModeAllowsRequestReachedThroughTheHttpsProxy() throws Exception {
        var request = new MockHttpServletRequest("GET", "/login");
        request.setRemoteAddr("127.0.0.1");
        request.setServerName("mailflow.example.com");
        var response = new MockHttpServletResponse();

        new LocalAccessFilter(new AppRuntimeProperties("cloud", "jdbc:postgresql://db?sslmode=verify-full"))
                .doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void localModeStillRejectsNonLocalHostHeader() throws Exception {
        var request = new MockHttpServletRequest("GET", "/login");
        request.setRemoteAddr("127.0.0.1");
        request.setServerName("mailflow.example.com");
        var response = new MockHttpServletResponse();

        new LocalAccessFilter(new AppRuntimeProperties("local", "jdbc:postgresql://localhost/mailflow"))
                .doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }
}
