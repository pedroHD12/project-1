package br.com.mailflow.contact;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock
    private ContactRepository repository;

    private ContactService service;

    @BeforeEach
    void setUp() {
        service = new ContactService(repository, new br.com.mailflow.security.CurrentWorkspace() {
            @Override public java.util.UUID id() { return br.com.mailflow.security.AccountStore.INITIAL_WORKSPACE; }
        });
    }

    @Test
    void createsContactWithNormalizedEmail() {
        var form = new ContactForm();
        form.setEmail("  Pedro@Example.COM ");
        form.setDisplayName("Pedro");
        when(repository.save(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(form);

        var captor = ArgumentCaptor.forClass(Contact.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("pedro@example.com");
    }

    @Test
    void rejectsDuplicateEmail() {
        var form = new ContactForm();
        form.setEmail("pedro@example.com");
        when(repository.existsByWorkspaceIdAndEmailIgnoreCase(br.com.mailflow.security.AccountStore.INITIAL_WORKSPACE, "pedro@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(form))
                .isInstanceOf(DuplicateContactException.class);
    }
}
