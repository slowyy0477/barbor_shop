-- PostgreSQL migration. Money is stored as integer PKR minor units; ledger rows are append-only.
create extension if not exists pgcrypto;

create table if not exists salon_settings (
    salon_id uuid primary key,
    version bigint not null default 0,
    name varchar(120) not null,
    address varchar(500) not null,
    phone varchar(32) not null,
    currency varchar(3) not null default 'PKR',
    timezone varchar(64) not null default 'Asia/Karachi',
    opening_minute integer not null default 480,
    closing_minute integer not null default 1380,
    haircut_reminder_days integer not null default 25,
    deposit_bonus_minor bigint not null default 5000,
    referral_referrer_minor bigint not null default 10000,
    referral_new_customer_minor bigint not null default 10000,
    minimum_withdrawal_minor bigint not null default 10000,
    maximum_daily_withdrawal_minor bigint not null default 1000000,
    consume_promo_first boolean not null default true,
    updated_at timestamptz not null default now()
);

create table if not exists customers (
    id uuid primary key,
    salon_id uuid not null,
    created_at timestamptz not null default now(),
    version bigint not null default 0,
    name varchar(120) not null,
    phone varchar(32) not null,
    phone_hash varchar(128) not null,
    status varchar(24) not null default 'ACTIVE',
    phone_verified boolean not null default false,
    marketing_consent boolean not null default false,
    app_instance_hash varchar(128),
    last_ip_hash varchar(128),
    deleted_at timestamptz,
    constraint uq_customer_phone unique(salon_id, phone),
    constraint uq_customer_phone_hash unique(salon_id, phone_hash)
);
create index if not exists ix_customer_app_instance on customers(salon_id, app_instance_hash);

create table if not exists wallets (
    customer_id uuid primary key references customers(id),
    salon_id uuid not null,
    cash_available_minor bigint not null default 0 check(cash_available_minor >= 0),
    cash_reserved_minor bigint not null default 0 check(cash_reserved_minor >= 0),
    promo_available_minor bigint not null default 0 check(promo_available_minor >= 0),
    version bigint not null default 0
);

create table if not exists staff (
    id uuid primary key,
    salon_id uuid not null,
    created_at timestamptz not null default now(),
    version bigint not null default 0,
    name varchar(120) not null,
    phone varchar(32),
    role varchar(24) not null,
    active boolean not null default true,
    permissions_json text not null default '[]'
);

create table if not exists services (
    id uuid primary key,
    salon_id uuid not null,
    created_at timestamptz not null default now(),
    version bigint not null default 0,
    name varchar(120) not null,
    description varchar(600),
    price_minor bigint not null check(price_minor >= 0),
    duration_minutes integer not null check(duration_minutes > 0),
    category varchar(80),
    active boolean not null default true
);

create table if not exists bookings (
    id uuid primary key,
    salon_id uuid not null,
    created_at timestamptz not null default now(),
    version bigint not null default 0,
    customer_id uuid not null references customers(id),
    service_id uuid not null references services(id),
    staff_id uuid references staff(id),
    starts_at timestamptz not null,
    ends_at timestamptz not null,
    total_minor bigint not null check(total_minor > 0),
    add_on_minor bigint not null default 0 check(add_on_minor >= 0),
    status varchar(20) not null default 'PENDING',
    payment_method varchar(24) not null,
    wallet_payment_reference varchar(120),
    completed_at timestamptz
);
create index if not exists ix_booking_staff_slot on bookings(salon_id, staff_id, starts_at, ends_at);

create table if not exists deposits (
    id uuid primary key,
    salon_id uuid not null,
    created_at timestamptz not null default now(),
    version bigint not null default 0,
    customer_id uuid not null references customers(id),
    amount_minor bigint not null check(amount_minor > 0),
    currency varchar(3) not null default 'PKR',
    provider varchar(24) not null,
    provider_code varchar(32) not null,
    provider_reference varchar(180) not null,
    proof_uri varchar(500),
    bonus_snapshot_minor bigint not null default 0,
    status varchar(20) not null default 'PENDING',
    reviewed_by uuid,
    review_reason varchar(500),
    reviewed_at timestamptz
);
create unique index if not exists uq_deposit_provider_reference on deposits(salon_id, lower(provider_reference));

