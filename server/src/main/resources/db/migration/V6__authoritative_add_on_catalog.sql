-- Owner-managed optional extras. Booking rows snapshot the selected catalog
-- values so later price edits cannot rewrite historical totals.
create table if not exists add_ons (
    id uuid primary key,
    salon_id uuid not null,
    created_at timestamptz not null default now(),
    version bigint not null default 0,
    name varchar(120) not null,
    description varchar(600),
    price_minor bigint not null check(price_minor >= 0),
    duration_minutes integer not null check(duration_minutes > 0),
    service_id uuid,
    active boolean not null default true
);
create index if not exists ix_add_on_catalog on add_ons(salon_id, active, service_id, name);
alter table bookings add column if not exists add_on_snapshot varchar(4000);
