-- Legacy messages/jobs are deliberately not enrolled in automatic delivery.
alter table messages add column new_flow boolean not null default false;
alter table messages add column confirmed_at timestamp with time zone;
alter table messages add column from_email varchar(320);
alter table messages add column account_fingerprint varchar(64);
alter table messages add column delivery_mode varchar(12) not null default 'NOW'
    check (delivery_mode in ('NOW','ONCE','DAILY','WEEKLY'));
alter table messages add column occurrence_count integer not null default 1 check (occurrence_count between 1 and 30);
alter table messages add column planned_at timestamp with time zone;
alter table messages add column delivery_timezone varchar(80) not null default 'America/Sao_Paulo';
alter table messages add column test_of uuid;
alter table messages add constraint uq_message_self_test unique (workspace_id, test_of);
alter table messages add constraint fk_message_self_test foreign key (test_of, workspace_id) references messages(id, workspace_id);

alter table message_recipients add column rendered_subject varchar(998);
alter table message_recipients add column rendered_text text;
alter table message_recipients add column rendered_html text;

alter table delivery_jobs drop constraint delivery_jobs_status_check;
alter table delivery_jobs add constraint delivery_jobs_status_check check (status in
    ('PENDING','PROCESSING','SENT','RETRY','FAILED','CANCELLED','UNKNOWN','MISSED','SKIPPED'));
alter table delivery_attempts drop constraint delivery_attempts_outcome_check;
alter table delivery_attempts add constraint delivery_attempts_outcome_check check (outcome in
    ('PROCESSING','ACCEPTED','TEMPORARY_ERROR','PERMANENT_ERROR','UNKNOWN'));

-- Serializes claims across processes; attempts themselves remain outside DB transactions.
create table delivery_worker_guard (id integer primary key check(id=1));
insert into delivery_worker_guard(id) values (1);
create index idx_delivery_attempts_rate on delivery_attempts (started_at desc);
create index idx_messages_new_flow on messages (workspace_id, created_at desc) where new_flow;
create index idx_blocked_recipient_normalized on blocked_recipients (workspace_id, lower(email));
