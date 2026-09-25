-- Optional local/demo seed. Do not run this against a production database.
-- Values are PKR minor units (500 PKR = 50000).
insert into salon_settings (salon_id, name, address, phone, currency, timezone, opening_minute, closing_minute,
  haircut_reminder_days, deposit_bonus_minor, referral_referrer_minor, referral_new_customer_minor,
  minimum_withdrawal_minor, maximum_daily_withdrawal_minor, consume_promo_first)
values ('00000000-0000-0000-0000-000000000001', 'Ayan Beauty Salon',
  'VC8Q+R33 Ayan Beauty Salon, Uqab Plaza, Gate Number 2, Katba Village, Kamra Kalan',
  '0310 5301460', 'PKR', 'Asia/Karachi', 480, 1380, 25, 5000, 10000, 10000, 10000, 1000000, true)
on conflict (salon_id) do nothing;

insert into staff (id, salon_id, name, phone, role, active) values
 ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000001', 'Ayan Malik', '0310 5301460', 'OWNER', true),
 ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000001', 'Usman Barber', '0300 0000001', 'BARBER', true),
 ('00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000001', 'Bilal Stylist', '0300 0000002', 'STAFF', true)
on conflict (id) do nothing;

insert into services (id, salon_id, name, description, price_minor, duration_minutes, category) values
 ('00000000-0000-0000-0000-000000001001', '00000000-0000-0000-0000-000000000001', 'Haircut', 'Classic neighbourhood haircut', 30000, 30, 'Hair'),
 ('00000000-0000-0000-0000-000000001002', '00000000-0000-0000-0000-000000000001', 'Beard Trim', 'Beard shaping and trim', 9900, 15, 'Beard'),
 ('00000000-0000-0000-0000-000000001003', '00000000-0000-0000-0000-000000000001', 'Haircut + Beard', 'Combined haircut and beard', 45000, 45, 'Hair'),
 ('00000000-0000-0000-0000-000000001004', '00000000-0000-0000-0000-000000000001', 'Kids Haircut', 'Child-friendly haircut', 25000, 25, 'Hair'),
 ('00000000-0000-0000-0000-000000001005', '00000000-0000-0000-0000-000000000001', 'Hair Styling', 'Wash and styling', 35000, 30, 'Hair'),
 ('00000000-0000-0000-0000-000000001006', '00000000-0000-0000-0000-000000000001', 'Head Massage', 'Relaxing head massage', 19900, 20, 'Wellness'),
 ('00000000-0000-0000-0000-000000001007', '00000000-0000-0000-0000-000000000001', 'Facial', 'Basic salon facial', 70000, 45, 'Skin'),
 ('00000000-0000-0000-0000-000000001008', '00000000-0000-0000-0000-000000000001', 'Hair Colour', 'Single-process colour', 120000, 90, 'Colour'),
 ('00000000-0000-0000-0000-000000001009', '00000000-0000-0000-0000-000000000001', 'Hair Wash', 'Shampoo and conditioning', 15000, 15, 'Hair'),
 ('00000000-0000-0000-0000-000000001010', '00000000-0000-0000-0000-000000000001', 'Bridal Styling', 'Consultation required', 250000, 150, 'Special')
on conflict (id) do nothing;

insert into customers (id, salon_id, name, phone, phone_hash, phone_verified, marketing_consent) values
 ('00000000-0000-0000-0000-000000002001', '00000000-0000-0000-0000-000000000001', 'Ali Raza', '0300 1111111', 'demo-phone-001', true, true),
 ('00000000-0000-0000-0000-000000002002', '00000000-0000-0000-0000-000000000001', 'Hamza Khan', '0300 1111112', 'demo-phone-002', true, false),
 ('00000000-0000-0000-0000-000000002003', '00000000-0000-0000-0000-000000000001', 'Saad Ahmed', '0300 1111113', 'demo-phone-003', true, true),
 ('00000000-0000-0000-0000-000000002004', '00000000-0000-0000-0000-000000000001', 'Usman Tariq', '0300 1111114', 'demo-phone-004', true, false),
 ('00000000-0000-0000-0000-000000002005', '00000000-0000-0000-0000-000000000001', 'Bilal Shah', '0300 1111115', 'demo-phone-005', true, true),
 ('00000000-0000-0000-0000-000000002006', '00000000-0000-0000-0000-000000000001', 'Muneeb Ali', '0300 1111116', 'demo-phone-006', true, false),
 ('00000000-0000-0000-0000-000000002007', '00000000-0000-0000-0000-000000000001', 'Fahad Iqbal', '0300 1111117', 'demo-phone-007', true, true),
 ('00000000-0000-0000-0000-000000002008', '00000000-0000-0000-0000-000000000001', 'Arslan Yousaf', '0300 1111118', 'demo-phone-008', true, false),
 ('00000000-0000-0000-0000-000000002009', '00000000-0000-0000-0000-000000000001', 'Naveed Khan', '0300 1111119', 'demo-phone-009', true, true),
 ('00000000-0000-0000-0000-000000002010', '00000000-0000-0000-0000-000000000001', 'Waqas Butt', '0300 1111120', 'demo-phone-010', true, false)
on conflict (id) do nothing;

insert into wallets (customer_id, salon_id) select id, salon_id from customers
where salon_id = '00000000-0000-0000-0000-000000000001'
on conflict (customer_id) do nothing;

insert into payment_method_configs (id, salon_id, provider, display_name, account_title, account_reference, account_token, instructions, enabled, sort_order, mode) values
 ('00000000-0000-0000-0000-000000003001', '00000000-0000-0000-0000-000000000001', 'EASYPAISA', 'Easypaisa', 'Ayan Beauty Salon', '03105301460', 'configure-in-owner-dashboard', 'Send payment, then submit reference ID. Never share a PIN.', true, 1, 'MANUAL'),
 ('00000000-0000-0000-0000-000000003002', '00000000-0000-0000-0000-000000000001', 'JAZZCASH', 'JazzCash', 'Ayan Beauty Salon', '03105301460', 'configure-in-owner-dashboard', 'Send payment, then submit reference ID. Never share a PIN.', true, 2, 'MANUAL'),
 ('00000000-0000-0000-0000-000000003003', '00000000-0000-0000-0000-000000000001', 'NAYAPAY', 'NayaPay', 'Ayan Beauty Salon', '03105301460', 'configure-in-owner-dashboard', 'Send payment, then submit reference ID. Never share a PIN.', true, 3, 'MANUAL'),
 ('00000000-0000-0000-0000-000000003004', '00000000-0000-0000-0000-000000000001', 'SADAPAY', 'SadaPay', 'Ayan Beauty Salon', '03105301460', 'configure-in-owner-dashboard', 'Send payment, then submit reference ID. Never share a PIN.', true, 4, 'MANUAL')
on conflict (id) do nothing;
