package br.com.mailflow.template;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EmailTemplateService {

    private final EmailTemplateRepository repository;
    private final br.com.mailflow.security.CurrentWorkspace workspace;

    public EmailTemplateService(EmailTemplateRepository repository, br.com.mailflow.security.CurrentWorkspace workspace) {
        this.repository = repository;
        this.workspace = workspace;
    }

    @Transactional(readOnly = true)
    public List<EmailTemplate> list() {
        return repository.findTop100ByWorkspaceIdOrderByUpdatedAtDesc(workspace.id());
    }

    @Transactional(readOnly = true)
    public EmailTemplate get(UUID id) {
        return repository.findByIdAndWorkspaceId(id, workspace.id())
                .orElseThrow(() -> new EntityNotFoundException("Template não encontrado."));
    }

    @Transactional
    public EmailTemplate create(EmailTemplateForm form) {
        return repository.save(new EmailTemplate(
                workspace.id(),
                form.getName(),
                form.getSubject(),
                form.getBodyText(),
                form.getBodyHtml()
        ));
    }

    @Transactional
    public EmailTemplate update(UUID id, EmailTemplateForm form) {
        var template = get(id);
        template.update(form.getName(), form.getSubject(), form.getBodyText(), form.getBodyHtml());
        return template;
    }

    @Transactional
    public void changeActive(UUID id, boolean active) {
        var template = get(id);
        if (active) {
            template.restore();
        } else {
            template.archive();
        }
    }
}
