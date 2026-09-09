# lynq-iam scripts

## backfill-user-roles.sql

Fills `lynq_iam_db.user_roles` for the users that already existed when the role
axis moved into lynq-iam. Their role is inferred from `lynq_backend_db.users.type`,
which was the only source of truth for candidate/company before this change.

It is a one-off script, run by hand once per environment (dev, sec, prod) — not a
Liquibase changeset, because it crosses schemas and IAM's startup must not depend
on the backend's schema being there.

### Order

1. Deploy lynq-iam with the `02-create-user-roles-table` changeset applied.
2. Run this script.
3. Check that step 3 returns no rows.
4. Only then drop `lynq_backend_db.users.type` (changeset `19-drop-user-type`), which
   is the column step 2 reads.

### Running it

Any MySQL client connected as a user that can read `lynq_backend_db` and write
`lynq_iam_db`:

```bash
mysql -h <host> -u <user> -p < scripts/backfill-user-roles.sql
```

### What each statement is for

- **Step 1** counts IAM users with no row in `lynq_backend_db.users`: accounts that
  registered but never completed the profile. They get no role from this script and
  are answered 403 on every role-guarded endpoint until they do.
- **Step 2** inserts one role per user, `R_COMPANY` for `type = 'COMPANY'` and
  `R_CANDIDATE` otherwise. It is idempotent: re-running it rewrites the same value.
- **Step 3** lists the IAM users still without a role. It must come back empty; the
  rows it returns are the accounts counted in step 1.