create table if not exists withdrawals (
    id uuid primary key,
    salon_id uuid not null,
    created_at timestamptz not null default now(),
    version bigint not null default 0,
    customer_id uuid not null references customers(id),
    amount_minor bigint not null check(amount_minor > 0),
    currency varchar(3) not null default 'PKR',
    provider varchar(24) not null,
    provider_code varchar(32) not null,
    destination_token varchar(180) not null,
    status varchar(20) not null default 'PENDING',
    reviewed_by uuid,
    review_reason varchar(500),
    reviewed_at timestamptz
);
create index if not exists ix_withdrawal_customer_day on withdrawals(salon_id, customer_id, created_at);

create table if not exists wallet_transactions (
    id uuid primary key,
    salon_id uuid not null,
    created_at timestamptz not null default now(),
    customer_id uuid not null references customers(id),
    type varchar(32) not null,
    direction varchar(12) not null default 'CREDIT',
    cash_amount_minor bigint not null default 0 check(cash_amount_minor >= 0),
    promo_amount_minor bigint not null default 0 check(promo_amount_minor >= 0),
    currency varchar(3) not null default 'PKR',
    status varchar(32) not null,
    reference_id varchar(120) not null,
    created_by uuid,
    reason varchar(500),
    occurred_at timestamptz not null default now(),
    constraint ck_ledger_positive check(cash_amount_minor + promo_amount_minor > 0)
);
create unique index if not exists uq_wallet_reference_type on wallet_transactions(salon_id, reference_id, type);
create index if not exists ix_wallet_customer_time on wallet_transactions(salon_id, customer_id, occurred_at desc);

create table if not exists referrals (
    id uuid primary key,
    salon_id uuid not null,
    created_at timestamptz not null default now(),
    version bigint not null default 0,
    referrer_customer_id uuid not null references customers(id),
    referred_customer_id uuid not null references customers(id),
    code varchar(32) not null,
    status varchar(24) not null default 'REGISTERED',
    referrer_reward_minor bigint not null default 0,
    new_customer_discount_minor bigint not null default 0,
    qualifying_booking_id uuid references bookings(id),
    released_at timestamptz,
    constraint ck_referral_not_self check(referrer_customer_id <> referred_customer_id),
    constraint uq_referral_referred unique(salon_id, referred_customer_id),
    constraint uq_referral_code unique(salon_id, code)
);
create unique index if not exists uq_referral_code_ci on referrals(salon_id, lower(code));

create table if not exists visits (
    id uuid primary key,
    salon_id uuid not null,
    created_at timestamptz not null default now(),
    customer_id uuid not null references customers(id),
    booking_id uuid not null unique references bookings(id),
    service_id uuid not null references services(id),
    staff_id uuid references staff(id),
    completed_at timestamptz not null,
    next_due_at timestamptz,
    total_minor bigint not null
);

create table if not exists reminders (
    id uuid primary key,
    salon_id uuid not null,
    created_at timestamptz not null default now(),
    customer_id uuid not null references customers(id),
    service_id uuid not null references services(id),
    due_at timestamptz not null,
    status varchar(20) not null default 'SCHEDULED',
    opted_out boolean not null default false,
    source_visit_id uuid references visits(id)
);
create unique index if not exists uq_reminder_cycle on reminders(salon_id, customer_id, service_id, source_visit_id);

create table if not exists payment_method_configs (
    id uuid primary key,
    salon_id uuid not null,
    created_at timestamptz not null default now(),
    provider varchar(24) not null,
    display_name varchar(80) not null,
    account_title varchar(120),
    account_token varchar(300),
    instructions varchar(500),
    enabled boolean not null default true,
    sort_order integer not null default 0,
    mode varchar(20) not null default 'MANUAL',
    constraint uq_payment_provider unique(salon_id, provider)
);

create table if not exists business_ledger (
    id uuid primary key,
    salon_id uuid not null,
    created_at timestamptz not null default now(),
    type varchar(32) not null,
    amount_minor bigint not null check(amount_minor > 0),
    currency varchar(3) not null default 'PKR',
    reference_id varchar(120) not null,
    reason varchar(500),
    occurred_at timestamptz not null default now()
);

create table if not exists audit_logs (
    id uuid primary key,
    salon_id uuid not null,
    created_at timestamptz not null default now(),
    actor_id uuid,
    action varchar(80) not null,
    target_type varchar(80) not null,
    target_id uuid,
    details varchar(4000),
    occurred_at timestamptz not null default now()
);

create table if not exists idempotency_records (
    idempotency_key varchar(160) primary key,
    salon_id uuid not null,
    operation varchar(80) not null,
    response_json text not null,
    created_at timestamptz not null default now()
);
