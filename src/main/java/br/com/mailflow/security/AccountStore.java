package br.com.mailflow.security;

import jakarta.validation.Validator;
import br.com.mailflow.config.AppRuntimeProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AccountStore {
    public static final UUID INITIAL_WORKSPACE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final Validator validator;
    private final TransactionTemplate transactions;
    private final String dummyHash;
    private final AppRuntimeProperties runtime;
    private final OwnerSetupProperties owner;

    public AccountStore(JdbcClient jdbc, PasswordEncoder encoder, Validator validator,
                        PlatformTransactionManager transactionManager, AppRuntimeProperties runtime,
                        OwnerSetupProperties owner) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.validator = validator;
        transactions = new TransactionTemplate(transactionManager);
        dummyHash = encoder.encode(UUID.randomUUID().toString());
        this.runtime = runtime;
        this.owner = owner;
    }
    public boolean isRegistrationOpen() {
        return !runtime.isCloud() && jdbc.sql("select count(*) from app_users").query(Long.class).single() == 0;
    }
    public boolean isCloudSetupOpen() {
        return runtime.isCloud() && jdbc.sql("select count(*) from app_users").query(Long.class).single() == 0;
    }
    public boolean register(RegistrationForm form) {
        if (!validator.validate(form).isEmpty()) throw new IllegalArgumentException("Cadastro inválido.");
        var hash = encoder.encode(form.getPassword());
        return Boolean.TRUE.equals(transactions.execute(status -> {
            jdbc.sql("select id from workspaces where id = ? for update")
                    .param(INITIAL_WORKSPACE).query(UUID.class).single();
            if (!isRegistrationOpen()) return false;
            jdbc.sql("""
                    insert into app_users (id, workspace_id, name, email, normalized_email, password_hash)
                    values (?, ?, ?, ?, ?, ?)
                    """).params(UUID.randomUUID(), INITIAL_WORKSPACE, form.getName().strip(),
                    form.getEmail().strip(), normalize(form.getEmail()), hash).update();
            return true;
        }));
    }
    public boolean createInitialOwner(RegistrationForm form, char[] setupToken) {
        if (form == null || !runtime.isCloud() || !sameOwnerToken(setupToken) || !normalize(form.getEmail()).equals(owner.email())
                || !validator.validate(form).isEmpty()) {
            return false;
        }
        var hash = encoder.encode(form.getPassword());
        return Boolean.TRUE.equals(transactions.execute(status -> {
            jdbc.sql("select id from workspaces where id = ? for update")
                    .param(INITIAL_WORKSPACE).query(UUID.class).single();
            if (!isCloudSetupOpen()) {
                return false;
            }
            jdbc.sql("""
                    insert into app_users (id, workspace_id, name, email, normalized_email, password_hash)
                    values (?, ?, ?, ?, ?, ?)
                    """).params(UUID.randomUUID(), INITIAL_WORKSPACE, form.getName().strip(),
                    form.getEmail().strip(), normalize(form.getEmail()), hash).update();
            return true;
        }));
    }
    public AccountPrincipal authenticate(String email, String password) {
        var safePassword = password == null ? "" : password;
        if (email == null || email.length() > 320 || safePassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            encoder.matches("invalid", dummyHash);
            return null;
        }
        // Return failures rather than throwing so lockout updates commit.
        return transactions.execute(status -> {
            var row = jdbc.sql("""
                    select id, workspace_id, normalized_email, password_hash, failed_attempts, locked_until, enabled
                    from app_users where normalized_email = ? for update
                    """).param(normalize(email)).query((rs, index) -> new LoginRow(
                    rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class),
                    rs.getString("normalized_email"), rs.getString("password_hash"),
                    rs.getInt("failed_attempts"), rs.getTimestamp("locked_until"), rs.getBoolean("enabled")))
                    .optional();
            if (row.isEmpty()) { encoder.matches(safePassword, dummyHash); return null; }
            var account = row.get();
            var now = Instant.now();
            var matches = encoder.matches(safePassword, account.hash());
            if (!account.enabled() || (account.lockedUntil() != null && account.lockedUntil().toInstant().isAfter(now)))
                return null;
            if (!matches) {
                var failures = account.lockedUntil() == null ? account.failures() + 1 : 1;
                jdbc.sql("update app_users set failed_attempts = ?, locked_until = ?, updated_at = current_timestamp where id = ?")
                        .param(1, failures).param(2, failures >= 5 ? Timestamp.from(now.plusSeconds(300)) : null, java.sql.Types.TIMESTAMP)
                        .param(3, account.id()).update();
                return null;
            }
            jdbc.sql("update app_users set failed_attempts = 0, locked_until = null, updated_at = current_timestamp where id = ?")
                    .param(account.id()).update();
            return new AccountPrincipal(account.id(), account.workspace(), account.email());
        });
    }
    private static String normalize(String v) { return v == null ? "" : v.strip().toLowerCase(Locale.ROOT); }
    private boolean sameOwnerToken(char[] provided) {
        if (provided == null || provided.length > 512 || owner.setupToken().isEmpty()) {
            return false;
        }
        var expected = owner.setupToken().getBytes(StandardCharsets.UTF_8);
        var candidate = new String(provided).getBytes(StandardCharsets.UTF_8);
        try {
            return java.security.MessageDigest.isEqual(expected, candidate);
        } finally {
            java.util.Arrays.fill(candidate, (byte) 0);
        }
    }
    private record LoginRow(UUID id, UUID workspace, String email, String hash, int failures,
                            Timestamp lockedUntil, boolean enabled) { }
}
