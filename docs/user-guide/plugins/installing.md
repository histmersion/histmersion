# Installing plugins

A **plugin** is a separate package (a `.jar` file) that adds to Histmersion. One plugin can add any of:

| it adds | used by |
|---|---|
| a **source fetcher** — a place to get papers from | `histmersion ingest`, `archive import` (the acquisition chain) |
| a **media collection** — a picture provider | `histmersion media import <name>` |
| an **event catalogue** | `histmersion events import <name>` |
| a **secret vault** — a password manager | secret references `<vault>:<ref>` |
| an **AI provider** | the agents' `provider:model` references |

Many of Histmersion's own parts are built-in plugins: the seven acquisition steps (`archive-org`,
`oa-unpaywall`, `oa-core`, `oa-openalex`, `oa-landing`, `pmc`, `arxiv`), the eight picture
providers, the three event catalogues, the three vaults (`keepassxc`, `1password`, `bitwarden`) and
the six AI providers. Each is switched on or off with `histmersion.plugins.<id>.enabled`.

!!! danger "Install only plugins you trust"
    A plugin runs **inside the server**, with the server's access to your collection and your
    network. There is no sandbox. Install a plugin only from a source you trust, check what
    `plugin add` shows you before you confirm, and prefer signed plugins (below).

    A plugin also declares on what terms its source fetchers obtain copies — **open**,
    **subscription** (your own access) or **unauthorised**. The server decides from that alone what
    may run: an unauthorised fetcher runs only if you set `acquire.allow-unauthorised = true`, and
    never on the legal path (a watcher's automatic ingest, an archive.org import). None ships with
    Histmersion. Respect the terms of every service a plugin talks to
    ([Responsible use](../responsible-use.md)).

## The commands

These manage files **on this computer** and never touch the database.

A third-party plugin is a jar compiled against the plugin API and the plugin platform (the libraries
the server provides; writing one: [Developing plugins](developing.md), which starts from
`histmersion plugin new`), with its provider in `META-INF/services/com.histmersion.plugin.Plugin`, its
defaults in `META-INF/histmersion/plugins/<id>.conf` and its own
`META-INF/native-image/<group>/<artifact>/reachability-metadata.json`. It is published with a **POM**
that lists the libraries it needs beyond the platform. `modules/plugin-fixtures/hello` is a tiny one
(a test fixture). Plugins live in the data directory's `plugins/`, one jar each with its POM beside it,
and their dependencies in `plugins/lib/`:

```bash
histmersion plugin add org.example::portal-plugin:1.2.0            # Maven (group::artifact = the Scala 3 artifact _3)
histmersion plugin add org.example::portal-plugin:1.2.0 --repo ~/src/portal/repo   # …from a repository of your own first
histmersion plugin add ~/Downloads/portal-plugin-1.2.0.jar        # a local jar, copied in (with portal-plugin-1.2.0.pom beside it)
histmersion plugin add https://example.org/portal-plugin-1.2.0.jar # downloaded, with the .pom beside it; its SHA-256 is shown (--sha256 <hex> to pin it)
histmersion plugin list                                            # what is in plugins/, and whether the binary has it
histmersion plugin status                                          # rebuild needed? (the jars, POMs and lib/ vs what the binary was linked with; the running server)
histmersion plugin rebuild                                         # relink, swap, restart, check health
histmersion plugin rollback                                        # the binary before the last relink
histmersion plugin rm portal-plugin
```

`add` shows what the jar is before installing it — the plugin's id, version, plugin SPI version, what it
registers, the **access** its source fetchers declare (open, subscription, unauthorised) and the
**dependencies** it installs — and refuses one built for another SPI version; an older version of the
same plugin is replaced. Maven coordinates are fetched (the jar and its POM) from the repositories named
with `--repo` (a directory or a URL; repeatable), then from each of `cli.plugins.maven-repos` in turn, each
file checked against the SHA-256/SHA-1 the repository publishes.

**Dependencies.** The libraries a plugin's POM lists are resolved with coursier, together with what the
server itself ships: a library the server has is not installed again (its copy is used), every other one
is downloaded from the same repositories and kept in `plugins/lib/`, and the relink links it in beside the
plugin (JVM mode puts it on the class path). A plugin that needs a **newer** version of a library than the
server ships is refused, naming the library and both versions: one process cannot load two versions of a
library, and the server's would win. The list of what the server provides to plugins, and at which
versions, is the plugin platform ([Developing plugins](developing.md#the-plugin-platform)). Downloads go
through coursier's cache, so a later relink needs no network. A jar without a POM is installed as having
no dependencies; if it does need a library, it fails to register with "class … is missing".

Dropping a jar (and its POM) into `plugins/` by hand works too: the next relink, or the next start in
JVM mode, resolves its dependencies. These commands manage files on
**this** machine and run the bundle's scripts; they never touch the database, and they refuse when
`histmersion` points at a server elsewhere. Describing a jar runs its code once in a short-lived JVM (no
sandbox exists for a plugin: it runs in the server's process — install only what you trust).

### Relinking

A native binary cannot load classes at run time, so a changed plugin set needs a new link;
changing a plugin's *settings* (`histmersion.plugins.<id>`, `enabled` included) needs only `histmersion
config reload`. `bin/histmersion-relink` (what `plugin rebuild` runs) re-runs GraalVM `native-image` over
the bundle's `kit/` — the server's Sloth-patched jars, their order and native-image flags, `kit.json` +
`SHA256SUMS` — plus the plugin jars: no sbt, no compile. It locks, checks the kit's checksums, the
toolchain (`histmersion-toolchain check`; `histmersion-toolchain install` provisions GraalVM CE), memory
(`HISTMERSION_RELINK_MIN_MEMORY_GB`, default 12) and disk; runs a **pre-flight on the JVM** (the plugins'
dependencies resolved into `plugins/lib/`, every jar has a provider of this SPI, no id or class twice
among the kit, the plugins and their dependencies, then the server's own `--self-check` with the plugins,
their dependencies and your config) before spending ~13.5 GB; Sloth-patches the plugin jars and their
dependencies; links; runs the **new binary's
`--self-check`** (never as a server) and requires the directory's plugin-set digest and every new plugin
registered; signs it; swaps it in keeping the old one as `bin/histmersion-server.prev`; restarts the
service and waits for `/api/health` and the expected digest in `/api/about` — else the previous binary
goes back. Exit codes: 2 refused before building, 3 the build or the self-check failed (nothing changed),
4 rolled back. Log: `<data>/logs/relink.log`. `/api/about`, `histmersion about` and `--self-check` report
the binary's **plugin set** (`plugin set: <digest> · 1 jar (native): …`, or first-party plugins only).

### JVM mode

**JVM mode** (`histmersion plugin rebuild --jvm`) is the fallback when no native link is possible: the
same kit and plugins on a JDK (`bin/histmersion-server-jvm`, `HISTMERSION_JVM_OPTS` for heap options),
plugins found at every start — adding one then needs a restart, not a relink. Their dependencies come
from `plugins/lib/`; when the plugin jars or POMs changed since it was resolved, it is resolved again
before the start. `plugin rebuild` returns to a native binary.

### Repairing an install: relink kits

Every bundle carries its kit, so a relink never needs the source. A release also
publishes each platform's kit on its own, next to the bundle: `histmersion-<tag>-<platform>-kit.tar.zst`
(~100 MB: `kit/`, `libexec/` and the relink scripts), its `.sha256`, and its `kit.json` as `-kit.json`
(version, commit, GraalVM version, plugin SPI, every file's SHA-256). A manual run of the `bundle` workflow
keeps the same three as a workflow artifact for 7 days. Use it to repair an install whose kit no longer
matches its `SHA256SUMS` (the relink refuses it) without downloading the ~1 GB bundle again — always the
kit of the version you have installed (`histmersion about` shows it; `kit.json` names its own):

```bash
shasum -a 256 -c histmersion-v1.2.0-macos-arm64-kit.tar.zst.sha256
zstd -dc histmersion-v1.2.0-macos-arm64-kit.tar.zst | tar -C ~/Applications/Histmersion -xf -
histmersion plugin rebuild
```

### Signing and GPG verification

Signing is optional. Without it binaries are ad-hoc signed, as before. `histmersion-signing
setup-macos` creates a self-signed code-signing identity in your login keychain (only when you run it,
after asking): relinked binaries then keep one designated requirement, so macOS privacy permissions
survive relinks. The identity is left untrusted, so your keychain's trust settings are not changed: a
check with a real privacy prompt (2026-10-23) showed a permission granted to a binary it signed survives
a relink all the same. `setup-macos --trust` also trusts it for code signing (macOS asks for your login
password). `histmersion-signing use-gpg <key id>` (or `generate-gpg`) turns on GPG provenance:
`plugin add` requires a valid signature (`<jar>.asc`; the signer's key is pinned per plugin id on first
use), the plugins directory's manifest (the plugin jars, their POMs and the dependency jars in
`plugins/lib/`) is signed and verified before every relink and every JVM-mode start (a jar dropped in
afterwards is refused until you `plugin add` it), and each relinked binary's
SHA-256 is signed. Passphrases stay with gpg-agent; only the key's fingerprint is stored
(`<data>/signing/gpg-key`). `histmersion-signing status` shows what is set up.

With GPG provenance on, **the service verifies the server binary at every start**: `use-gpg` signs the
installed binary's SHA-256 (and the previous one's) and points the LaunchAgent / systemd unit at
`libexec/histmersion-server-verified`, which starts `bin/histmersion-server` only when its SHA-256 is the
signed one, and otherwise logs why to `server.log` and does not start it (exit 78; the service manager
retries, so a fix is picked up by itself). Relinks, JVM mode and rollbacks carry the signed record with
their binary; a rollback to a binary without one is refused. `histmersion-signing sign-binary` signs the
installed binary when you trust it (e.g. after a manual change); `off-gpg` points the service back at the
binary. Without GPG provenance the service starts the binary directly, exactly as before. Installers make
the same choice; if they cannot sign (no secret key, pinentry cancelled) they start the binary unverified
and say so.

`scripts/bundle/tcc-check.sh` is the check that settled it (`start`, click Allow, `relink`, `control`,
`cleanup`), with a throw-away probe and LaunchAgent, never the server's; run it again to confirm on
another macOS version.


## Install a plugin from its source repository

A plugin you have as source, such as one `histmersion plugin new` wrote or a scala-cli project cloned from
its repository, is built, published into a directory and installed from there. You need an installed
Histmersion (its `histmersion` CLI and the server), [scala-cli](https://scala-cli.virtuslab.org), and a JDK:
use the one Histmersion is built with, GraalVM CE 25.4 (`sdk install java 25.4.4.1+1-graalce`, then point
`JAVA_HOME` at it). The Histmersion source repository is not needed: the project reads the plugin API, the
platform and the testkit from the local Maven repository (`//> using repository m2Local`,
`~/.m2/repository`), into which the installer copies the install's; `histmersion plugin repo --m2` copies
them again (after an update, say). From the plugin's directory:

1. **Build and test.** Offline: the unit tests run, and recorded live checks are replayed.

    ```bash
    scala-cli test .
    ```

2. **Publish** the jar, its sources and its POM (which lists the plugin's own libraries) into `./repo`, a
   Maven repository on disk:

    ```bash
    scala-cli --power publish . --publish-repository ./repo
    ```

3. **Install it** by the coordinates its `project.scala` publishes it under
   (`publish.organization::publish.name:publish.version`). `plugin add` shows the id, version, SPI, what it
   registers, the access its fetchers declare and its dependencies, then copies the jar and its POM into the
   plugins directory and installs the POM's dependencies beside it (`--yes` skips the question):

    ```bash
    histmersion plugin add <group>::<name>:<version> --repo ./repo
    ```

4. **Enable it** in `histmersion.conf`, once:

    ```hocon
    histmersion.plugins.<id>.enabled = true
    ```

5. **Relink the server with it.** A native server runs only the plugins it was linked with, so it is linked
   again with the plugins directory, self-checked, swapped in, restarted and health-checked; on failure the
   previous binary comes back. The link peaks near 13.5 GB of memory. Let running jobs finish first:

    ```bash
    histmersion agent autopilot drain      # wait until `histmersion agent autopilot status` says DRAINED
    histmersion plugin rebuild             # or: plugin rebuild --jvm — no relink, the server runs on the JVM
    histmersion agent autopilot undrain
    ```

6. **Check** with `histmersion plugin status`: the running server lists the plugin's jar
   (`… 1 jar (native): <name>-<version>.jar`). The server log names the plugin as it registers it.

**Updating.** Raise `publish.version` in `project.scala` (and the version in the plugin's `PluginInfo`), then
repeat steps 1–3 with that version, and step 5. `histmersion plugin status` says REBUILD NEEDED whenever the
plugins directory differs from what the running server was linked with.

**Removing.** `histmersion plugin rm <id> --rebuild` takes it out of the plugins directory and relinks; set
`enabled = false` too, or the server warns that the setting configures no plugin.

## Plugins and secrets

Plugins add to the server through one service (`com.histmersion.plugin.Plugin`, module
`modules/plugin-api`; the design is [`docs/research-plugins.md`](https://github.com/histmersion/histmersion/blob/master/docs/research-plugins.md)). Each one has
its own block, `histmersion.plugins.<id> { … }`, layered over the defaults it ships
(`META-INF/histmersion/plugins/<id>.conf`) with the environment over both. The built-in ones are the
three secret vaults above, all disabled by default, the two media collections that need a key of your
own (`media-smithsonian`, `media-europeana`), also disabled by default, and — all enabled — the seven
acquisition plugins, the eight keyless media collections (`media-wikidata`, `media-depicts`, `media-met`,
`media-cleveland`, `media-aic`, `media-loc`, `media-pleiades`, `media-opencontext`), the three event catalogues
(`events-gvp`, `events-noaa`, `events-wikidata`) and the six AI providers (`ai-anthropic`, `ai-openai`,
`ai-local`, `ai-claude-code`, `ai-codex`, `ai-opencode`).

**Acquisition runs through plugins.** `ingest <doi>` and `archive import` fetch through one chain of
*source fetchers* that plugins register, asked in `acquire.chain` order until one has a copy. The built-in
plugins and their fetchers: `archive-org` (`archive-org`: a public archive.org item; a lending-only one
is refused), one plugin per open-access index — `oa-unpaywall` (`unpaywall`), `oa-core` (`core`),
`oa-openalex` (`openalex`), `oa-landing` (`landing`: repository landing pages) — `pmc` (`pmc`,
`pmc-text`) and `arxiv` (`arxiv`); `histmersion.plugins.<id>.enabled = false` takes one plugin's
fetchers out, and the chain keeps its order. Each
fetcher declares its access — open, subscription, or *unauthorised* — and the server alone decides what
runs: an unauthorised fetcher (none ships with Histmersion) runs only when `acquire.allow-unauthorised =
true`, and never on the legal path (a watcher's auto-ingest, an archive.org import). The server also
checks that every "PDF" is one, and logs the chain it runs at startup.

**Media collections and event catalogues are plugins too.** `media import <name>` runs a collection a
plugin registered and `events import <name>` a catalogue (`histmersion media providers`,
`histmersion events catalogues` list them). A plugin's collection only searches and describes: it
returns each record with the licence its provider states, and the server reads that statement, judges
it against `media.collections` (the user's to widen per collection, never the plugin's), ties the record
to a place by its own match rules (names by the gazetteer's name key, uncertain values ignored, the dates
overlapping, the reason kept on the link), downloads, thumbnails and stores it. A plugin's catalogue
returns each part's records; the server checks each one, applies the years and thresholds, merges it
with the events there and records the part, so a second import reads only what is left. Neither may take
a name of the server's own providers.

**AI providers are plugins too.** The provider half of a `provider:model` reference (`agents.roles.*`,
`agent models set`, an allowance's target) names an AI provider in the plugins' registry. The six built-in
ones keep their settings under `agents.providers.<name>.*`; `histmersion.plugins.ai-<name>.enabled = false`
takes `<name>:*` models out of every chain (the chain passes them over, naming the switch) and `agent
doctor` says so. A plugin's provider answers one model call at a time; the server keeps the prompts, the
tool loop and the tools, the answer's validation, the budgets, allowances and approvals. Its models are
metered — price them in `agents.prices` (`0/0` for a free one) — and a reference to it is accepted only
while its plugin is registered. Its credentials are secret references in its own block.

A credential in a plugin's settings is never written as a bare value: it is a **secret reference**,
resolved when the plugin is registered and never stored, logged or returned:

| form | example | |
|---|---|---|
| `env:<NAME>` | `token = "env:PORTAL_TOKEN"` | a variable of the server's environment |
| `<vault>:<ref>` | `token = "keepassxc:Histmersion/Portal#password"` | asked of an enabled vault plugin (`keepassxc`, `1password`, `bitwarden`) |
| `value:<literal>` | `token = "value:…"` | allowed but discouraged: the server warns, and shows it as `value:***` |

`/api/about`, `/api/config` and `histmersion about` list every plugin — registered, disabled, failed
(with why) or refused (a plugin built for another SPI version, a duplicate id) — and its effective
settings with each secret as its reference. A plugin that fails contributes nothing and the server
still starts. `histmersion-server --self-check` loads the config and the plugins, resolves their
secrets, checks the time zones, prints that report and exits — 0 all good, 2 a plugin failed or was
refused or the time zones are missing, 3 the config is broken — without opening a database connection or a port; any other argument is an error (exit 1)
before anything is opened.

<figure markdown="span">
  ![The About window scrolled to its Plugins column: each plugin by id — 1password, the AI providers, archive-org, arxiv, bitwarden, the event catalogues, keepassxc, the media collections — with a green dot and "registered" or a grey dot and "disabled", its version, what it provides, and its settings](../assets/screenshots/about-plugins.png){ loading=lazy }
  <figcaption>The web page's About window lists the same: every plugin, its state, what it provides
  and its effective settings (the vaults are off until you enable them).</figcaption>
</figure>
