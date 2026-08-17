package kio.note.db

import kio.postgres.migration.Migration

val migration_1 = Migration(
    version = 1,
    name = "migration_1",
    sql = """
create table if not exists notes (
    id bigserial primary key,
    title text not null,
    create_at timestamptz not null default now(),
    update_at timestamptz not null default now()
);
create table if not exists note_blocks (
    id bigserial primary key,
    note_id bigint not null
        references notes(id)
        on delete cascade,
    type text not null,
    sort_order bigint not null,
    text_content text,
    image_url text
);
create index if not exists idx_note_blocks_note_order
on note_blocks(note_id, sort_order);
    """.trimIndent()
)

val migration_2 = Migration(
    version = 2,
    name = "migration_2",
    sql = """
create table users (
    id bigserial primary key,
    username text not null unique,
    password_hash text not null,
    create_at timestamptz not null default now()
);
create table sessions (
    id text primary key,
    user_id bigint not null
        references users(id)
        on delete cascade,
    create_at timestamptz not null default now()
);
    """.trimIndent()
)
