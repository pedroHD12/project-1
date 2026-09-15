package br.com.mailflow.security;

import br.com.mailflow.config.WebExceptionHandler;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import static org.assertj.core.api.Assertions.*;

class PublicErrorTest {
    @Test void notFoundPageNeverDisplaysInternalExceptionDetails() {
        var model = new ExtendedModelMap();
        new WebExceptionHandler().handleNotFound(new EntityNotFoundException("private-identifier-and-secret"), model);
        assertThat(model.getAttribute("message").toString()).doesNotContain("private-identifier-and-secret");
    }
}
