alter table if exists payment_method_configs
    add column if not exists account_reference varchar(120);

-- Keep the initial manual-payment setup useful without storing a provider PIN.
-- Owners can replace this public receiving number from the settings screen.
update payment_method_configs pm
set account_reference = ss.phone
from salon_settings ss
where pm.salon_id = ss.salon_id
  and (pm.account_reference is null or pm.account_reference = '');
