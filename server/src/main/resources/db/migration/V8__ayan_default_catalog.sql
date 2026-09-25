-- Configuration-only bootstrap for the first Ayan salon. It intentionally
-- creates no customer, wallet, booking, visit or financial records.
insert into salon_settings (salon_id, name, address, phone, currency, timezone,
  opening_minute, closing_minute, haircut_reminder_days, deposit_bonus_minor,
  referral_referrer_minor, referral_new_customer_minor, minimum_withdrawal_minor,
  maximum_daily_withdrawal_minor, consume_promo_first, primary_color)
values ('00000000-0000-0000-0000-000000000001', 'Ayan Beauty Salon',
  'VC8Q+R33 Ayan Beauty Salon, Uqab Plaza, Gate Number 2, Katba Village, Kamra Kalan',
  '0310 5301460', 'PKR', 'Asia/Karachi', 480, 1380, 25, 5000, 10000,
  10000, 10000, 1000000, true, '#FF9E3B')
on conflict (salon_id) do nothing;

insert into staff (id, salon_id, name, phone, role, active, permissions_json) values
 ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000001', 'Adeel Khan', '03001234567', 'BARBER', true, '["create_booking","complete_service"]'),
 ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000001', 'Hamza Iqbal', '03001234568', 'BARBER', true, '["create_booking","complete_service"]'),
 ('00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000001', 'Sana Ahmed', '03001234569', 'STAFF', true, '["create_booking","complete_service"]')
on conflict (id) do nothing;

insert into services (id, salon_id, name, description, price_minor, duration_minutes, category) values
 ('00000000-0000-0000-0000-000000001001', '00000000-0000-0000-0000-000000000001', 'Classic Haircut', 'Classic neighbourhood haircut', 80000, 35, 'Hair'),
 ('00000000-0000-0000-0000-000000001002', '00000000-0000-0000-0000-000000000001', 'Beard Trim', 'Beard shaping and trim', 45000, 20, 'Beard'),
 ('00000000-0000-0000-0000-000000001003', '00000000-0000-0000-0000-000000000001', 'Haircut + Beard', 'Combined haircut and beard', 110000, 50, 'Hair'),
 ('00000000-0000-0000-0000-000000001004', '00000000-0000-0000-0000-000000000001', 'Kids Cut', 'Child-friendly haircut', 60000, 30, 'Hair'),
 ('00000000-0000-0000-0000-000000001005', '00000000-0000-0000-0000-000000000001', 'Hair Colour', 'Single-process colour', 280000, 120, 'Colour'),
 ('00000000-0000-0000-0000-000000001006', '00000000-0000-0000-0000-000000000001', 'Signature Facial', 'Salon facial treatment', 150000, 55, 'Skin'),
 ('00000000-0000-0000-0000-000000001007', '00000000-0000-0000-0000-000000000001', 'Head Massage', 'Relaxing head massage', 70000, 30, 'Wellness'),
 ('00000000-0000-0000-0000-000000001008', '00000000-0000-0000-0000-000000000001', 'Wash & Blow Dry', 'Wash and styling', 90000, 45, 'Hair'),
 ('00000000-0000-0000-0000-000000001009', '00000000-0000-0000-0000-000000000001', 'Bridal Styling', 'Consultation required', 450000, 150, 'Occasion'),
 ('00000000-0000-0000-0000-000000001010', '00000000-0000-0000-0000-000000000001', 'Skin Cleanup', 'Basic skin cleanup', 120000, 45, 'Skin')
on conflict (id) do nothing;

insert into add_ons (id, salon_id, name, price_minor, duration_minutes, service_id, active) values
 ('00000000-0000-0000-0000-000000004001', '00000000-0000-0000-0000-000000000001', 'Beard Trim', 9900, 12, '00000000-0000-0000-0000-000000001001', true),
 ('00000000-0000-0000-0000-000000004002', '00000000-0000-0000-0000-000000000001', 'Head Massage', 19900, 18, '00000000-0000-0000-0000-000000001001', true)
on conflict (id) do nothing;

insert into payment_method_configs (id, salon_id, provider, display_name, account_title,
  account_token, instructions, enabled, sort_order, mode) values
 ('00000000-0000-0000-0000-000000003001', '00000000-0000-0000-0000-000000000001', 'EASYPAISA', 'Easypaisa', 'Ayan Beauty Salon', null, 'Send the exact amount, then submit the reference ID. Owner verifies it manually.', true, 1, 'MANUAL'),
 ('00000000-0000-0000-0000-000000003002', '00000000-0000-0000-0000-000000000001', 'JAZZCASH', 'JazzCash', 'Ayan Beauty Salon', null, 'Send the exact amount, then submit the reference ID. Owner verifies it manually.', true, 2, 'MANUAL'),
 ('00000000-0000-0000-0000-000000003003', '00000000-0000-0000-0000-000000000001', 'NAYAPAY', 'NayaPay', 'Ayan Beauty Salon', null, 'Send the exact amount, then submit the reference ID. Owner verifies it manually.', true, 3, 'MANUAL'),
 ('00000000-0000-0000-0000-000000003004', '00000000-0000-0000-0000-000000000001', 'SADAPAY', 'SadaPay', 'Ayan Beauty Salon', null, 'Send the exact amount, then submit the reference ID. Owner verifies it manually.', true, 4, 'MANUAL')
on conflict (id) do nothing;
