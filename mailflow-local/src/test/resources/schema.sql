create table if not exists workspaces (id uuid primary key, name varchar(160) not null,
    created_at timestamp with time zone default current_timestamp);
insert into workspaces (id, name) select '00000000-0000-0000-0000-000000000001', 'Meu espaço'
    where not exists (select 1 from workspaces where id = '00000000-0000-0000-0000-000000000001');
create table if not exists app_users (id uuid primary key, workspace_id uuid not null unique references workspaces(id),
    name varchar(160) not null, email varchar(320) not null, normalized_email varchar(320) not null unique,
    password_hash varchar(100) not null, enabled boolean not null default true,
    failed_attempts integer not null default 0, locked_until timestamp with time zone,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp);
create table if not exists schedules (id uuid primary key, workspace_id uuid not null, enabled boolean default true);
create table if not exists delivery_jobs (id uuid primary key, workspace_id uuid not null, status varchar(20));

