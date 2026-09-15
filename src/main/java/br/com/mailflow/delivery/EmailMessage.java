package br.com.mailflow.delivery;

import java.util.List;

public record EmailMessage(
        String from,
        List<String> recipients,
        String subject,
        String textBody,
        String htmlBody
) {
}

