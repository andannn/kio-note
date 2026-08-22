package kio.note.db

import kio.postgres.conn.PgConnection
import kio.postgres.migration.Migration

val migrations get() = listOf(
    migration_1,
    migration_2,
)

suspend fun PgConnection.dropAllTables() {
    exec("drop table if exists schema_migrations cascade")
    exec("drop table if exists users cascade")
    exec("drop table if exists sessions cascade")
    exec("drop table if exists notes cascade")
    exec("drop table if exists note_blocks cascade")
}

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
insert into users (
    id,
    username,
    password_hash
)
values (1, 'preset', '8d969eef6ecad3c29a3a629280e686cff8ca2a4a8e0c6c3f5f5a86aff3ca120');

alter table notes
add column user_id bigint;

update notes
set user_id = 1
where user_id is null;

alter table notes
alter column user_id set not null;

alter table notes
add constraint fk_notes_user
foreign key (user_id)
references users(id)
on delete cascade;

create index if not exists idx_notes_user_id
on notes(user_id);
    """.trimIndent()
)
