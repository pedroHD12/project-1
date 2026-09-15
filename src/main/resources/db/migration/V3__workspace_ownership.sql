-- V1/V2 records retain their UUIDs and move together into the reserved private workspace.
create table workspaces (
    id uuid primary key,
    name varchar(160) not null,
    created_at timestamptz not null default current_timestamp
);
insert into workspaces (id, name) values ('00000000-0000-0000-0000-000000000001', 'Meu espaço');

create table app_users (
    id uuid primary key,
    workspace_id uuid not null unique references workspaces(id),
    name varchar(160) not null,
    email varchar(320) not null,
    normalized_email varchar(320) not null unique,
    password_hash varchar(100) not null,
    enabled boolean not null default true,
    failed_attempts integer not null default 0 check (failed_attempts >= 0),
    locked_until timestamptz,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint ck_user_email_normalized check (normalized_email = lower(btrim(normalized_email)))
);

do $$
declare
    table_name text;
    owned_tables text[] := array[
        'smtp_accounts','contacts','contact_groups','contact_group_members','tags','contact_tags',
        'email_templates','messages','message_recipients','attachments','recurrence_rules','schedules',
        'sequences','sequence_steps','delivery_jobs','delivery_attempts','blocked_recipients',
        'application_settings','audit_logs'
    ];
begin
    foreach table_name in array owned_tables loop
        execute format('alter table %I add column workspace_id uuid', table_name);
        execute format('update %I set workspace_id = %L::uuid', table_name, '00000000-0000-0000-0000-000000000001');
        execute format('alter table %I alter column workspace_id set not null', table_name);
        execute format('alter table %I add constraint %I foreign key (workspace_id) references workspaces(id)',
            table_name, 'fk_' || table_name || '_workspace');
        execute format('create index %I on %I (workspace_id)', 'idx_' || table_name || '_workspace', table_name);
        if table_name not in ('contact_group_members', 'contact_tags', 'application_settings') then
            execute format('alter table %I add constraint %I unique (workspace_id,id)',
                table_name, 'uq_' || table_name || '_workspace_id');
        end if;
    end loop;
end $$;

-- Keep the original delete behavior; additional composite FKs prevent cross-workspace links.
do $$
declare
    rel record;
begin
    for rel in
        select c.conname, c.conrelid::regclass as child_table,
               c.confrelid::regclass as parent_table, attr.attname as child_column
        from pg_constraint c
        join pg_attribute attr on attr.attrelid = c.conrelid and attr.attnum = c.conkey[1]
        where c.contype = 'f' and cardinality(c.conkey) = 1
          and c.connamespace = current_schema()::regnamespace
          and c.confrelid in (
            'smtp_accounts'::regclass, 'contacts'::regclass, 'contact_groups'::regclass,
            'tags'::regclass, 'email_templates'::regclass, 'messages'::regclass,
            'message_recipients'::regclass, 'recurrence_rules'::regclass,
            'sequences'::regclass, 'delivery_jobs'::regclass
          )
    loop
        execute format('alter table %s add constraint %I foreign key (workspace_id,%I) references %s(workspace_id,id) deferrable initially deferred',
            rel.child_table, 'ws_' || rel.conname, rel.child_column, rel.parent_table);
    end loop;
end $$;

drop index uq_contacts_email_normalized;
create unique index uq_contacts_email_normalized on contacts(workspace_id, lower(email));
drop index uq_smtp_accounts_name_normalized;
create unique index uq_smtp_accounts_name_normalized on smtp_accounts(workspace_id, lower(name));
alter table contact_groups drop constraint contact_groups_name_key;
alter table contact_groups add constraint uq_contact_groups_workspace_name unique(workspace_id, name);
alter table tags drop constraint tags_name_key;
alter table tags add constraint uq_tags_workspace_name unique(workspace_id, name);
alter table blocked_recipients drop constraint blocked_recipients_email_key;
-- Preserve V1's case-sensitive uniqueness: existing case variants must not abort the upgrade.
-- Future suppression lookups must compare case-insensitively without deleting legacy records.
create unique index uq_blocked_workspace_email on blocked_recipients(workspace_id, email);
alter table delivery_jobs drop constraint delivery_jobs_idempotency_key_key;
alter table delivery_jobs add constraint uq_delivery_workspace_idempotency unique(workspace_id, idempotency_key);
alter table application_settings drop constraint application_settings_pkey;
alter table application_settings add primary key(workspace_id, setting_key);

create index idx_contacts_workspace_display on contacts(workspace_id, display_name, email);
create index idx_templates_workspace_updated on email_templates(workspace_id, updated_at desc);
