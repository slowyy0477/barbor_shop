-- Enforce staff-slot exclusivity at the database boundary as well as in the
-- service query. This closes the race where two API workers check an empty
-- slot concurrently and both try to reserve it.
create extension if not exists btree_gist;

alter table bookings
    add constraint ex_booking_staff_slot
    exclude using gist (
        salon_id with =,
        staff_id with =,
        tstzrange(starts_at, ends_at, '[)') with &&
    ) where (staff_id is not null and status in ('PENDING', 'CONFIRMED'));
