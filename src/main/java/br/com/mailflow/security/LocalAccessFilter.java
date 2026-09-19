package br.com.mailflow.security;

import br.com.mailflow.config.AppRuntimeProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Set;

/** This release is local-only, including when a proxy or hostile DNS name reaches loopback. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocalAccessFilter extends OncePerRequestFilter {
    private static final Set<String> LOCAL_ADDRESSES = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");
    private final AppRuntimeProperties runtime;

    public LocalAccessFilter(AppRuntimeProperties runtime) {
        this.runtime = runtime;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (runtime.isCloud()) {
            chain.doFilter(request, response);
            return;
        }
        if (!LOCAL_ADDRESSES.contains(request.getRemoteAddr())
                || !LOCAL_HOSTS.contains(request.getServerName().toLowerCase(java.util.Locale.ROOT))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Acesso permitido somente neste computador, usando localhost.");
            return;
        }
        chain.doFilter(request, response);
    }
}
