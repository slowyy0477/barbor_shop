-- Sign-in PINs for salon accounts (customers, owner and staff). Only a
-- PBKDF2-HMAC-SHA256 digest and a random salt are stored; the plaintext PIN
-- never reaches PostgreSQL or the logs. An account without a row keeps using
-- the SMS/OTP verification code, so this is additive.
create table if not exists sign_in_pins (
    id uuid primary key,
    salon_id uuid not null,
    actor_id uuid not null,
    pin_hash varchar(256) not null,
    pin_salt varchar(64) not null,
    iterations integer not null default 120000,
    attempt_count integer not null default 0,
    locked_until timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0,
    constraint uq_sign_in_pin unique(salon_id, actor_id)
);
create index if not exists ix_sign_in_pin_actor on sign_in_pins(salon_id, actor_id);
