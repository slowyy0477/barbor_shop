-- Self-service signup limits. The salon asked for one account per phone and a
-- cap of a few accounts per internet connection, because SMS verification is
-- switched off and the mobile number plus a strong password is now the only
-- sign-in. Only digests are stored: the raw device id and IP address never
-- reach PostgreSQL.
create table if not exists signup_guards (
    id uuid primary key,
    salon_id uuid not null,
    device_hash varchar(128),
    ip_hash varchar(128) not null,
    customer_id uuid not null,
    created_at timestamptz not null default now(),
    version bigint not null default 0
);
create unique index if not exists uq_signup_guard_device
    on signup_guards(salon_id, device_hash) where device_hash is not null;
create index if not exists ix_signup_guard_ip on signup_guards(salon_id, ip_hash);
