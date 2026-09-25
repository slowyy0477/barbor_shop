alter table idempotency_records
    add column if not exists request_fingerprint varchar(128) not null default '';
