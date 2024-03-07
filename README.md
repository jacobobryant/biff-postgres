# How to use Postgres with Biff

This is an example of using Postgres instead of XTDB with [Biff](https://biffweb.com). See [How to use Postgres with
Biff](https://biffweb.com/p/how-to-use-postgres-with-biff/) for commentary. To try it out:

1. Make sure you have Docker installed (for running Postgres in dev).
2. Run `clj -M:dev dev` like usual.

See `resources/migrations.sql` for schema. There's also a custom `clj -M:dev dev` task in
`dev/tasks.clj`.
