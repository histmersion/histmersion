# Installing

Histmersion installs as one folder — the **bundle** — that holds everything it needs: the server,
the `histmersion` command, its own database (PostgreSQL 18 with PostGIS 3.6, for maps) and its own
text-recognition program. Nothing is installed system-wide, nothing runs in a virtual machine, and
you need no administrator rights.

It runs on **macOS 13.3 (Ventura) or later on Apple Silicon** and **Linux (x64 or arm64)**: the bundled PostgreSQL 18.6 is built for macOS 13.3.

## What goes where

| | macOS | Linux |
|---|---|---|
| the program (the bundle) | `~/Applications/Histmersion/` | `~/.local/opt/histmersion/` |
| your data: database, logs, backups, uploads, plugins | `~/Library/Application Support/Histmersion/` | `~/.local/share/histmersion/` |
| starts it when you log in, restarts it if it stops | `~/Library/LaunchAgents/com.histmersion.server.plist` | `~/.config/systemd/user/histmersion.service` |
| the `histmersion` command | `~/bin/histmersion` | `~/.local/bin/histmersion` |
| your settings | `~/.config/histmersion/histmersion.conf` | `~/.config/histmersion/histmersion.conf` |

The installer writes a settings file with comments and no settings the first time, and never
overwrites it afterwards. [Configuration](../reference/configuration.md) explains it.

## Option A: install a release archive

If you have an unpacked release archive of the bundle, run its installer with the folder it was
unpacked to:

```bash
scripts/bundle/install-macos.sh <bundle-dir>      # macOS
scripts/bundle/install-linux.sh <bundle-dir>      # Linux
```

`--help` prints every option.

## Option B: build it from the source code

You need [sdkman](https://sdkman.io) (it installs sbt), [uv](https://docs.astral.sh/uv/) and
[pixi](https://pixi.sh), and [just](https://just.systems) to run the recipes. Then, in a checkout of
the repository:

```bash
sdk env install        # sbt (.sdkmanrc)
just toolchain install # the pinned Java toolchain, GraalVM CE 25.4: downloaded once (~340 MB),
                       # checked against its pinned SHA-256, unpacked into the data folder's toolchain/
just install           # builds dist/histmersion (~3.3 GB, of which the OCR models are ~1.3 GB),
                       # installs it, starts it
```

Then open <http://localhost:8000>.

## How the server program is made on your machine

The server is a *native* program: compiled ahead of time for your processor, so it starts fast.
By default the installer **links** (assembles) that program **on your machine**,
from parts that ship in the bundle (the *relink kit*), together with any [plugins](../plugins/installing.md)
you added. That is why a plugin can become part of it.

To do that the installer:

1. checks for a C toolchain (macOS: `xcode-select --install`; Linux: `build-essential zlib1g-dev`),
   enough memory (`HISTMERSION_RELINK_MIN_MEMORY_GB`, default 12; about 16 GB in practice) and disk;
2. downloads GraalVM CE 25.4 **once** from its official release (~340 MB on macOS, ~350 MB on
   Linux), checks it against a pinned SHA-256 checksum, and unpacks it into the data folder's
   `toolchain/` (~640 MB) — the same copy a build from source uses. GraalVM CE 25.4 has no build
   for Intel Macs: there the installer falls back to the bundle's ready-made server;
3. links the server — about 2 minutes and ~13.5 GB of memory on a recent laptop.

You do not have to let it:

| you want | run | what happens |
|---|---|---|
| the default | `just install` | the server is linked on this machine (or the existing one is reused when nothing changed) |
| no toolchain, no download | `just install --prebuilt` (or `HISTMERSION_BUILD=prebuilt`) | the bundle's ready-made server is used as it is; it cannot hold your own plugins |
| no native program at all | `just install --jvm` | the same parts run on a Java runtime: more memory, a slower start, but plugins load at each start |

The same flags work on the release installers (`install-macos.sh <dir> --prebuilt`).

**It never fails because of the link.** If this machine cannot link the server — too little memory,
no C toolchain, a failed download or checksum, a failed self-check — the installer says why and goes
on with the bundle's ready-made server. `histmersion plugin rebuild` tries again later.

**Nothing is linked twice.** When linking would give the same program as before (same kit, same
plugins, same machine), the existing one is kept.

## Starting, stopping, logs

```bash
just restart        # macOS: launchctl kickstart -k gui/$(id -u)/com.histmersion.server
                    # Linux: systemctl --user restart histmersion
just stop           # until the next login, or `just start`
just logs           # follow the server log (also: just logs postgres, just logs converter)
histmersion health  # is it up? database and schema version
```

The logs are `<data>/logs/server.log`, `postgres.log` and `converter.log`, where `<data>` is the data
folder from the table above.

On Linux the service runs while you are logged in; `loginctl enable-linger "$USER"` keeps it running
after you log out.

## What runs, and where it listens

The server starts and stops everything else itself. On its first start it creates the database,
then runs it (on `127.0.0.1:5433`) and the text-recognition program (on `127.0.0.1:8100`) as its own
child processes. The server itself listens on `127.0.0.1:8000` — only your own computer can reach it.

To reach it from other devices on your network, set `HISTMERSION_BIND=0.0.0.0` **and**
`HISTMERSION_API_KEY` (a password every write must send) in the service's environment (macOS: the
LaunchAgent's `EnvironmentVariables`; Linux: an `Environment=` line), then restart. Do this only on a
network you trust.

## First-run data

On its first start the server imports, as a background job, the **gazetteer** that ships with it
(Natural Earth, Pleiades, and traced Bronze- and Iron-Age states) and the timeline's **period
presets** (a selection from PeriodO). The web page works meanwhile; the map fills in when the job
is done — a minute or two. `histmersion jobs` shows it; `HISTMERSION_INIT_AUTO=false` turns the
automatic import off, and `histmersion init` runs it on demand. See
[Open data](../how-to/open-data.md) for what else you can import.

## Removing it

```bash
just uninstall           # removes the program, keeps your data
just uninstall --purge   # removes your data too
```

Next: [First steps](first-steps.md).
