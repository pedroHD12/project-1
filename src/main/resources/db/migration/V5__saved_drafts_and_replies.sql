create table saved_drafts (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    account_id uuid,
    contact_ids varchar(739) not null default '',
    subject varchar(998) not null default '',
    body_text text not null default '',
    body_html text not null default '',
    delivery_mode varchar(12) not null check (delivery_mode in ('NOW','ONCE','DAILY','WEEKLY')),
    scheduled_at varchar(30) not null default '',
    delivery_timezone varchar(80) not null,
    occurrence_count integer not null check (occurrence_count between 1 and 30),
    version bigint not null default 0,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp
);
create index idx_saved_drafts_workspace on saved_drafts(workspace_id, updated_at desc, id);

create table mailbox_connections (
    account_id uuid not null,
    workspace_id uuid not null,
    enabled boolean not null default false,
    account_fingerprint varchar(64) not null,
    uid_validity bigint not null default 0,
    last_uid bigint not null default 0,
    last_checked_at timestamp with time zone,
    last_error_code varchar(30),
    locked_until timestamp with time zone,
    lock_token uuid,
    primary key (account_id,workspace_id),
    foreign key (account_id,workspace_id) references smtp_accounts(id,workspace_id)
);
create table received_replies (
    id uuid primary key,
    workspace_id uuid not null,
    account_id uuid not null,
    message_id uuid not null,
    incoming_key varchar(64) not null,
    from_email varchar(320) not null,
    subject varchar(998) not null,
    body_text text not null,
    received_at timestamp with time zone not null,
    removed boolean not null default false,
    unique (account_id,workspace_id,incoming_key),
    foreign key (account_id,workspace_id) references smtp_accounts(id,workspace_id),
    foreign key (message_id,workspace_id) references messages(id,workspace_id) on delete cascade
);
create index idx_received_replies_workspace on received_replies(workspace_id,received_at desc,id);
