# Optional multi-user — design proposal

**Status (2026-10-30):** a proposal only. Nothing here is built. It completes phase 8 of the web UI redesign
(`research-web-ui.md` § 4.10, § 8), whose first half — bookmarks, notes and read marks — is built (§ 10 there,
"As built: phase 8") with a `user_id` on every row already, fixed to `local`.

## 1. What it is for, and what stays as it is

Histmersion runs for one person today. A few people may want to share one server — a research group, a
family, a teacher and a class — each with their own bookmarks, notes and read marks, and with different
rights: some read, some add sources, one looks after the server.

What must not change:

- **Single-user mode stays sign-in free.** With no people configured, the server behaves exactly as now:
  no sign-in page, no user menu, reads open, writes gated by the one shared key when `auth.api-key` is set.
- **Settings live in the `.conf` file** (decision 6 of `research-web-ui.md` § 7): a person must be able to
  repair access with a text editor, without the web UI, the database or the server running. That includes
  locking themselves out.
- **No secret is ever written in plain text by Histmersion**, and none is shown (phase 6's credential rule).
- **The server decides; the page only hides.** Every check is made on the server for REST, MCP and the CLI
  alike; the web UI hides what a role cannot use so people are not offered buttons that fail.

## 2. Accounts and sign-in

- **An account** is a name (`ada`), a display name, a role, and a way to sign in. Accounts are declared in
  the settings file (§ 4); nothing about who may do what is only in the database.
- **Sign-in** is a password checked against a hash (Argon2id, parameters in the hash string), entered on a
  plain sign-in page. The hash is never in the `.conf` file itself: the account names a *reference* to it,
  the way plugins name their credentials today — `env:HISTMERSION_PASSWORD_ADA`, `<vault>:<entry>` (the
  vault plugins), or `file:people/ada.hash` (a file next to the settings file, mode 0600, written by the CLI).
- **Sessions**: a random 256-bit token in an `HttpOnly`, `Secure` (when served over HTTPS), `SameSite=Lax`
  cookie; the server keeps only its SHA-256, in a `sessions` table (account, created, last seen, expires,
  user agent). Idle timeout and absolute lifetime are settings (`people.session-idle-hours`,
  `people.session-max-days`). Sessions are the one thing that lives in the database — losing them only means
  signing in again, and `histmersion people sign-out-all` (or deleting the table's rows) ends them all.
- **Machines**: the CLI and MCP clients use **personal access tokens** — per account, named, with a role no
  higher than their owner's, shown once, stored as a hash, listed and revocable. The CLI keeps its token
  where it keeps the shared key today. A token is sent as `Authorization: Bearer <token>`, so existing
  clients keep working.
- **CSRF**: cookie-authenticated writes carry an `X-Histmersion-CSRF` header (double-submit token); bearer
  tokens need none.
- **No third-party sign-in** in the first version (no paid providers, keyless first): OpenID Connect could
  be a plugin later.

## 3. Roles and permissions

Four roles, ordered; each has everything the one before it has.

| role | may |
|---|---|
| **reader** | read the corpus, the map, the articles; keep their **own** bookmarks, notes and read marks |
| **contributor** | also add and enrich sources, write articles, add places and events, upload files, edit the queues |
| **researcher** | also run and steer the research agents, decide approvals, start imports and downloads, export bundles |
| **admin** | also change settings, manage plugins, back up and restore, merge collections, manage people and their roles |

Permissions are checked per **operation**, not per screen: each endpoint (and so each MCP tool and CLI
command, which reach the same handlers) declares the role it needs in one table, next to `Api.all`. A unit
test fails when an endpoint has no entry. Reads of the person's own data (`/api/bookmarks`, `/api/notes`,
`/api/reading`) are always scoped to the account making the call; nobody, not even an admin, reads another
person's notes through the API.

## 4. Where accounts and roles are stored

```hocon
histmersion.people {
  enabled = false                       # false: single-user mode — everything below is ignored
  anonymous = "reader"                  # what someone NOT signed in may do: "none" | "reader"
  session-idle-hours = 72
  session-max-days = 30
  accounts {
    ada  { name = "Ada Lovelace", role = "admin",       password = "file:people/ada.hash" }
    ben  { name = "Ben",          role = "contributor", password = "env:HISTMERSION_PASSWORD_BEN" }
  }
}
```

- Accounts and roles: the settings file, by name. Adding a person, changing a role or removing someone is
  an edit of the file (by hand, from **Settings › People and access**, or with `histmersion people …`),
  followed by the existing reload — no restart.
- Password hashes: by reference (§ 2), never inline; `histmersion people set-password ada` writes the
  `file:` hash on the server's machine (or prints a hash for an `env:`/vault entry).
- Sessions and access tokens: the database (hashes only).
- **Repairing access without the web UI**: stop nothing — edit the file. Set `enabled = false` to go back to
  single-user mode, or give yourself `role = "admin"`, or point `password` at a new hash written with
  `histmersion people set-password --local`. The server's `--self-check` warns when people are enabled
  and no admin can sign in (no admin, or every admin's password reference unresolvable).

## 5. Per-person bookmarks, notes and read marks

Already shaped for it: every row of `bookmarks`, `notes` and `read_marks` (V109) has `user_id`, today
`local`. With people enabled:

- `user_id` = the account's name. The controllers take the signed-in account instead of
  `UserId.Local` (the one place `PersonalControllers` is built changes; services and repos are untouched).
- On the first sign-in of the first admin, the `local` rows are offered to them ("Keep the bookmarks, notes
  and read marks made before people were set up?") — one `UPDATE … SET user_id`, nothing silent.
- Renaming an account in the file would orphan its rows; `histmersion people rename old new` renames both.
- A removed account's rows stay (tombstoned on request with `histmersion people forget <name>`), so
  re-adding the person restores them.
- For the later exchange of person edits between databases (`research-deterministic-merge.md`), a row's
  `(origin, user_id)` names the person; the same account name on two servers is the same person only if
  the two admins say so (a mapping in the merge plan), never by name alone.

## 6. Server checks first, then the page

1. **Server**: a request is resolved to an account (cookie session, bearer access token, the legacy shared
   key — § 7, or anonymous), then the endpoint's required role is checked before any handler runs: 401
   when not signed in and the role needs one, 403 when the role is too low. MCP tool calls go through the
   same check. The maintenance lock and the existing write gate stay as they are.
2. **`/api/config`** tells the page who is signed in and their role (never a token), alongside
   `writeKeyRequired`, which it replaces when people are on.
3. **Page**: a `Can` helper hides or disables what the role cannot use (a contributor sees no Settings ›
   Plugins, a reader no "Add sources"). The page never decides access; a hidden button whose request is
   forged is refused by step 1.

## 7. Migration from the single shared key

- People off (the default): nothing changes; `auth.api-key` keeps gating writes.
- Turning people on with `auth.api-key` still set: the key keeps working **as an admin bearer token** for
  scripts and the CLI, with a warning on every About page and in the server log ("the shared key is still
  accepted; create access tokens and remove `auth.api-key`"). `people.accept-shared-key = false` turns it off.
- `histmersion people init` writes the block of § 4 with one admin (the person running it), asks for a
  password, writes the hash file, and reloads — the one command needed.
- The web UI's key window becomes the sign-in page when people are on; a stored key in the browser is
  forgotten on the first sign-in.

## 8. Wireframes (plain text)

**Settings › People and access** (people on; an admin's view)

```
┌ Settings ─────────────────────────────────────────────────────────────────────────────┐
│ General           People and access                                                   │
│ Data and …        ───────────────────────────────────────────────────────────────────│
│ …                 [x] Several people use this server       (people.enabled)          │
│ People and access    Someone not signed in may:  ( ) nothing  (•) read                │
│ Advanced …                                                                            │
│                   People                                             [ Add a person ] │
│                   ┌─────────────────────────────────────────────────────────────────┐ │
│                   │ Ada Lovelace  ada   Admin ▾        signed in 2 min ago  [Reset…]│ │
│                   │ Ben           ben   Contributor ▾  never signed in      [Reset…]│ │
│                   │                                                     [Remove ben]│ │
│                   └─────────────────────────────────────────────────────────────────┘ │
│                   Sessions: 3 open   [ Sign everyone out ]                            │
│                   The shared key (auth.api-key) is still accepted.  [ Stop accepting ]│
│                   These settings are in histmersion.conf: edit it to repair access.   │
│                                                                      [ Save ]         │
└───────────────────────────────────────────────────────────────────────────────────────┘
```

**The user menu** (top bar, right; only when people are on)

```
 … Agents working · 3 runs   2 need your decision   [ AL ▾ ]
                                                    ┌────────────────────────────┐
                                                    │ Ada Lovelace · Admin       │
                                                    │ My library                 │
                                                    │ Access tokens…             │
                                                    │ Change my password…        │
                                                    │ ────────────────────────── │
                                                    │ Sign out                   │
                                                    └────────────────────────────┘
```

**Sign-in page**

```
            Histmersion
   ┌──────────────────────────────┐
   │ Name      [ ada            ] │
   │ Password  [ ••••••••       ] │
   │           [   Sign in    ]   │
   │ Forgotten? Ask the person who│
   │ looks after this server.     │
   └──────────────────────────────┘
```

## 9. Open questions, with recommended answers

1. **Should anonymous visitors be able to read?** — Recommended: `anonymous = "reader"` by default for a
   server on `localhost`, `"none"` when `server.public-url` is not a loopback address (the installer asks).
2. **Should an admin be able to read other people's notes?** — No through the API (§ 3); they can read the
   database, and the page should say so honestly in People and access.
3. **Can roles be finer than four (per-topic, per-goal)?** — Not in the first version. Four ordered roles
   cover the cases asked for; per-object rights multiply checks and UI states.
4. **Should agents act as a person?** — Yes: an agent run records the account that started it (or the
   autopilot as a system actor), and its writes are checked against that account's role, so a contributor's
   runs cannot decide approvals.
5. **Are bookmarks and notes ever shared?** — Later, as an explicit "share with…" that copies a read-only
   view; not by changing the owner.
6. **Should the CLI on the server's own machine bypass sign-in?** — Only `--local` commands that edit the
   file (§ 4), as today; HTTP calls always carry a token.
7. **Password rules** — length ≥ 12, no other composition rules (NIST SP 800-63B), a rate limit and a short
   delay per failed sign-in, and a lock-out logged but not permanent.
8. **Two-factor sign-in** — not in the first version; TOTP is a small addition once accounts exist.
