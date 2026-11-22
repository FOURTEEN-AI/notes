create table if not exists note (
    id         bigint generated always as identity primary key,
    text       text        not null,
    created_at timestamptz not null default now()
);
