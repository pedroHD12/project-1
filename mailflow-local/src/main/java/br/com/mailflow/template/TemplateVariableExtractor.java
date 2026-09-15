package br.com.mailflow.template;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

public final class TemplateVariableExtractor {

    private static final Pattern VARIABLE =
            Pattern.compile("\\{\\{\\s*([a-zA-Z][a-zA-Z0-9_.]*)\\s*}}", Pattern.MULTILINE);

    private TemplateVariableExtractor() {
    }

    public static Set<String> extractFrom(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptySet();
        }

        var variables = new TreeSet<String>();
        var matcher = VARIABLE.matcher(content);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return Collections.unmodifiableSet(variables);
    }
}

