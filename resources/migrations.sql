/* thanks to pesterhazy for the `idempotent` function:
 * https://clojureverse.org/t/how-do-you-do-database-migration-evolution/2005/2
 * https://gist.github.com/pesterhazy/9f7c0a7a9edd002759779c1732e0ac43
 */

create table if not exists migrations (
  key text CONSTRAINT pkey PRIMARY KEY
);

create or replace function idempotent(migration_name text,code text) returns void as $$
begin
if exists (select key from migrations where key=migration_name) then
  raise notice 'Migration already applied: %', migration_name;
else
  raise notice 'Running migration: %', migration_name;
  execute code;
  insert into migrations (key) VALUES (migration_name);
end if;
end;
$$ language plpgsql strict;


do $do$ begin perform idempotent('V0001__users_table', $$
CREATE TABLE users (
  id        uuid PRIMARY KEY,
  email     text NOT NULL UNIQUE,
  joined_at timestamp DEFAULT NOW(),
  foo       text,
  bar       text
)
$$); end $do$;


do $do$ begin perform idempotent('V0002__message_table', $$
CREATE TABLE message (
  id      uuid PRIMARY KEY,
  user_id uuid NOT NULL,
  text    text NOT NULL,
  sent_at timestamp DEFAULT NOW()
)
$$); end $do$;


do $do$ begin perform idempotent('V0003__auth_code_table', $$
CREATE TABLE auth_code (
  id              uuid PRIMARY KEY,
  email           text NOT NULL UNIQUE,
  code            text NOT NULL,
  created_at      timestamp NOT NULL,
  failed_attempts integer DEFAULT 0
)
$$); end $do$;


/*
 * Use this template to create additional migrations:
 *
 * do $do$ begin perform idempotent('V0004__another_migration', $$
 * ...
 * $$); end $do$;
 *
 */
