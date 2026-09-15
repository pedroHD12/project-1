create table smtp_accounts (
    id uuid primary key,
    name varchar(120) not null,
    host varchar(255) not null,
    port integer not null check (port between 1 and 65535),
    username varchar(320),
    secret_reference varchar(500),
    encryption_mode varchar(20) not null check (encryption_mode in ('NONE', 'STARTTLS', 'TLS')),
    default_sender varchar(320),
    enabled boolean not null default true,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp
);

create table contacts (
    id uuid primary key,
    email varchar(320) not null,
    display_name varchar(160),
    company varchar(160),
    birthday date,
    status varchar(20) not null default 'ACTIVE'
        check (status in ('ACTIVE', 'BLOCKED', 'UNSUBSCRIBED')),
    custom_fields jsonb not null default '{}'::jsonb,
    notes text,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    constraint uq_contacts_email unique (email)
);

create table contact_groups (
    id uuid primary key,
    name varchar(160) not null unique,
    description text,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp
);

create table contact_group_members (
    group_id uuid not null references contact_groups(id) on delete cascade,
    contact_id uuid not null references contacts(id) on delete cascade,
    created_at timestamp with time zone not null default current_timestamp,
    primary key (group_id, contact_id)
);

create table tags (
    id uuid primary key,
    name varchar(80) not null unique,
    color varchar(20),
    created_at timestamp with time zone not null default current_timestamp
);

create table contact_tags (
    contact_id uuid not null references contacts(id) on delete cascade,
    tag_id uuid not null references tags(id) on delete cascade,
    created_at timestamp with time zone not null default current_timestamp,
    primary key (contact_id, tag_id)
);

create table email_templates (
    id uuid primary key,
    name varchar(160) not null,
    subject varchar(998) not null,
    body_text text,
    body_html text,
    active boolean not null default true,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    constraint ck_template_has_body check (body_text is not null or body_html is not null)
);

create table messages (
    id uuid primary key,
    template_id uuid references email_templates(id) on delete set null,
    smtp_account_id uuid references smtp_accounts(id) on delete restrict,
    name varchar(160) not null,
    subject varchar(998) not null,
    body_text text,
    body_html text,
    status varchar(24) not null default 'DRAFT'
        check (status in ('DRAFT', 'SCHEDULED', 'QUEUED', 'PROCESSING', 'COMPLETED', 'PAUSED', 'CANCELLED', 'FAILED')),
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    constraint ck_message_has_body check (body_text is not null or body_html is not null)
);

create table message_recipients (
    id uuid primary key,
    message_id uuid not null references messages(id) on delete cascade,
    contact_id uuid references contacts(id) on delete set null,
    email varchar(320) not null,
    display_name varchar(160),
    variables jsonb not null default '{}'::jsonb,
    status varchar(20) not null default 'PENDING'
        check (status in ('PENDING', 'QUEUED', 'SENT', 'FAILED', 'SKIPPED', 'CANCELLED')),
    created_at timestamp with time zone not null default current_timestamp,
    constraint uq_message_recipient unique (message_id, email)
);

create table attachments (
    id uuid primary key,
    message_id uuid not null references messages(id) on delete cascade,
    file_name varchar(255) not null,
    content_type varchar(160),
    file_path varchar(1000) not null,
    file_size bigint not null check (file_size >= 0),
    sha256 varchar(64),
    created_at timestamp with time zone not null default current_timestamp
);

create table recurrence_rules (
    id uuid primary key,
    recurrence_type varchar(20) not null
        check (recurrence_type in ('DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY', 'CUSTOM')),
    interval_value integer not null default 1 check (interval_value > 0),
    days_of_week smallint[],
    day_of_month smallint check (day_of_month between 1 and 31),
    month_of_year smallint check (month_of_year between 1 and 12),
    end_at timestamp with time zone,
    configuration jsonb not null default '{}'::jsonb,
    created_at timestamp with time zone not null default current_timestamp
);

