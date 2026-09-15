package br.com.mailflow.contact;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

@Service
public class ContactService {

    private final ContactRepository repository;
    private final br.com.mailflow.security.CurrentWorkspace workspace;

    public ContactService(ContactRepository repository, br.com.mailflow.security.CurrentWorkspace workspace) {
        this.repository = repository;
        this.workspace = workspace;
    }

    @Transactional(readOnly = true)
    public List<Contact> list(String query) {
        if (query == null || query.isBlank()) {
            return repository.findTop100ByWorkspaceIdOrderByDisplayNameAscEmailAsc(workspace.id());
        }
        return repository.search(workspace.id(), query.strip(), PageRequest.of(0, 100));
    }

    @Transactional(readOnly = true)
    public Contact get(UUID id) {
        return repository.findByIdAndWorkspaceId(id, workspace.id())
                .orElseThrow(() -> new EntityNotFoundException("Contato não encontrado."));
    }

    @Transactional
    public Contact create(ContactForm form) {
        var normalizedEmail = normalizeEmail(form.getEmail());
        if (repository.existsByWorkspaceIdAndEmailIgnoreCase(workspace.id(), normalizedEmail)) {
            throw new DuplicateContactException();
        }

        var contact = new Contact(
                workspace.id(),
                normalizedEmail,
                form.getDisplayName(),
                form.getCompany(),
                form.getBirthday(),
                form.getNotes()
        );
        return repository.save(contact);
    }

    @Transactional
    public Contact update(UUID id, ContactForm form) {
        var contact = get(id);
        var normalizedEmail = normalizeEmail(form.getEmail());
        if (repository.existsByWorkspaceIdAndEmailIgnoreCaseAndIdNot(workspace.id(), normalizedEmail, id)) {
            throw new DuplicateContactException();
        }

        contact.update(
                normalizedEmail,
                form.getDisplayName(),
                form.getCompany(),
                form.getBirthday(),
                form.getNotes()
        );
        return contact;
    }

    @Transactional
    public void changeStatus(UUID id, ContactStatus status) {
        get(id).changeStatus(status);
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(get(id));
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(java.util.Locale.ROOT);
    }
}
