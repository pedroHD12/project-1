package br.com.mailflow.settings.smtp;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SmtpAccountService {

    private final SmtpAccountRepository repository;
    private final SecretProtector secretProtector;
    private final br.com.mailflow.security.CurrentWorkspace workspace;
    private final jakarta.validation.Validator validator;

    public SmtpAccountService(SmtpAccountRepository repository, SecretProtector secretProtector, br.com.mailflow.security.CurrentWorkspace workspace, jakarta.validation.Validator validator) {
        this.repository = repository;
        this.secretProtector = secretProtector;
        this.workspace = workspace;
        this.validator = validator;
    }

    public void prepareForm(SmtpAccountForm form, UUID id) {
        var stored = id == null ? null : get(id);
        if (form.getDefaultSender() != null) form.setDefaultSender(form.getDefaultSender().strip());
        if (form.getName() == null || form.getName().isBlank()) {
            var email = form.getDefaultSender();
            form.setName(email == null ? "" : email.substring(0, Math.min(email.length(), 120)));
        }
        if ("GOOGLE".equals(form.getProvider()) || ("AUTO".equals(form.getProvider()) && form.isGmailAddress())) {
            form.setHost("smtp.gmail.com");
            form.setPort(587);
            form.setEncryptionMode(EncryptionMode.STARTTLS);
            form.setUsername(form.getDefaultSender());
        }
        // A saved credential may only be reused for the exact same destination and identity.
        form.setExistingSecret(stored != null && stored.hasProtectedSecret()
                && java.util.Objects.equals(stored.getHost(), form.getHost())
                && stored.getPort() == form.getPort()
                && stored.getEncryptionMode() == form.getEncryptionMode()
                && java.util.Objects.equals(stored.getUsername(), form.getUsername()));
    }

    private void validate(SmtpAccountForm form) {
        var errors = validator.validate(form);
        if (!errors.isEmpty()) throw new jakarta.validation.ConstraintViolationException(errors);
    }

    @Transactional(readOnly = true)
    public List<SmtpAccount> list() {
        return repository.findAllByWorkspaceIdOrderByNameAsc(workspace.id());
    }

    @Transactional(readOnly = true)
    public SmtpAccount get(UUID id) {
        return repository.findByIdAndWorkspaceId(id, workspace.id())
                .orElseThrow(() -> new EntityNotFoundException("Conta SMTP não encontrada."));
    }

    @Transactional
    public SmtpAccount create(SmtpAccountForm form) {
        prepareForm(form, null);
        validate(form);
        if (repository.existsByWorkspaceIdAndNameIgnoreCase(workspace.id(), form.getName().strip())) {
            throw new DuplicateSmtpAccountException();
        }

        var protectedSecret = org.springframework.util.StringUtils.hasText(form.getUsername()) ? protectWhenPresent(form.getPassword()) : null;
        var account = new SmtpAccount(
                workspace.id(),
                form.getName(),
                form.getHost(),
                form.getPort(),
                form.getUsername(),
                protectedSecret,
                form.getEncryptionMode(),
                form.getDefaultSender(),
                form.isEnabled()
        );
        return repository.save(account);
    }

    @Transactional
    public SmtpAccount update(UUID id, SmtpAccountForm form) {
        prepareForm(form, id);
        validate(form);
        var account = get(id);
        if (repository.existsByWorkspaceIdAndNameIgnoreCaseAndIdNot(workspace.id(), form.getName().strip(), id)) {
            throw new DuplicateSmtpAccountException();
        }

        account.update(
                form.getName(),
                form.getHost(),
                form.getPort(),
                form.getUsername(),
                form.getEncryptionMode(),
                form.getDefaultSender(),
                form.isEnabled()
        );
        if (!org.springframework.util.StringUtils.hasText(form.getUsername())) {
            account.changeSecret(null);
        } else if (form.getPassword() != null && !form.getPassword().isBlank()) {
            account.changeSecret(secretProtector.protect(form.getPassword()));
        }
        return account;
    }

    @Transactional(readOnly = true)
    public String revealSecret(SmtpAccount account) {
        account = get(account.getId());
        if (!account.hasProtectedSecret()) {
            return null;
        }
        return secretProtector.unprotect(account.getSecretReference());
    }

    private String protectWhenPresent(String password) {
        return password == null || password.isBlank() ? null : secretProtector.protect(password);
    }
}
