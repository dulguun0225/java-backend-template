-- The worked-example table. Conventions every migration here follows (MigrationConventionsTest and squawk
-- enforce the mechanical ones):
--   * timeouts first: a migration that waits on a lock or runs long fails fast instead of stalling a deploy.
--   * primary key: uuid, DEFAULT uuidv7() (PostgreSQL 18 native) as the backstop for ad-hoc SQL; the app
--     assigns v7 ids itself via Ids.newId(). Never serial, identity, sequences or gen_random_uuid().
--   * timestamps: timestamptz, never timestamp; no clock function as a column default, the app's Clock is
--     the only clock.
--   * nothing here is ever edited after it ships; a change is a new V<n>__ file.
set lock_timeout = '5s';
set statement_timeout = '60s';

create table greeting (
    id         uuid primary key default uuidv7(),
    name       text not null check (length(name) between 1 and 100),
    created_at timestamptz not null
);

create index greeting_created_at_id_idx on greeting (created_at desc, id desc);
