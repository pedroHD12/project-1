alter table delivery_jobs drop constraint delivery_jobs_status_check;
alter table delivery_jobs add constraint delivery_jobs_status_check check (status in
    ('PENDING','PROCESSING','SENT','SENT_LATE','RETRY','FAILED','CANCELLED','UNKNOWN','MISSED','SKIPPED'));
