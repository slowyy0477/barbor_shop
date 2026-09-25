-- New salons use the requested PKR 100 referrer reward (minor units).
-- Existing owner-configured values are intentionally preserved.
alter table salon_settings
    alter column referral_referrer_minor set default 10000;
