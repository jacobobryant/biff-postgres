# How to use Postgres with Biff

This is an example of using Postgres instead of XTDB with
[Biff](https://biffweb.com). Blog post coming soon. To try it out:

1. Make sure you have Docker installed (for running Postgres in dev).
2. Copy `secrets.env.TEMPLATE` to `secrets.env`.
3. Run `bb generate-secrets` and insert the values into `secrets.env`.
2. Run `bb dev` like usual.

See `resources/migrations.sql` for schema. There's also a custom `bb dev` task in
`bb/src/com/biffweb/examples/postgres/tasks.clj`.
