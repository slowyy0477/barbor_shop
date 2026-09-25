-- Durable notification intents. Delivery providers can retry independently.
create table if not exists notification_outbox (
    id uuid primary key,
    salon_id uuid not null,
    customer_id uuid not null,
    channel varchar(32) not null,
    template varchar(80) not null,
    payload varchar(4000) not null,
    status varchar(20) not null default 'PENDING',
    attempts integer not null default 0,
    next_attempt_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    sent_at timestamptz,
    last_error varchar(1000),
    version bigint not null default 0
);
create index if not exists ix_notification_outbox_due
    on notification_outbox(status, next_attempt_at, created_at);
