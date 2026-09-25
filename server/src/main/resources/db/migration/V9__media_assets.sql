-- Bounded logo and haircut images for small deployments.  The UUID URL is
-- immutable; replacing an image creates a new row and leaves old references
-- valid for already-rendered catalog responses.
create table if not exists media_assets (
    id uuid primary key,
    salon_id uuid not null,
    created_at timestamptz not null default now(),
    purpose varchar(32) not null,
    content_type varchar(64) not null,
    file_size bigint not null check(file_size > 0 and file_size <= 10485760),
    data bytea not null,
    version bigint not null default 0
);
create index if not exists ix_media_assets_salon on media_assets(salon_id, purpose, created_at desc);
