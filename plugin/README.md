# hist-db — Claude Code plugin

Installs the four hist-db skills and the MCP server connection into a workspace, so an agent working
in (for example) the research wiki can query the corpus instead of grepping markdown files.

| component | what |
|---|---|
| MCP server `hist-db` | `search`, `get_source`, `geo_when`, `neighbors`, `ping` — all returning Markdown |
| skill `histdb-query` | how to answer a research question: search → narrow → `get_source` → `neighbors` |
| skill `histdb-ingest` | getting sources in and keeping them enriched |
| skill `histdb-compile` | the writing rules (structure, citation, contradiction-hunting, banned filler) |
| skill `histdb-dev` | build/test/native-image workflow, for changing hist-db itself |

The skills are **symlinks** to `../../skills/` in this repo — one canonical copy, shared by Claude
Code, OpenCode and this plugin. Edit `skills/<name>/SKILL.md` at the repo root, never the symlink.

## Prerequisites

The hist-db server must be running, and `plugin/.mcp.json` must point at it. Both are handled by the
installer, which writes `plugin/.mcp.json` from the configured public URL:

```bash
cd /path/to/hist-db
./install.sh
```

Check it: `curl -fsS "$HISTDB_PUBLIC_URL/api/health"` (or `make health`).

## Install (verified on Claude Code, this machine)

hist-db ships its own **local marketplace** (`.claude-plugin/marketplace.json` at the repo root).

```bash
# from the workspace you want to use it in — e.g. ~/Documents/research
claude plugin marketplace add /path/to/hist-db
claude plugin install hist-db@hist-db
```

Then restart Claude Code (or start a new session) so the MCP server and skills load.

Notes from verifying these commands:

- The marketplace source **must** be an absolute path or start with `./`. A bare `.` is rejected with
  *"Invalid marketplace source format"*.
- `claude plugin install` takes `plugin@marketplace`; both are named `hist-db`, hence `hist-db@hist-db`.
- Scope defaults to `user` (all workspaces). Use `--scope project` to declare it in the workspace's
  checked-in settings, or `--scope local` for machine-local settings that are not committed. The same
  flag is needed to uninstall: a plugin installed with `--scope local` needs
  `claude plugin uninstall hist-db@hist-db --scope local`.
- Removing the marketplace (`claude plugin marketplace remove hist-db`) also removes plugins installed
  from it.

## Verify

```bash
claude plugin list                  # hist-db@hist-db, enabled
claude plugin details hist-db       # 4 skills, 1 MCP server
claude plugin validate /path/to/hist-db/plugin
```

`claude plugin validate` reports one warning — that four entries are symlinks and were not followed.
That is expected and harmless: a real session *does* follow them, which `claude plugin details` proves
by listing all four skills. Validate the targets separately:

```bash
claude plugin validate /path/to/hist-db/skills          # passes, including --strict
```

Because of that warning, `claude plugin validate /path/to/hist-db/plugin --strict` **fails** (strict
treats warnings as errors). Validate `skills/` with `--strict` instead; the plugin manifest and the
marketplace manifest both pass strict on their own.

## Using it in the research workspace

Installing the plugin does **not** modify anything in the research workspace's own files — it only
adds a marketplace/plugin entry to the settings scope you chose. The workspace's existing skills keep
working; over time they migrate to `histdb`/MCP calls (`docs/plan.md` §5).

## OpenCode

OpenCode does not use this plugin directory. It reads the same skills through the
`.opencode/skill → ../skills` symlink in the hist-db repo (OpenCode 1.18.23 globs
`{skill,skills}/**/SKILL.md`, so either directory name is discovered). Configure the MCP server in
`~/.config/opencode/opencode.json` using the same URL as `plugin/.mcp.json`.
