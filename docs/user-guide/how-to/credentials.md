# Credentials and secrets

Some services need a key or a login: an AI provider, a key-gated index such as CORE, a plugin for a
service you subscribe to. Histmersion never wants the secret itself written into its settings.
Instead you write a **secret reference** — a pointer to where the secret is kept — and the server
looks it up when it needs it. It never stores, logs or shows the value.

## Secret references

| form | example | the value comes from |
|---|---|---|
| `env:<NAME>` | `"env:HISTMERSION_CORE_KEY"` | an environment variable of the server |
| `keepassxc:<entry>[#<attribute>]` | `"keepassxc:Histmersion/CORE#password"` | your KeePassXC database |
| `1password:op://<vault>/<item>/<field>` | `"1password:op://Research/CORE/credential"` | your 1Password account |
| `bitwarden:<item>[#<field>]` | `"bitwarden:CORE#password"` | your Bitwarden vault |
| `value:<literal>` | `"value:…"` | the settings file itself — allowed, but the server warns, and shows it as `value:***` |

Reports (`histmersion about`, the About window) always show the reference, never the value.

Check your references without starting anything:

```bash
~/Applications/Histmersion/bin/histmersion-server --self-check     # macOS; on Linux: ~/.local/opt/histmersion/bin/…
```

It loads the settings and the plugins, looks up every secret, prints a report and exits: 0 when all
is well, 2 when a plugin failed, 3 when the settings are broken. It opens no database and no port.

## Setting an environment variable for the server

The installed server reads only its own environment:

- **macOS:** the LaunchAgent's `EnvironmentVariables` in
  `~/Library/LaunchAgents/com.histmersion.server.plist`. Add an entry, then stop and start the
  service so it reads the file again:

    ```bash
    plutil -insert EnvironmentVariables.HISTMERSION_CORE_KEY -string '<the key>' \
      ~/Library/LaunchAgents/com.histmersion.server.plist
    just stop && just start
    ```

    A re-install **keeps** every `HISTMERSION_*` entry and `ANTHROPIC_API_KEY` / `OPENAI_API_KEY`,
    and **drops** any other name. So name your own variables `HISTMERSION_…`.

- **Linux:** an `Environment=` line in the service (`systemctl --user edit histmersion` opens a
  drop-in), then `systemctl --user daemon-reload` and `just restart`.

A `.env` file in the repository is read by `just` only, not by the installed server.

## Password managers (vaults)

Three vaults are built in, all **off** by default. Each runs the vault's own command-line tool (an
argument list, never a shell), with a time-out, and reads the value from its output. No master
password is stored.

### KeePassXC

Needs `keepassxc-cli` (it comes with KeePassXC).

```hocon
histmersion {
  plugins.keepassxc {
    enabled  = true
    database = "/path/to/research.kdbx"
    # how to open it — any combination:
    key-file = "/path/to/research.keyx"            # optional
    password = "env:HISTMERSION_KEEPASSXC_PASSWORD"  # optional, a secret reference itself
    yubikey  = ["2:12345678", "2:87654321", "1"]     # optional, see below
  }
}
```

Without `password`, the database is opened with `--no-password` (a key file or a YubiKey alone).

**YubiKey challenge-response.** `yubikey` is one `"<slot>[:<serial>]"` or a **list** of them, tried
in order — for example your main key and a backup key. If `ykman` (YubiKey Manager) is installed,
Histmersion first asks it which keys are plugged in and tries only those; with none plugged in it
fails at once. Without `ykman` every entry is tried in order. The log says "touch your YubiKey" at
each attempt; each attempt waits up to 30 seconds (`yubikey-timeout-seconds`). A wrong password or a
missing entry stops at once — another key cannot fix those. The key that opened the database last is
tried first next time. `ykman = ""` turns detection off.

### 1Password

Needs the 1Password CLI, `op`.

```hocon
histmersion {
  plugins.1password {
    enabled = true
    account = ""                                            # optional, with several accounts
    service-account-token = "env:HISTMERSION_OP_TOKEN"     # optional; otherwise the desktop app signs `op` in
  }
}
```

References: `1password:op://<vault>/<item>/<field>`.

### Bitwarden

Needs the Bitwarden CLI, `bw`.

```hocon
histmersion {
  plugins.bitwarden {
    enabled = true
    session = "env:HISTMERSION_BW_SESSION"   # the session `bw unlock` prints; without it the vault is locked
  }
}
```

References: `bitwarden:<item id or search>[#<field>]` (default field: `password`).

A vault's own secret (`password`, `session`, `service-account-token`) can live in **another** vault:
Bitwarden's session can be kept in KeePassXC, for example.

## Where you need credentials

