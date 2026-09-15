package br.com.mailflow.template;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateFormTest {

    @Test
    void requiresTextOrHtmlContent() {
        var form = new EmailTemplateForm();
        form.setName("Lembrete");
        form.setSubject("Um lembrete");

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(form);
            assertThat(violations)
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("contentPresent");
        }
    }
}

