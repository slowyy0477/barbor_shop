-- A fresh install must not display a salon name, address or receiving account
-- that the owner never typed. Only the untouched seed values are cleared, so a
-- salon that already renamed itself is never overwritten. Everything is filled
-- in again by the owner from Owner > Settings.
update salon_settings
   set name = '',
       address = ''
 where name = 'Ayan Beauty Salon';

update payment_method_configs
   set account_title = '',
       account_reference = null,
       instructions = 'Ask the salon owner for the correct receiving account and reference before sending money.'
 where account_title = 'Ayan Beauty Salon';