| for | setting | notes |
|---|---|---|
| the **polite-pool contact** for Crossref, OpenAlex and Unpaywall | `acquire.contact-email` (`HISTMERSION_ACQUIRE_CONTACT_EMAIL`) | not a secret, but yours: an e-mail address the services ask for, sent only as their `mailto`/`email` parameter. Unpaywall is not asked without one. |
| **CORE** | `plugins.oa-core.api-key` | a secret reference, e.g. `"keepassxc:Histmersion/CORE#password"`. This **one setting enables both** CORE uses: the acquisition chain's PDF lookup and the `core` index of the new-source search (`histmersion candidates`). `acquire.core.api-key` (`HISTMERSION_ACQUIRE_CORE_API_KEY`), a plain key, still works for both. Without a key CORE is not asked. A personal key allows 1,000 tokens a day and 25 a minute (a simple query costs 1 token), so both uses share one budget: `acquire.core.daily-tokens` (900), of which searches may spend `acquire.core.search-daily-tokens` (600), at most `acquire.core.per-minute` (20) calls a minute. After CORE answers 429 or reports no tokens left, it is not asked until the time it names. `histmersion about` shows the day's spending |
| **Smithsonian Open Access** (`media import smithsonian`) | `plugins.media-smithsonian.api-key` | an api.data.gov key, as a secret reference. The plugin is off by default: set `plugins.media-smithsonian.enabled = true` too. Without a key it is listed but not asked |
| **Europeana** (`media import europeana`) | `plugins.media-europeana.api-key` | your **personal** Europeana API key, as a secret reference. Off by default: set `plugins.media-europeana.enabled = true` too. Without a key it is listed but not asked |
| **Semantic Scholar** (for `followups`) | `acquire.semantic-scholar.api-key` | optional; a key only raises the rate limit |
| the server's own **write key** | `auth.api-key` (`HISTMERSION_API_KEY`) | when set, every write must send `Authorization: Bearer <key>`; set it whenever the server listens beyond your computer |
| **Anthropic** API | `ANTHROPIC_API_KEY` | environment only |
| **OpenAI** API | `OPENAI_API_KEY` | environment only |
| a **local** OpenAI-compatible server | `HISTMERSION_AGENTS_LOCAL_API_KEY` | environment only, if your server wants one |
| a **plugin**'s service | its own block, `plugins.<id> { … }` | always a secret reference |

## Getting the keys

Where each key comes from, and what the sign-up looks like. All of these are free.

| service | where to sign up | what to expect |
|---|---|---|
| **api.data.gov** (used by the Smithsonian Open Access API) | [api.data.gov/signup](https://api.data.gov/signup/) | A short form: first name, last name and e-mail address, plus an optional "How will you use the APIs?" box. The key arrives **by e-mail**; there is no account page to come back to, so keep that e-mail. The form is loaded by a script: if the page stays on "Loading sign up form…", allow the site in your content blocker or open it in another browser. One key covers every api.data.gov service. |
| **CORE** | [core.ac.uk/services/api](https://core.ac.uk/services/api) → *Register for an API key* | Enter your e-mail address, then pick a profile: **Academic**, **Non-academic** or **Personal**. The form asks for first and last name, e-mail, country and a description of your use (at least 150 characters), and you confirm CORE's terms. CORE first e-mails a link to confirm your address; after you open it, the key arrives in a **second e-mail**. A **Personal** key (1,000 tokens a day, 25 a minute; a simple query costs 1 token) is enough for Histmersion on one computer, but it certifies that the research is your own, unfunded and not for an organisation, and that you build no public service on CORE: if your Histmersion server is reachable by others, or the work is for an institution, register as Academic or Non-academic instead. |
| **Europeana** | [europeana.eu/en/account/api-keys](https://www.europeana.eu/en/account/api-keys) | Needs a Europeana account first: the page asks you to log in or **Join**. The join form asks for an e-mail address, a username (it may be shown publicly), first and last name and a password of at least 8 characters, and you confirm you are at least 13 and accept the terms of use. Europeana then sends a confirmation e-mail; open its link, and the API keys page offers a **personal** or a **project** key. Request a **personal** key: each Histmersion user uses their own. |
| **Flickr** | [flickr.com/services/api/keys/apply](https://www.flickr.com/services/api/keys/apply/) | **Paid accounts only** (as of October 2026): Flickr has turned off key creation for free accounts and offers it to Flickr Pro subscribers. Histmersion has no Flickr provider; Flickr photographs are counted, not taken. |

Store each key as a secret reference (an `HISTMERSION_…` environment variable of the server, or a
vault entry), never in the settings file or a repository.

For example, with both keys kept in KeePassXC — one entry per key, the key in the entry's password
field — the settings file (`histmersion.conf`) holds only the references:

```hocon
histmersion {
  plugins {
    keepassxc {
      enabled  = true
      database = "/path/to/research.kdbx"
    }
    media-smithsonian {
      enabled = true
      api-key = "keepassxc:Histmersion/api.data.gov#password"
    }
    media-europeana {
      enabled = true
      api-key = "keepassxc:Histmersion/Europeana#password"
    }
  }
}
```

Then `histmersion config reload` (no restart needed). A plugin whose reference cannot be resolved is
reported as failed, naming the reference, by `histmersion-server --self-check` (above), and its
collection is not offered; `histmersion media providers` lists the two collections, and says
"NO KEY SET" for one that has no key. Each key is sent only as its API's `X-Api-Key` header, never in a URL. Start with a dry
run on one place: `histmersion media import europeana --dry-run --limit 5`.

### Logging in to the command-line AI tools

The `claude-code`, `codex` and `opencode` providers run **your own** installed tool on **your own**
login. Histmersion never reads or stores that login; you sign in through the tool's own flow:

- **Claude Code:** sign in with `claude` (then `/login`), or `claude setup-token`. To keep the
  server's runs apart from your own sessions, give it a configuration folder of its own
  (`agents.providers.claude-code.config-dir`) and log in there once. An `ANTHROPIC_API_KEY` in the
  environment would bill the API instead, so the server removes it from the tool's environment unless
  `use-api-key = true`.
- **Codex:** the server's runs use their own Codex home, `<data>/agents/codex/home` (or
  `agents.providers.codex.home`). Log in there once: `CODEX_HOME=<that folder> codex login`.
- **opencode:** uses its own sign-in; with `chatgpt-login = true` its `openai/…` models draw on your
  ChatGPT plan.

`histmersion agent doctor` checks every provider — keys, the free model listing, the CLI tools and
their logins — without spending anything. More in [AI providers, budgets and quotas](ai-providers.md).

!!! note "Your subscription is for you"
    A subscription login is for your own, personal use. The server refuses to use one while it
    listens beyond your own computer, so that nobody else can spend your plan. Read your plan's terms
    before you let the server use it.
