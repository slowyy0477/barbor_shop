-- One-time deposit promotion state, owner branding, and visual haircut catalog.
-- Image fields intentionally store bounded URIs/metadata rather than raw bytes.

alter table wallets
    add column if not exists first_deposit_bonus_claimed boolean not null default false;
alter table deposits
    add column if not exists bonus_granted_minor bigint not null default 0;
do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conname = 'ck_deposit_bonus_granted_range'
          and conrelid = 'deposits'::regclass
    ) then
        alter table deposits add constraint ck_deposit_bonus_granted_range
            check (bonus_granted_minor >= 0 and bonus_granted_minor <= bonus_snapshot_minor);
    end if;
end $$;

-- Existing installations may already have granted a deposit bonus.  Backfill the
-- account-level flag conservatively from either the immutable ledger or an
-- approved deposit snapshot so those customers never receive it again.
update wallets w
set first_deposit_bonus_claimed = true
where not w.first_deposit_bonus_claimed
  and (
      exists (
          select 1 from wallet_transactions t
          where t.salon_id = w.salon_id
            and t.customer_id = w.customer_id
            and t.type = 'DEPOSIT_BONUS'
            and t.promo_amount_minor > 0
      )
      or exists (
          select 1 from deposits d
          where d.salon_id = w.salon_id
            and d.customer_id = w.customer_id
            and d.status = 'APPROVED'
            and d.bonus_snapshot_minor > 0
      )
  );

alter table salon_settings
    add column if not exists primary_color varchar(7) not null default '#0F766E';
alter table salon_settings
    add column if not exists logo_uri varchar(2048);
do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conname = 'ck_salon_settings_primary_color'
          and conrelid = 'salon_settings'::regclass
    ) then
        alter table salon_settings add constraint ck_salon_settings_primary_color
            check (primary_color ~ '^#[0-9A-Fa-f]{6}$');
    end if;
end $$;

create table if not exists haircut_styles (
    id uuid primary key,
    salon_id uuid not null,
    created_at timestamptz not null default now(),
    version bigint not null default 0,
    name varchar(120) not null,
    photo_uri varchar(2048) not null,
    photo_alt_text varchar(160),
    description varchar(600),
    price_minor bigint not null check(price_minor >= 0),
    display_order integer not null default 0 check(display_order >= 0),
    active boolean not null default true
);
create index if not exists ix_haircut_style_catalog
    on haircut_styles(salon_id, active, display_order, name);
