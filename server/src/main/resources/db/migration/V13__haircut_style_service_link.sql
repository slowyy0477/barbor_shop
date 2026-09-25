-- A published haircut style can optionally be linked to one bookable service so
-- the customer's "Book this look" button opens the booking screen with that
-- service already selected. Styles stay usable as pure look-books (null link).
alter table haircut_styles
    add column if not exists service_id uuid;

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'fk_haircut_style_service'
    ) then
        alter table haircut_styles
            add constraint fk_haircut_style_service
            foreign key (service_id) references services(id) on delete set null;
    end if;
end
$$;

create index if not exists ix_haircut_style_service
    on haircut_styles(service_id);
