-- Revocable opaque sessions and one-time OTP challenges. Raw OTPs/tokens are never stored.
create table if not exists auth_accounts (
    id uuid primary key,
    salon_id uuid not null,
    created_at timestamptz not null default now(),
    version bigint not null default 0,
    phone_hash varchar(128) not null,
    role varchar(24) not null,
    permissions_json text not null default '[]',
    status varchar(24) not null default 'ACTIVE',
    constraint uq_auth_account_phone unique(salon_id, phone_hash)
);

create table if not exists otp_challenges (
    id uuid primary key,
    salon_id uuid not null,
    phone_hash varchar(128) not null,
    code_hash varchar(128) not null,
    expires_at timestamptz not null,
    attempt_count integer not null default 0,
    max_attempts integer not null default 5,
    consumed boolean not null default false,
    request_ip_hash varchar(128),
    created_at timestamptz not null default now(),
    version bigint not null default 0
);
create index if not exists ix_otp_phone_time on otp_challenges(salon_id, phone_hash, created_at desc);

create table if not exists auth_sessions (
    id uuid primary key,
    token_hash varchar(128) not null unique,
    salon_id uuid not null,
    actor_id uuid not null,
    role varchar(24) not null,
    permissions_json text not null default '[]',
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    last_seen_at timestamptz not null default now(),
    revoked_at timestamptz,
    user_agent varchar(300)
);
create index if not exists ix_auth_session_expiry on auth_sessions(expires_at);
