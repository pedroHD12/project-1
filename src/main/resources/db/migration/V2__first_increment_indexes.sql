-- Normalização lógica: endereços de e-mail e nomes de contas não diferenciam maiúsculas.
alter table contacts drop constraint if exists uq_contacts_email;

create unique index uq_contacts_email_normalized
    on contacts (lower(email));

create unique index uq_smtp_accounts_name_normalized
    on smtp_accounts (lower(name));

alter table smtp_accounts
    alter column secret_reference type text;

-- O PostgreSQL não cria índices automaticamente para chaves estrangeiras.
-- Índices cujo prefixo já é coberto por PK/UNIQUE não são repetidos.
create index idx_contact_group_members_contact_id
    on contact_group_members (contact_id);

create index idx_contact_tags_tag_id
    on contact_tags (tag_id);

create index idx_messages_template_id
    on messages (template_id);

create index idx_messages_smtp_account_id
    on messages (smtp_account_id);

create index idx_message_recipients_contact_id
    on message_recipients (contact_id);

create index idx_attachments_message_id
    on attachments (message_id);

create index idx_schedules_message_id
    on schedules (message_id);

create index idx_schedules_recurrence_rule_id
    on schedules (recurrence_rule_id);

create index idx_sequence_steps_template_id
    on sequence_steps (template_id);

create index idx_delivery_jobs_message_recipient_id
    on delivery_jobs (message_recipient_id);

-- Índices pequenos e direcionados para as consultas mais frequentes.
drop index if exists idx_schedules_next_run;
create index idx_schedules_due
    on schedules (next_run_at, id)
    where enabled = true and next_run_at is not null;

drop index if exists idx_delivery_jobs_queue;
create index idx_delivery_jobs_ready
    on delivery_jobs (priority desc, available_at, id)
    where status in ('PENDING', 'RETRY');

create index idx_contacts_active_name
    on contacts (lower(coalesce(display_name, email)), id)
    where status = 'ACTIVE';
