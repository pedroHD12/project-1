package br.com.mailflow.template;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateVariableExtractorTest {

    @Test
    void extractsUniqueVariablesInStableOrder() {
        var content = "Olá {{ nome }}, sua empresa é {{empresa}}. Até logo, {{nome}}!";

        var variables = TemplateVariableExtractor.extractFrom(content);

        assertThat(variables).containsExactly("empresa", "nome");
    }

    @Test
    void returnsEmptySetForEmptyContent() {
        assertThat(TemplateVariableExtractor.extractFrom("  ")).isEqualTo(Set.of());
    }
}

