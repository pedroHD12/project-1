package br.com.mailflow.settings.smtp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledOnOs(OS.WINDOWS)
class WindowsDpapiSecretProtectorTest {

    @Test
    void protectsAndUnprotectsForCurrentWindowsUser() {
        var protector = new WindowsDpapiSecretProtector();

        var encrypted = protector.protect("senha de teste local");

        assertThat(encrypted).startsWith("dpapi:").doesNotContain("senha de teste local");
        assertThat(protector.unprotect(encrypted)).isEqualTo("senha de teste local");
    }
}