create table schedules (
    id uuid primary key,
    message_id uuid not null references messages(id) on delete cascade,
    recurrence_rule_id uuid references recurrence_rules(id) on delete set null,
    scheduled_at timestamp with time zone not null,
    timezone varchar(80) not null default 'America/Sao_Paulo',
    missed_run_policy varchar(20) not null default 'ASK'
        check (missed_run_policy in ('SEND_LATER', 'SKIP', 'ASK')),
    enabled boolean not null default true,
    last_run_at timestamp with time zone,
    next_run_at timestamp with time zone,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp
);

create table sequences (
    id uuid primary key,
    name varchar(160) not null,
    description text,
    status varchar(20) not null default 'DRAFT'
        check (status in ('DRAFT', 'ACTIVE', 'PAUSED', 'ARCHIVED')),
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp
);

create table sequence_steps (
    id uuid primary key,
    sequence_id uuid not null references sequences(id) on delete cascade,
    template_id uuid not null references email_templates(id) on delete restrict,
    step_order integer not null check (step_order > 0),
    wait_amount integer not null default 0 check (wait_amount >= 0),
    wait_unit varchar(12) not null default 'DAYS'
        check (wait_unit in ('MINUTES', 'HOURS', 'DAYS', 'WEEKS')),
    allowed_start_time time,
    allowed_end_time time,
    created_at timestamp with time zone not null default current_timestamp,
    constraint uq_sequence_step_order unique (sequence_id, step_order)
);

create table delivery_jobs (
    id uuid primary key,
    message_recipient_id uuid not null references message_recipients(id) on delete cascade,
    idempotency_key varchar(160) not null unique,
    status varchar(20) not null default 'PENDING'
        check (status in ('PENDING', 'PROCESSING', 'SENT', 'RETRY', 'FAILED', 'CANCELLED')),
    priority smallint not null default 5 check (priority between 1 and 10),
    attempt_count integer not null default 0 check (attempt_count >= 0),
    max_attempts integer not null default 3 check (max_attempts > 0),
    available_at timestamp with time zone not null default current_timestamp,
    locked_at timestamp with time zone,
    locked_by varchar(160),
    completed_at timestamp with time zone,
    last_error_code varchar(80),
    last_error_message text,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp
);

create table delivery_attempts (
    id uuid primary key,
    delivery_job_id uuid not null references delivery_jobs(id) on delete cascade,
    attempt_number integer not null check (attempt_number > 0),
    started_at timestamp with time zone not null,
    finished_at timestamp with time zone,
    outcome varchar(20) not null check (outcome in ('PROCESSING', 'ACCEPTED', 'TEMPORARY_ERROR', 'PERMANENT_ERROR')),
    smtp_response_code varchar(20),
    error_message text,
    created_at timestamp with time zone not null default current_timestamp,
    constraint uq_delivery_attempt_number unique (delivery_job_id, attempt_number)
);

create table blocked_recipients (
    id uuid primary key,
    email varchar(320) not null unique,
    reason varchar(40) not null check (reason in ('MANUAL', 'UNSUBSCRIBED', 'BOUNCE', 'COMPLAINT')),
    notes text,
    created_at timestamp with time zone not null default current_timestamp
);

create table application_settings (
    setting_key varchar(160) primary key,
    setting_value jsonb not null,
    updated_at timestamp with time zone not null default current_timestamp
);

create table audit_logs (
    id uuid primary key,
    event_type varchar(80) not null,
    entity_type varchar(80),
    entity_id uuid,
    details jsonb not null default '{}'::jsonb,
    created_at timestamp with time zone not null default current_timestamp
);

create index idx_contacts_display_name on contacts (display_name);
create index idx_contacts_status on contacts (status);
create index idx_messages_status on messages (status);
create index idx_message_recipients_status on message_recipients (status);
create index idx_schedules_next_run on schedules (enabled, next_run_at);
create index idx_delivery_jobs_queue on delivery_jobs (status, available_at, priority);
create index idx_delivery_attempts_job on delivery_attempts (delivery_job_id, attempt_number);
create index idx_audit_logs_created_at on audit_logs (created_at desc);

