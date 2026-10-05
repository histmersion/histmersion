# Agent runtimes: one set of definitions for Claude Code, Codex and opencode (research, 2026-10-05)

The embedded engine (docs/research-embedded-agents.md §11–§15) runs pipelines over two kinds of model access:

- **direct API calls** through sttp-ai;
- the **`claude-code` provider**, which runs one `claude -p` process per step attempt against the read-only MCP endpoint `/mcp/agent`.

The direction from the user:

> "stay within quota (if on subscription); load skills and agents definitions: how to review, how to fix, how to communicate with histmersion, set allowed tools — virtually only MCP to histmersion, except maybe agents that should look up the web to find new sources; we want to reuse these between all agents, but we might partially generate them since e.g. how agents are defined and how we tell them to use skills and set permissions is per-AI specific"

> "store general definitions of skills and agents as resources, use them in startup to generate the plugins per each enabled tool (claude, codex, maybe opencode), run tools with generated plugins and settings — the plugin could live somewhere locally e.g. in histmersion directory"

This note establishes what each runtime supports (§1) and proposes the design (§2). Open questions that only a paid call can settle are in §3.

**Method.**
- Installed versions, checked locally: `claude` 2.1.289, `codex-cli` 0.160.0, `opencode` 1.18.34, all under `/opt/homebrew/bin`.
- Sources read on 2026-10-05:
  - the Claude Code docs (`code.claude.com/docs/en/*.md`) and the mods type declarations that 2.1.289 writes (`claude-code.d.ts`);
  - Codex source at tag `rust-v0.160.0` (`codex-rs/core/config.schema.json`, `agent-roles`, `plugin`, `core/src/agent/role.rs`) and `developers.openai.com/codex/*`;
  - opencode docs (`opencode.ai/docs/*`) and source at tag `v1.18.34` (`packages/opencode/src/cli/cmd/run.ts`, `packages/plugin/src/index.ts`).
- Only non-generating commands were run:
  - `--help`;
  - `claude plugin validate`;
  - `codex mcp list|get`, `codex features list` and `codex debug prompt-input` (it renders the model-visible input offline);
  - `opencode debug paths|skill|agent` and `opencode mcp list`;
  - one Codex `account/rateLimits/read` through `codex app-server`.
- No prompt was sent to any model, and no credential file was opened.
- **Untested** marks what could only be read, not run.

---

## 1. Per-runtime facts

### 1.1 Claude Code 2.1.289

**Definitions.**

| thing | format and location | notes |
|---|---|---|
| subagent | `agents/<name>.md`, YAML frontmatter + prompt body | Fields: `name`, `description` (required), `tools`, `disallowedTools`, `model`, `permissionMode`, `maxTurns`, `skills` (preloaded in full), `mcpServers`, `hooks`, `memory`, `effort`, `omitClaudeMd`, `isolation`, … (sub-agents.md). |
| where agents load from | managed > `--agents` JSON > `.claude/agents/` > `~/.claude/agents/` > plugin `agents/` | **Plugin agents ignore `hooks`, `mcpServers` and `permissionMode`** ("For security reasons, plugin subagents don't support…"). |
| skill | `skills/<name>/SKILL.md` | Fields include `description` (listing truncated at 1,536 chars with `when_to_use`), `allowed-tools`, `disallowed-tools`, `model`, `context: fork`, `agent`, `metadata`. |
| plugin | `.claude-plugin/plugin.json`, plus `agents/`, `skills/`, `hooks/`, `.mcp.json` | Plugin MCP tools are named `mcp__plugin_<plugin>_<server>__<tool>`. That is a different name from the same server configured directly (`mcp__histmersion__<tool>`). |
| MCP | `.mcp.json`, `~/.claude.json`, plugin `.mcp.json`, claude.ai connectors | Precedence: local > project > user > plugin > connectors. A plugin or connector pointing at an already-configured URL counts as a duplicate. |
| permissions | `settings.json` → `permissions.{allow,deny}`, `defaultMode` | `"mcp__*"` in deny removes every MCP tool. Allow globs only work after a literal `mcp__<server>__` prefix. |

**One run on a generated config, the user's config untouched.**

| flag / env | effect |
|---|---|
| `--plugin-dir <dir>` (repeatable) or `CLAUDE_CODE_PLUGIN_DIRS` | Loads a plugin folder for this session only. "A headless `claude -p` always loads fresh". |
| `--agent <plugin>:<name>` | The main thread *becomes* that agent: its `tools` restriction and `model` apply. |
| `--agents <json-or-file>` | Session-only agents. "with --print the path to a file". |
| `--settings <file-or-json>` | An extra settings layer, which can carry permissions, `disableAllHooks` and `disableClaudeAiConnectors`. |
| `--setting-sources` / `--restricted` | Leave out the user, project and local settings files. `--restricted` also strips the code-running tools and WebFetch unless `--tools` names them. |
| `--mcp-config <file>` + `--strict-mcp-config` | Only these MCP servers. **Untested:** whether a `--plugin-dir` plugin's own `.mcp.json` is also dropped. Servers passed inline through `--agents` are explicitly *not* filtered. |
| `--tools ""` / `--tools "WebSearch,WebFetch"` | Which built-in tools exist at all. |
| `--allowedTools` / `--disallowedTools` | Allow rules, or removal from context. |
| `--permission-mode dontAsk` | Anything not allowed is denied, never prompted. |
| `--disable-slash-commands` | Help text: "**Disable all skills**". The current backend passes it, so no skill reaches a `claude-code` step today. |
| `CLAUDE_CONFIG_DIR` | A whole separate home: settings, sessions, plugins, and its own login (Keychain entry keyed to the directory, iam.md). |
| `ENABLE_CLAUDEAI_MCP_SERVERS=false` | Keeps claude.ai connectors out. `--mcp-config` servers are unaffected. |

**Restricting a run to Histmersion's MCP, and whether it is enforced.** Combine:

- `--tools ""`, or `--tools "WebSearch,WebFetch"` for a discovery role;
- `--strict-mcp-config --mcp-config <gen>`;
- `--allowedTools mcp__histmersion__get_source,…`;
- `--disallowedTools` for the rest of the server's tools;
- `--permission-mode dontAsk`;
- `ENABLE_CLAUDEAI_MCP_SERVERS=false`.

This is **enforced by the harness**, not requested of the model:
- a tool that is not offered does not exist in the model's context;
- `dontAsk` denies anything else.

The stronger layer is server-side (§2.4): the endpoint serves only the role's tools.

**Quota.**

| surface | under `claude -p`? | source |
|---|---|---|
| `rate_limit_event` in `--output-format stream-json` | yes; `unifiedWindows` per window is `@internal` and **unobserved** | research-plan-quotas.md §1.2 |
| `StopFailure` hook, matcher `rate_limit` | yes (settings hooks); output ignored, side effects only | hooks.md |
| mods: `$.session.usage()` → `{rateLimits:[{kind:"five_hour"\|"seven_day", percentUsed, resetsAt}], cost, context}`; event `session.measure` "when a rate-limit window moves a whole point"; `$.turn.abort({turnId})`; `tool.call` hooks may return `{deny}`; `$.http.fetch` | **documented to load** under `-p` with `--plugin-dir` ("always loads fresh"); a `-p` run "names a `--plugin-dir` plugin whose module was not loaded … the switch being off included", so some switch can keep a module out. **Untested.** Marked "EARLY ACCESS: this surface may change between releases without notice". | `claude-code.d.ts` 2.1.289, plugin-authoring reference |
| status line `rate_limits` | no (TUI only) | statusline docs |

**What this allows.** A *quota-guard mod* shipped in the generated plugin can do three things:

- POST each `session.measure` snapshot to Histmersion;
- deny further `tool.call`s;
- abort the turn when Histmersion says the pool is below its floor.

The same mod, installed by the user in their **interactive** Claude Code, would report the human's spending from the shared pool. That is the "status-line tap" of research-plan-quotas.md §5.2, done with the user's own consent and without a file.

**Login.** These verdicts are from research-model-providers.md §1.2.
- **Subscription login.** It is permitted for the user's own unmodified binary on their own machine, signed in through Anthropic's flow.
  - Never read the Keychain or `.credentials.json`.
  - Strip `ANTHROPIC_API_KEY` and `ANTHROPIC_AUTH_TOKEN`, because in `-p` a present key wins.
  - It is not to be offered to other people, and is refused off loopback.
  - `--bare` never reads OAuth, so it is useless on a plan.
- **API key.** Commercial Terms, no restriction on scripting.

### 1.2 Codex CLI 0.160.0

**Definitions.**

| thing | format and location | notes |
|---|---|---|
| instructions | `AGENTS.md` (walked from the repository root to the cwd), `developer_instructions` (a developer-role message), `model_instructions_file` (replaces the built-in instructions; "STRONGLY DISCOURAGED") | Verified with `codex debug prompt-input`: `-c developer_instructions=…` becomes the first developer message. |
| custom agent (subagent role) | TOML, `~/.codex/agents/*.toml` (i.e. `$CODEX_HOME/agents/`) or `.codex/agents/*.toml`; required `name`, `description`, `developer_instructions` | The docs say a role file may also set `sandbox_mode`, `mcp_servers`, `skills.config`. The source (`core/src/agent/role.rs`) applies only `developer_instructions`, the model knobs, `features` and `skills`, and states "Roles may customize the child or reduce its capabilities, but never replace the parent session's authority". **Treat per-role MCP in a role file as unreliable.** |
| skill | `SKILL.md` + optional `agents/openai.yaml`, found in `$CODEX_HOME/skills`, `$CWD/.agents/skills` up to the repository root, `$HOME/.agents/skills`, `/etc/codex/skills` | Verified offline: skills in a scratch `$CODEX_HOME/skills` and `$CWD/.agents/skills` both appear in `<skills_instructions>`. Codex lists only name, description and path; **the model must read `SKILL.md` itself.** |
| plugin | `.codex-plugin/plugin.json` with `skills`, `mcpServers` (`.mcp.json`), `apps`, `hooks`; installed through `codex plugin marketplace add` / `codex plugin add` | No agents in a Codex plugin. |
| MCP | `[mcp_servers.<name>]`: `url`, `bearer_token_env_var`, `required`, **`enabled_tools`**, **`disabled_tools`**, `tools.<t>.approval_mode`, `default_tools_approval_mode` | Verified: `codex mcp get` shows `enabled_tools` applied. `required = true` makes start-up fail ("required MCP servers failed to initialize … HISTMERSION_AGENT_TOKEN … is not set"). |

**One run on a generated config.**
- `CODEX_HOME=<dir>` gives a whole separate home: config, skills, agents, rollouts, and auth. Logging in there is a `codex login` with that `CODEX_HOME`, never a copied `auth.json`.
- `-p/--profile <name>` "Layer[s] `$CODEX_HOME/<name>.config.toml` on top of the base".
  - Verified: a profile file holding only `[mcp_servers.histmersion] enabled_tools = [...]` merged over the base server (URL kept, tool list narrowed).
- `-c key=value` takes TOML values. `--ignore-user-config` skips `config.toml`, and "auth still uses CODEX_HOME". `--ignore-rules` skips execpolicy. `--strict-config` refuses unknown keys.
- **Running in an empty temporary cwd** keeps out project `AGENTS.md` and `.codex/` layers.
- Output:
  - `codex exec --json` emits `thread.started`, `turn.*`, `item.*`, `error` with token `usage`;
  - `--output-schema <file>` asks for a typed final answer;
  - `-o <file>` writes the last message.

**Restricting to MCP only.** Codex has no general tool allowlist. Restriction is assembled from these settings:

| setting | removes |
|---|---|
| `features.shell_tool = false` | `exec_command`/`write_stdin` (spec_plan_tests: "disabling_shell_tools_disables_command_tools_for_all_environments") |
| `web_search = "disabled"` (`"cached"` / `"live"` for discovery) | the web search tool |
| `features.{view_image,image_generation,browser_use,computer_use,apps,plugins,memories,tool_suggest} = false`, `agents.enabled = false` | the rest (verified in `codex features list` with a scratch home) |
| `skills.bundled.enabled = false`, `include_environment_context = false`, `include_permissions_instructions = false`, `include_collaboration_mode_instructions = false` | Injected context. Verified: the prompt input shrank to the developer message and the user message. |
| `sandbox_mode = "read-only"`, `approval_policy = "never"` | Writes. This also covers `apply_patch`, which may still be offered: **untested** whether it disappears with the shell. |
| `[mcp_servers.histmersion] enabled_tools = [...]` | the Histmersion tools the role may not use |

Enforcement is by the harness: tools are not registered, and MCP tools are filtered client-side.

Two caveats:
- `agents.enabled = false` was needed to drop the `<multi_agent_role>` block. `features.multi_agent = false` alone left it in place, because multi-agent v2 is on.
- With the shell off, **the model cannot open a `SKILL.md` that Codex lists**. Skill bodies must therefore be inlined into `developer_instructions` (§2.3). **Untested:** whether the `skill_search` feature supplies a loader tool.

**Quota.** `account/rateLimits/read` through `codex app-server` (stdio JSON-RPC: `initialize` → `initialized` → the read) **was run once**. It generates nothing; the server reads its own login. Result, today:

```
planType: prolite   ordinaryUsageAllowed: false   rateLimitReachedType: rate_limit_reached
codex:                 primary {usedPercent: 100, windowDurationMins: 10080, resetsAt: 2026-10-10 09:45 UTC}, secondary: null
base_model_inference:  ("gpt-reserve", gpt-5.6-luna) primary {usedPercent: 0, windowDurationMins: 10080, resetsAt: 2026-10-12 17:16 UTC}
credits: none; rateLimitResetCredits: 1 available ("Full reset", expires 2026-10-29)
```

Three consequences:

1. **The plan's weekly Codex pool is spent right now.** A Codex agent would be refused, or moved to the reserve model, until 10 October.
2. **There is no 5-hour window on this plan** (`secondary: null`; primary is 7 days). This confirms that windows must be keyed by `windowDurationMins`, never by "primary".
3. **There is a second bucket** (`base_model_inference`, a fallback model). A floor applies per `limitId`.

The response also offers a reset credit and an upsell CTA (`reset_usage`). Histmersion must never act on either: `account/rateLimitResetCredit/consume` stays forbidden.

Two further sources:
- rollout files: `token_count.rate_limits` in `$CODEX_HOME/sessions/**/rollout-*.jsonl`, not written with `--ephemeral`;
- Codex hooks (`PreToolUse` etc.) can deny a tool call. `--dangerously-bypass-hook-trust` is needed for untrusted hooks, so a generated home should mark its hooks trusted rather than bypass.

**Login.**
- ChatGPT plan: OpenAI documents seeding a runner's auth "for trusted runners … needing ChatGPT rate limits".
- API key: `CODEX_API_KEY` per run.
- Terms: research-plan-quotas.md §3 (reading limits and pausing honours them; never touch `auth.json`).

### 1.3 opencode 1.18.34

**Definitions.**

| thing | format and location | notes |
|---|---|---|
| config | JSON. Merged in this order: remote → `~/.config/opencode/opencode.json` → `OPENCODE_CONFIG` → project `opencode.json` → `.opencode/` → `OPENCODE_CONFIG_CONTENT` → managed | Configs are **merged, not replaced**. There is no "ignore the user's config" flag. |
| agent | Markdown in `agents/` (under `.opencode/`, `~/.config/opencode/`, or `OPENCODE_CONFIG_DIR`); frontmatter `description`, `mode` (`primary`/`subagent`), `model`, `temperature`, `steps`, `permission`, `prompt` | Verified: a generated `agents/article-skeptic.md` with `permission: {"*": deny, histmersion_get_source: allow, …}` resolved (`opencode debug agent`) to `bash`, `read`, `glob`, `grep`, `task`, `webfetch`, `skill` … all **false**. |
| skill | `skills/<name>/SKILL.md`, from `.opencode/skills`, `~/.config/opencode/skills`, **`.claude/skills`, `~/.claude/skills`**, `.agents/skills`, `~/.agents/skills` | Recognised fields: `name` (must match the folder), `description` (≤ 1,024 chars), `license`, `compatibility`, `metadata` (string→string). All 19 Histmersion skills are under 510 characters. `OPENCODE_DISABLE_CLAUDE_CODE=1` stops the `.claude` reads. |
| MCP | `"mcp": {"histmersion": {"type":"remote","url":…,"headers":{…}}}` | Tools are named `<server>_<tool>`, e.g. `histmersion_get_source`. Verified connected: `opencode mcp list`. |
| permissions | `permission` keyed by tool name, wildcards, **last match wins**; `allow`/`ask`/`deny` | `opencode run` (non-interactive) **auto-rejects every `ask`** (run.ts: "permission requested … auto-rejecting"), and denies `question`/`plan_*`. |

**One run on a generated config.**
- `XDG_CONFIG_HOME=<gen>` moves the global config. Verified: `opencode debug paths` showed `config` moved while `data` (where `auth.json` lives) stayed in `~/.local/share/opencode`.
- Add `OPENCODE_CONFIG_DIR=<gen>/opencode`, `OPENCODE_DISABLE_CLAUDE_CODE=1`, `OPENCODE_DISABLE_AUTOUPDATE=1`, and an empty temporary cwd.
- Then `opencode run --agent <role> --format json [-m provider/model] < prompt`.
- `--format json` emits `step_start`, `text`, `reasoning`, `tool_use`, `step_finish` (the part carries tokens and cost) and `error`.
- `--pure` runs without external plugins.

**Restricting to MCP only.**
- Set `"permission": {"*": "deny", "histmersion_<tool>": "allow", …}` in the agent.
- For a discovery role, add `webfetch`/`websearch: allow` (websearch needs Exa: `OPENCODE_ENABLE_EXA`).
- Denied tools are removed from the agent's tool map.
- For a second gate, a plugin's `tool.execute.before` hook can throw.

This is enforced by the harness.

**Quota.**
- opencode has no plan-quota surface.
- Usage is in `step_finish` and `opencode stats`.
- Logins are API keys, OpenCode Zen/Go, GitHub Copilot, or **ChatGPT Plus/Pro** (OAuth).
- Anthropic's plan is **not** usable: the docs say "Anthropic explicitly prohibits this" and the plugins were removed in 1.3.0.
- **Untested:** whether opencode's ChatGPT login draws on the same Codex pool. If it does, the Codex app-server read above is its quota too.
- **Terms:** a ChatGPT login inside a third-party client is OpenAI's call. OpenAI does not object publicly, and opencode advertises it. The same "no automated extraction except via the API" clause applies as for Codex (research-plan-quotas.md §3).

### 1.4 Summary

| | Claude Code | Codex | opencode |
|---|---|---|---|
| point one run at generated config | `--plugin-dir`, `--agent`, `--settings`, `--strict-mcp-config --mcp-config`, `CLAUDE_CONFIG_DIR` | `CODEX_HOME`, `-p <profile>`, `-c`, empty cwd | `XDG_CONFIG_HOME`, `OPENCODE_CONFIG_DIR`, `--agent`, empty cwd |
| MCP-only, enforced by harness | yes (`--tools ""`, allow list, `dontAsk`) | yes (`shell_tool=false`, `web_search=disabled`, `enabled_tools`, read-only sandbox); `apply_patch` untested | yes (`"*": deny` + allow list; `ask` auto-rejected in `run`) |
| web for discovery roles | `WebSearch`, `WebFetch(domain:…)` | `web_search = cached\|live` (no fetch tool without the shell) | `webfetch`, `websearch` (Exa) |
| skills | plugin skills; `skills:` preload in an agent | listed only; inline them (no shell) | native `skill` tool |
| quota read without spending | none (a mod in the user's own session can report) | **`account/rateLimits/read`**: verified | none |
| quota seen during a run | `rate_limit_event`; mod `session.measure` (untested in `-p`) | rollout `token_count.rate_limits` | tokens and cost only |
| quota enforced mid-run | mod `tool.call` deny / `$.turn.abort` (untested); kill the process | hook `PreToolUse` deny; kill | plugin `tool.execute.before`; kill |
| structured output | `--json-schema` | `--output-schema` | none (validate the final text) |

---

## 2. Design

### 2.1 Canonical definitions, stored in the Histmersion repo

**Skills** stay where they are: `skills/<name>/SKILL.md`, already one copy for Claude Code, opencode and the plugin. Two changes:

1. **Tool references become placeholders**: `{{tool:get_source}}` instead of `mcp__histmersion__get_source` or `histmersion get`. The generator renders one per runtime:

   | runtime | `{{tool:get_source}}` renders as |
   |---|---|
   | Claude Code | `mcp__histmersion__get_source` |
   | opencode | `histmersion_get_source` |
   | Codex, API | `get_source` |
   | interactive CLI flavour | `histmersion get` |

   `tools/check_skills.sh` checks that every placeholder names a real tool.
2. `allowed-tools` leaves the skill. Permissions belong to the agent role, not to the skill.

**Agents** become runtime-neutral: `agents/<role>.md`, with a body that speaks of tools by placeholder and a small manifest in the frontmatter.

```yaml
---
name: article-skeptic
description: A specialist adversary who tries to refute one compiled Histmersion article from the database itself. Read-only.
model-role: reviewer            # → agents.roles.reviewer chain (claude-code:opus > anthropic:… > local:…)
skills: [histmersion-query]     # inlined or preloaded, per runtime
include: [writing-guide]        # shared fragments from agents/_shared/ or docs/
tools:
  histmersion: [get_source, neighbors, search, followups, query, facets]
  web: none                     # none | discover (Histmersion's own discover tool) | search | fetch
writes: none                    # none | propose (returns JSON, code writes) | direct (rare, e.g. an interactive fixer)
max-turns: 40
answer: review-findings         # optional: schemas/review-findings.json → --json-schema / --output-schema
runtimes: [claude-code, codex, opencode, api]
---
You are the **specialist skeptic** … run {{tool:get_source}} on what it cites, then {{tool:neighbors}} …
```

Notes on the manifest:

- **`web: discover`** is the default for acquisition roles.
  - Histmersion's `discover` tool already wraps OpenAlex, Crossref and Wikipedia references, so "MCP only" holds even when finding new sources.
  - `search`/`fetch` grant the runtime's own web tools only when a role truly needs the open web.
- **`writes: propose`** keeps §12–§14's rule that no step has a write tool: the answer is JSON and code writes it.
- **The existing server-owned notes** (`prompts/review-article/{common,…}.md`) become `include:` fragments.
- **The four review agents** move to this form, which retires their `Bash, Read` and `histmersion …` CLI instructions.

**A pipeline** keeps calling `ctx.llm(name, role, prompt, tools, schema)`. The role's manifest now supplies the prompt, the tools and the schema, and the runtime is chosen by the model chain.

### 2.2 The generator

**When it runs.**
- `AgentDefinitions.generate()` runs at server start and whenever the definitions, the chains or the agent config change.
- The definitions are the resources bundled in the server, overridden by `agents.prompts-dir` as now.

**Where it writes.** To `<data-dir>/agents/`:
- macOS: `~/Library/Application Support/Histmersion/agents/`;
- Linux: `$XDG_DATA_HOME/histmersion/agents/`.

**How it writes.**
- It writes atomically: into `agents/.next/`, then a rename.
- It records `manifest.json` (source sha256 per file, generator version, Claude/Codex/opencode versions it targeted).
- A runtime is generated only when it is enabled, or installed if `agents.runtimes.auto`.
- `agent doctor` validates each runtime offline: `claude plugin validate`, `codex mcp get` and `codex debug prompt-input`, `opencode debug agent`.

**What it never touches.** Login state. The `login/` directories below are created empty and filled only by the vendor's own login command.

```
~/Library/Application Support/Histmersion/agents/
├── manifest.json
├── claude-code/
│   ├── .claude-plugin/marketplace.json        # local marketplace: plugins = [./histmersion-agents]
│   ├── histmersion-agents/                     # THE plugin (headless runs and the user's sessions)
│   │   ├── .claude-plugin/plugin.json          # version 0.3.0+<defs-sha>
│   │   ├── agents/article-skeptic.md           # tools: mcp__histmersion__get_source, …; skills: histmersion-query; model: …
│   │   ├── agents/article-cold-reader.md       # tools: (none) — Read dropped, the article travels in the prompt
│   │   ├── skills/<19 skills>/SKILL.md         # rendered (tool names, no allowed-tools)
│   │   └── hooks/{hooks.json,quota-guard.ts}   # the quota mod (2.5)
│   ├── roles/article-skeptic/{settings.json,mcp.json}
│   └── login/                                  # optional CLAUDE_CONFIG_DIR; empty until `claude /login` there
├── codex/
│   ├── home/                                   # CODEX_HOME for agent runs
│   │   ├── config.toml                         # base: everything off, histmersion MCP required
│   │   ├── article-skeptic.config.toml         # profile per role
│   │   ├── agents/article-skeptic.toml         # for interactive multi-agent use (name/description/developer_instructions)
│   │   └── skills/<19 skills>/SKILL.md
│   └── plugin/.codex-plugin/plugin.json + skills/ + .mcp.json   # for the user's own Codex (skills + MCP)
└── opencode/
    ├── xdg/opencode/                           # XDG_CONFIG_HOME for agent runs (empty global config)
    └── config/                                 # OPENCODE_CONFIG_DIR
        ├── opencode.json                       # mcp.histmersion; "permission": {"*": "deny"}; share disabled
        ├── agents/article-skeptic.md           # mode: primary; permission: {"*": deny, histmersion_get_source: allow, …}
        ├── skills/<19 skills>/SKILL.md
        └── plugins/quota-guard.ts              # tool.execute.before → Histmersion
```

**Key generated files.**

`claude-code/roles/article-skeptic/settings.json` and `mcp.json`:

```json
{ "disableAllHooks": false, "disableClaudeAiConnectors": true,
  "permissions": { "defaultMode": "dontAsk",
    "allow": ["mcp__histmersion__get_source", "mcp__histmersion__neighbors", "mcp__histmersion__search",
              "mcp__histmersion__followups", "mcp__histmersion__query", "mcp__histmersion__facets"],
    "deny":  ["Bash", "Edit", "Write", "NotebookEdit", "WebFetch", "WebSearch", "Agent"] } }

{ "mcpServers": { "histmersion": { "type": "http", "url": "http://127.0.0.1:8000/mcp/agent",
    "headers": { "Authorization": "Bearer ${HISTMERSION_RUN_TOKEN}" } } } }
```

- `disableAllHooks` becomes `false` only so that the plugin's quota mod runs. **Untested:** whether `disableAllHooks` also stops mod modules. If it does not, keep it `true`.
- The plugin used for runs has **no** `.mcp.json`, so tool names stay `mcp__histmersion__*` and do not depend on the plugin's name.

`codex/home/config.toml` (base) and one profile:

```toml
web_search = "disabled"
sandbox_mode = "read-only"
approval_policy = "never"
check_for_update_on_startup = false
include_environment_context = false
include_permissions_instructions = false
include_collaboration_mode_instructions = false
[skills.bundled]
enabled = false
[agents]
enabled = false
[features]
shell_tool = false
view_image = false
image_generation = false
browser_use = false
computer_use = false
apps = false
plugins = false
memories = false
tool_suggest = false
[mcp_servers.histmersion]
url = "http://127.0.0.1:8000/mcp/agent"
bearer_token_env_var = "HISTMERSION_RUN_TOKEN"
required = true
enabled_tools = []            # nothing unless a profile grants it
```

```toml
# article-skeptic.config.toml  (layered by `-p article-skeptic`)
developer_instructions = """<agent body, include: fragments and inlined skills, tool placeholders rendered as bare names>"""
[mcp_servers.histmersion]
enabled_tools = ["get_source", "neighbors", "search", "followups", "query", "facets"]
```

This shape was verified offline: `codex -p … mcp get histmersion` showed the narrowed list, and `debug prompt-input` showed only the developer and user messages.

`opencode/config/agents/article-skeptic.md`:

```markdown
---
description: A specialist adversary who tries to refute one compiled Histmersion article. Read-only.
mode: primary
steps: 40
permission:
  "*": deny
  histmersion_get_source: allow
  histmersion_neighbors: allow
  histmersion_search: allow
  histmersion_followups: allow
  histmersion_query: allow
  histmersion_facets: allow
---
<agent body; skills inlined, or `skill: allow` for the listed ones>
```

This was verified offline with `opencode debug agent` against a scratch config.

### 2.3 Skills per runtime

| runtime | how the role gets its skills |
|---|---|
| Claude Code | The agent's `skills:` frontmatter preloads them. Drop `--disable-slash-commands` for roles that list skills. **Untested:** whether that flag also blocks the preload. If it does, keep the flag and inline. |
| Codex | Inline the skill bodies into `developer_instructions`, because a listed skill cannot be opened without a shell. Keep `skills/` in the home for the user's interactive Codex, where the shell exists. |
| opencode | Use the native `skill` tool (`permission.skill: {"*": deny, "<listed>": allow}`), or inline for parity. |
| API (sttp-ai) | Inline, as the bundled prompts do now. |

Inlining is the common denominator. It also makes the prompt sha recorded per attempt (§13) cover the skills.

### 2.4 Launching a run, and enforcement in depth

**Per-run token.** Each step attempt gets a fresh **run token**, bound in the server to `{job, step, role, allowed tools, expiry}`.

- `/mcp/agent` serves `tools/list` filtered to the token's role.
- It refuses any other call with an MCP error, which is also traced.

This is the layer no runtime can talk its way around. It turns the endpoint from "the seven Toolbox tools" into "exactly this role's tools", and it makes write tools possible for a future `writes: direct` role without opening them to every step. The runtime's own restriction (§1) is the second layer. The empty temporary cwd and the stripped environment are the third.

**Claude Code** (the existing `ClaudeCodeClient`, extended):

```
env -u ANTHROPIC_API_KEY -u ANTHROPIC_AUTH_TOKEN \
    ENABLE_CLAUDEAI_MCP_SERVERS=false HISTMERSION_RUN_TOKEN=<token> \
    [CLAUDE_CONFIG_DIR=<agents>/claude-code/login] \
  claude -p --output-format stream-json --verbose --no-session-persistence \
    --plugin-dir <agents>/claude-code/histmersion-agents \
    --agent histmersion-agents:article-skeptic \
    --settings <agents>/claude-code/roles/article-skeptic/settings.json --setting-sources "" \
    --strict-mcp-config --mcp-config <agents>/claude-code/roles/article-skeptic/mcp.json \
    --tools "" --permission-mode dontAsk \
    --json-schema '<answer schema>' --max-turns 40 --model <from the chain>
  < user-message
```

- **System prompt.** `--agent` carries the prompt, tools, model and skills. `--system-prompt-file` stays available for steps that are not an agent role.
- **Discovery roles.** Use `--tools "WebSearch,WebFetch"` plus `WebFetch(domain:…)` allow rules.
- **`--setting-sources ""`.** It replaces `--restricted`, whose effect on the subscription login is still unknown (§15). **Untested:** whether an empty value is accepted. If not, use `--setting-sources local` inside the empty cwd.

**Codex:**

```
CODEX_HOME=<agents>/codex/home HISTMERSION_RUN_TOKEN=<token> \
  codex exec --json -p article-skeptic -m <model> --output-schema <schema.json> \
    --skip-git-repo-check -C <empty tmp dir> -o <tmp>/last.json  < user-message
```

- **Not `--ephemeral`**: the rollout carries the rate-limit snapshot.
- **`OPENAI_API_KEY`/`CODEX_API_KEY`** are stripped, unless the chain entry is an API-key entry.

**opencode:**

```
XDG_CONFIG_HOME=<agents>/opencode/xdg OPENCODE_CONFIG_DIR=<agents>/opencode/config \
OPENCODE_DISABLE_CLAUDE_CODE=1 OPENCODE_DISABLE_AUTOUPDATE=1 HISTMERSION_RUN_TOKEN=<token> \
  opencode run --agent article-skeptic --format json -m <provider/model> --dir <empty tmp dir> < user-message
```

opencode has no schema flag. The final `text` is parsed as JSON, and a retry carries the validator's problems, as for every provider.

**Results back into Histmersion.** One adapter per runtime turns its stream into the engine's existing records: the trace (`agent_messages`), the tokens and cost (`agent_steps`), and the typed answer.

| | trace | usage | answer | limit signal |
|---|---|---|---|---|
| Claude | `assistant`/`user` tool_use/tool_result events | `result.modelUsage`, `total_cost_usd` (an API-equivalent figure on a plan) | `result.structured_output` | `rate_limit_event`, result error, `StopFailure` |
| Codex | `item.*` (agent_message, mcp_tool_call, reasoning) | `turn.completed.usage` | `-o` file / last `agent_message` | `turn.failed`, rollout `rate_limits` |
| opencode | `tool_use`, `text`, `reasoning` | `step_finish.part.tokens`, `.cost` | last `text` | `error` (provider message) |

### 2.5 The quota layer across runtimes

This builds on research-plan-quotas.md §5, which still holds. What the runtimes add:

**Pools.** An allowance targets a **pool**, not a model:

| pool | kind | targets |
|---|---|---|
| `claude-plan` | `plan` | every `claude-code:*` entry |
| `chatgpt-plan:<limitId>` | `plan` | `codex:*`, and `opencode:openai/*` if the login is the ChatGPT one |
| `anthropic-key`, `openai-key`, `opencode-zen`, … | `metered` | USD and tokens per rolling window, as now |
| `local` | none | — |

**Plan floors.** Per pool, there is a floor per window kind (`five_hour`, `seven_day`; Codex by `windowDurationMins` and `limitId`). A pool is usable only when:
- every known window has more left than its floor;
- the provider's own signal is not `rejected` or `rate_limit_reached`;
- Codex `ordinaryUsageAllowed` is not `false`.

**Where the snapshots come from.**

| pool | source |
|---|---|
| ChatGPT plan | `codex app-server` → `account/rateLimits/read` before a run starts or resumes, at most once a minute; `account/rateLimits/updated` notifications; rollouts after each step. It must run against the **same `CODEX_HOME`** the runs use, so it reads that login. |
| Claude plan | `rate_limit_event` from each run's stream; the quota-guard mod's `session.measure` POSTs, both from headless runs and, if the user installs the plugin, from **their interactive sessions** (the human's share of the pool) |
| opencode | its provider's pool if known (ChatGPT → the Codex read), else metered by `step_finish` |

**Mid-run guard.**
- **Claude:** the mod denies `tool.call` and calls `$.turn.abort` when Histmersion answers "below floor" to its POST. Its endpoint is `POST /api/agents/quota/claude`, which returns `{allow:false, reason, resetsAt}`.
- **Codex and opencode:** a `PreToolUse` hook (Codex) or `tool.execute.before` (opencode) asks the same endpoint.
- **Everywhere:** the engine also kills the process tree, which is what a cancel already does.
- Each of these ends the step as `plan_quota`, without using up a validation retry.

**Hold switch.** A per-pool "hold" is a manual pause for the subscription, checked before everything else. The mods and hooks see it on their next call.

**Chains pause; they do not fall through to money.**
- `on-plan-floor = pause` is the default per chain. A run pauses with `plan_quota` and `resume_at` = the earliest reset among the windows that block it.
- `next` must be opted into, per chain, to fall from a plan to a paid key.
- A metered pool that is spent pauses with `allowance`, as now.
- Only these signals move down a chain. A provider error still pauses (§15, deviation 4).

**Concurrency.** `max-llm-calls-in-flight` per pool: 1–2 for plan pools, because the three-way review panel otherwise spends the last headroom three times.

### 2.6 One generated plugin for the user's own tools

The same generator writes the *interactive flavour*:
- the MCP URL is `/mcp` (all 60 tools, the user's own permission prompts) instead of `/mcp/agent`;
- skills are rendered with `mcp__histmersion__*` names, and the CLI form is kept in a note;
- no run token.

| consumer | how |
|---|---|
| research workspace, Claude Code | `claude plugin marketplace add "~/Library/Application Support/Histmersion/agents/claude-code"` then `claude plugin install histmersion-agents@histmersion-agents --scope project`. A folder marketplace with a relative source is read from the folder itself, so a regeneration reaches a session with `/reload-plugins`, with no reinstall. The workspace then deletes `.claude/skills/*` and `.claude/agents/article-*.md`. **13 of its 19 skills have already drifted** from the Histmersion copies (diffed today), which is the problem this removes. |
| user's Claude Code, everywhere | the same, `--scope user`; the quota mod in it reports the human's plan usage |
| user's Codex | `codex plugin marketplace add <agents>/codex/plugin` + `codex plugin add`, or `-p histmersion` against a profile written into `~/.codex` only with consent. Custom agents (`agents/*.toml`) can only be installed into a home, not shipped in a plugin. |
| user's opencode | `OPENCODE_CONFIG_DIR=<agents>/opencode/interactive`, or a one-line `instructions`/`skills` path in their own config. The `.opencode/skill → ../skills` symlink in the repo keeps working for the repo itself. |

The repo's `plugin/` and `.claude-plugin/marketplace.json` become one more generator output, checked in for people without a running server. A CI check runs the generator and diffs the output.

### 2.7 What stays on the direct API, what moves

| work | runtime | why |
|---|---|---|
| typed single-shot steps: extract, classify, findings, notes, draft, cold reader | **direct API** (sttp-ai), or any runtime as a chain entry | The engine owns the loop, validators, retries and checkpoints. A CLI adds process start-up and nothing else. |
| tool-using read steps: rules reviewer, skeptic, fixer (returns a body), planner | any; the chain decides | This is where a plan login pays off. |
| open-ended "agentic" pipelines (a whole `trailhead` or `report`) | CLI runtimes only, as a single step with `max-turns` and a schema'd final report | These are the skills as written. The engine treats the whole run as one checkpointed step. |
| discovery | `discover` (MCP) first; `web: search` roles on Claude or Codex only | It keeps "MCP only" true by default. |

**Migration from the current `claude-code` backend:**

1. Add the run token and the role-filtered `/mcp/agent`. The Toolbox becomes "the role's tools".
2. Rewrite the four review agents in the neutral format, with the server notes as `include:` fragments. The generated Claude agent must be byte-identical in body to what the bundled prompt sends now; a test checks this.
3. Generator plus `agent doctor` offline validation.
4. `ClaudeCodeClient` switches to `--plugin-dir/--agent/--settings`. `extra-args = "--restricted"` stays the default until probe P2 (§3) settles it.
5. `CodexExecClient` (`codex:` provider) and the app-server quota reader.
6. `OpencodeRunClient` (`opencode:` provider).
7. Pools and plan floors (research-plan-quotas.md §5), the quota endpoint and the mods and hooks.
8. Switch the research workspace to the generated plugin.

**Tests.** No live model:

- **Fake CLIs.** Small executables on `PATH` (`fake-claude`, `fake-codex`, `fake-opencode`) replay recorded streams, chosen by an env var: success, tool call, schema miss and retry, `rate_limit_event` with `rejected`, `turn.failed` on a usage limit, a malformed stream, a hang that is then killed. They also record their argv and env, and a test asserts:
  - no `ANTHROPIC_API_KEY` reached them;
  - the cwd was empty;
  - the expected flags were present.
- **Generator.** Golden trees per runtime, plus the offline validators: `claude plugin validate`, `codex mcp get` and `codex debug prompt-input`, `opencode debug agent`. These run in CI only when the CLIs are installed.
- **Endpoint.** A token for role A cannot list or call role B's tools.
- **Quota.** The pick with plan snapshots: floors, `ordinaryUsageAllowed:false`, a missing window, a stale snapshot, a hold, and `on-plan-floor = pause` versus `next`. A stub app-server answers `account/rateLimits/read` from fixtures; the response captured above is one of them.

---

## 3. Open questions that need a paid probe

Each one can be settled by one minimal call, about one Haiku- or mini-sized turn. The user runs it, or approves it.

| # | question | the call |
|---|---|---|
| P1 | Does `claude -p` stream-json carry `rate_limit_event` with `unifiedWindows` (per-window utilization) on 2.1.289? | `claude -p "ok" --model haiku --max-turns 1 --tools "" --output-format stream-json --verbose --no-session-persistence \| grep rate_limit_event` |
| P2 | Does `--restricted` (or `--setting-sources ""`) keep the subscription login? | the same call with `--restricted`, then with `--setting-sources ""`; check that the `system/init` event shows `apiKeySource: none` and that the result is not an auth error |
| P3 | Do mods load under `-p` from `--plugin-dir`? Does `session.measure` fire with `rateLimits`? Can a `tool.call` deny and `$.turn.abort` end the run? | P1's call plus `--plugin-dir <scratch mod>`, the mod POSTing to a local `nc -l` listener and denying the first tool call |
| P4 | Does `--strict-mcp-config` drop a `--plugin-dir` plugin's `.mcp.json` server? Does `--disable-slash-commands` block an agent's `skills:` preload? | P1's call with `--plugin-dir` (a plugin with `.mcp.json` and an agent with `skills:`), `--agent`, `--strict-mcp-config`; read the `system/init` event (`mcp_servers`, `skills`) |
| P5 | Does `disableAllHooks` in `--settings` also disable mod hooks? | P3's call with `--settings '{"disableAllHooks":true}'` |
| P6 | Codex with `shell_tool=false`: is `apply_patch` still offered? Does `-p <role>` reach `exec`? Does the rollout record `rate_limits` in a non-default `CODEX_HOME`? | once the weekly pool has reset (10 Oct): `CODEX_HOME=<gen> codex exec --json -p article-skeptic -C <tmp> "reply ok"`, then read the rollout's `token_count` and its `tools` |
| P7 | Can Codex use a listed skill with no shell (`skill_search`)? | the same call with a prompt asking for `$histmersion-query`; look for a skill-read item |
| P8 | Does opencode's ChatGPT login draw on the Codex pool? | `opencode run -m openai/<mini> "ok"` with a ChatGPT login; compare `account/rateLimits/read` before and after (needs a pool that is not at 100 %) |
| P9 | Does opencode `--format json` with a deny-all agent show a denied MCP call as an `error` or a `tool_use` with an error? | `opencode run --agent <deny-all> --format json "call histmersion_ping"` against a local model (costs nothing: `local:` / Ollama) |

P9 needs no plan or key at all, only a local model.

All the offline checks this note relied on can be repeated from the scratch configs it describes:
- `claude plugin validate`;
- `codex mcp get` and `codex debug prompt-input`;
- `opencode debug agent|skill|paths` and `opencode mcp list`;
- the one `account/rateLimits/read`.

## 3a. Probe results (2026-10-05)

P1–P5 were run on Claude Code 2.1.289 with five `claude -p` calls, all on `--model haiku` with trivial prompts. Settings:
- Every call ran with `ANTHROPIC_API_KEY` and `ANTHROPIC_AUTH_TOKEN` unset and `ENABLE_CLAUDEAI_MCP_SERVERS=false`.
- Every call used `--no-session-persistence --output-format stream-json --verbose --debug-file …`.
- The working directory was a scratch folder, so no project settings were read.

The five calls cost about US$0.046 in all, at list price (the figure the stream reports).

| call | flags beyond the common ones | answers |
|---|---|---|
| 1 | `--restricted --tools Read --allowedTools Read --permission-mode dontAsk --plugin-dir <quota-probe mod> --strict-mcp-config --mcp-config <empty> --max-turns 2`. Prompt: "Use the Read tool exactly once on hello.txt, then reply with the word ok." | P1, P2 (`--restricted`), P3 |
| 2 | `--setting-sources "" --plugin-dir <probeb> --agent probeb:probe-agent --tools "" --strict-mcp-config --mcp-config <empty> --disable-slash-commands --max-turns 1` | P2 (`--setting-sources ""`), P4 with both flags on |
| 3 | the same as call 2 without `--strict-mcp-config` and `--disable-slash-commands` | the P4 baseline |
| 4 | `--setting-sources "" --settings '{"disableAllHooks":true}' --plugin-dir <log-only mod> --plugin-dir <probeb> --tools Agent --disable-slash-commands --strict-mcp-config …`. The model spawns `probeb:probe-agent` once. | P5, and P4 for a real subagent |
| 5 | the same as call 4 without `disableAllHooks`, the mod and `--disable-slash-commands` | the baseline for the subagent preload |

The `probeb` plugin contains three things:
- a `.mcp.json` with one server, `hmro`, pointing at `http://localhost:8000/mcp/agent`;
- an agent `probe-agent` with `skills: [canary-skill]` and `tools: []`;
- the skill `canary-skill`.

| # | question | answer |
|---|---|---|
| P1 | Does `rate_limit_event` carry per-window utilization and reset times? | **Yes.** `unifiedWindows.five_hour` and `seven_day` each carry `utilization` (0–1) and `resetsAt` (epoch s). They were present on every call, with the status `allowed`. |
| P2 | Does the subscription login survive `--restricted` or `--setting-sources ""`? | **Yes, for both.** `system/init` had `apiKeySource: "none"`, the result was `success`, and `rate_limit_event` appeared. That event is always absent for API-key sessions. |
| P3 | Do mods load and work under `-p`? | **Yes, on all four counts.** The mod loads from `--plugin-dir`. `$.session.usage()` returns `rateLimits` with `kind`, `percentUsed` and `resetsAt`. `session.measure` fires. A `tool.call` hook can deny a call, and `$.turn.abort` ends the turn before the next model request. |
| P4a | Does `--strict-mcp-config` drop the MCP server bundled in a `--plugin-dir` plugin? | **Yes.** `mcp_servers` was `[]` with the flag. Without it, it was `[{"name":"plugin:probeb:hmro","status":"connected","source":"plugin"}]`. |
| P4b | Does `--disable-slash-commands` block an agent's `skills:` preload? | **Yes.** With the flag, the debug log says `[Agent: probeb:probe-agent] Warning: Skill 'canary-skill' specified in frontmatter was not found` and the subagent used 864 tokens. Without it, the log says `Preloaded skill 'canary-skill'` and the subagent used 1,301 tokens. The flag also empties `skills` and `slash_commands` in `system/init`. |
| P5 | Does `disableAllHooks` in `--settings` also turn off mods? | **Yes.** The debug log says ``hooks module hooks-off-probe@inline not loaded: only managed plugins and built-in plugins run (allowManagedHooksOnly / disableAllHooks)``. The mod wrote no log file. The built-in `cc-plugin-*` modules still loaded. |

**P1 evidence.** This is the `rate_limit_event` from call 1, with `uuid` and `session_id` removed. Calls 2–5 had the same values.

```json
{"type":"rate_limit_event","rate_limit_info":{"status":"allowed","resetsAt":1791236400,"rateLimitType":"five_hour",
 "overageStatus":"rejected","overageDisabledReason":"org_level_disabled","isUsingOverage":false,
 "unifiedWindows":{"five_hour":{"utilization":0.12,"resetsAt":1791236400},
                   "seven_day":{"utilization":0.42,"resetsAt":1791522000}}}}
```

- **One event per call.** It arrived after the first model response; in call 1 it came after the denied `tool_result`. Short calls never crossed a whole point, so no second event came.
- **No per-model weekly bucket.** No `seven_day_opus` or `seven_day_sonnet` window appeared. This is consistent with the `@internal` schema, which lists only `five_hour`, `seven_day` and `seven_day_overage_included`.
- **Issue #50518 is out of date.** `utilization` is present while the status is `allowed`, so it no longer appears only once a threshold is crossed.

**P2 evidence.** `system/init` of call 1 (`--restricted`) and of calls 2–5 (`--setting-sources ""`) all had these values:

```
"apiKeySource": "none", "model": "claude-haiku-4-5-20251001"
```

Every result was `subtype: "success"` and `is_error: false`.

`--restricted --tools Read` offered `["Read"]` only. The built-in plugins `cc-plugin-agents-md`, `cc-plugin-telemetry` and `cc-plugin-plugin-authoring` still load under both `--restricted` and `--setting-sources ""`.

**P3 evidence.** The mod hooked `session.start`, `turn.start`, `session.measure`, `tool.call`, `turn.step`, `turn.complete` and `session.end`, and wrote its log with `$.fs.write`.

The debug log of call 1 shows it loading:

```
hooks module quota-probe@inline loaded (worker, environment 1, tier user); events: session.start,turn.start,session.measure,tool.call,turn.step,turn.complete,session.end
plugin.register: quota-probe (user, quota-probe@inline), judged by core alone: admitted
```

`system/init` lists it as `{"name":"quota-probe","source":"quota-probe@inline",…}`.

The mod's own log, abridged:

```
session.start:usage   {"context":{"window":200000},"rateLimits":[],"cost":{"usd":0}}
turn.step             {"index":0,"model":"claude-haiku-4-5-20251001","denied":false}
tool.call             {"tool":"Read","denied":false}                       -> returned {deny}
session.measure       {"rateLimits":[{"kind":"five_hour","percentUsed":12,"resetsAt":"2026-10-05T21:40:00.000Z"},
                                     {"kind":"seven_day","percentUsed":42,"resetsAt":"2026-10-09T05:00:00.000Z"}],
                       "changed":["context","rateLimits","cost"],"cost":{"usd":0.011073}}
turn.step             {"index":1,...,"denied":true}                         -> $.turn.abort({turnId})
turn.abort            "ok"
turn.complete         {"reason":"aborted"}
session.end:usage     {"rateLimits":[ same two windows ], "cost":{"usd":0.011073}}
```

What the stream shows:

- **The denied call.** It is an ordinary error `tool_result` carrying the mod's reason:

  ```json
  {"type":"tool_result","content":"<tool_use_error>quota-probe: refused by the probe mod</tool_use_error>","is_error":true}
  ```

  with `"tool_result_meta":[{"non_execution_kind":"permission-rule"}]`. The result's `permission_denials` stays `[]`.
- **The abort.** No second assistant message appears. The `result` event is:

  ```json
  {"type":"result","subtype":"success","is_error":false,"result":"","num_turns":2,
   "stop_reason":"tool_use","terminal_reason":"aborted_streaming"}
  ```

  An abort by the mod is therefore *not* an error result. It is recognisable only by `terminal_reason: "aborted_streaming"` with an empty `result`.
- **Usage before the first response.** `$.session.usage()` at `session.start` has `rateLimits: []`. The windows are known only after the first response.
- **Percentages.** `percentUsed` equals `utilization × 100`, rounded to at most one decimal.

**P4 evidence.** These are the `system/init` fields of calls 2 and 3:

| field | call 2 (`--strict-mcp-config`, `--disable-slash-commands`) | call 3 (neither flag) |
|---|---|---|
| `mcp_servers` | `[]` | `[{"name":"plugin:probeb:hmro","status":"connected","source":"plugin"}]` |
| `skills` | `[]` | 20 entries, including `probeb:canary-skill` |
| `slash_commands` | `[]` | 57 entries |

**A main-thread `--agent` gets no skill preload, with or without the flag.** Calls 2 and 3 both used exactly 430 input tokens, and neither debug log has a preload line. `skills:` preloading applies only to an agent spawned as a subagent. That is why calls 4 and 5 test it with a real `Agent` spawn:

- **Call 4, with the flag:** `Warning: Skill 'canary-skill' specified in frontmatter was not found`, and the subagent used 864 tokens.
- **Call 5, without the flag:** `[Agent: probeb:probe-agent] Preloaded skill 'canary-skill'`, and the subagent used 1,301 tokens.

The bare name `canary-skill` resolves inside its own plugin. It does not need the `probeb:` prefix.

The binary explains the result. The preload looks the skill up in the skill-tool command list. That list's loader begins `if(Dg())return[]`, and `Dg()` is `host.launchOptions.disableSlashCommands()`. So the flag removes every skill a preload could find.

Haiku did not obey the canary instruction even when the skill was preloaded. The token difference and the debug line are the evidence, not the reply.

**P5 evidence.** In call 4 the log-only mod `hooks-off-probe` appears in `system/init.plugins`, but its module was refused:

```
hooks module hooks-off-probe@inline not loaded: only managed plugins and built-in plugins run (allowManagedHooksOnly / disableAllHooks)
hooks module cc-plugin-agents-md@builtin loaded (native, environment 1, tier builtin) …
hooks module cc-plugin-telemetry@builtin loaded (native, environment 2, tier builtin) …
```

### What this changes in §2

1. **The quota layer can parse the stream (§2.5, research-plan-quotas.md §5.2).**
   - **Every window is available.** `unifiedWindows` reaches `claude -p` stream-json on a plan login, so `ClaudeCodeClient` can store both windows from every call, not only the representative one.
   - **Only one reading per call.** It comes once per call, after the first response, so the mid-step `AllowanceGuard` sees the pool move within a step only when a window crosses a whole point.
   - **No per-model weekly window.** Histmersion cannot read a separate Opus or Sonnet weekly window from the stream.
2. **The quota-guard mod works under `-p`, with one constraint.**
   - **What works:** it loads, reads `rateLimits`, receives `session.measure`, denies tools and aborts the turn.
   - **The constraint:** `disableAllHooks` must **not** be in the generated `--settings`, because that also turns the mod off. To keep the user's own hooks out, use `--setting-sources ""` or `--restricted` instead; both keep the plan login.
   - **Reading an abort:** the backend must treat `terminal_reason: "aborted_streaming"` with `result: ""` as "stopped by the guard". It is not a success with an empty answer.
   - **Reading a denial:** a mod denial is a `tool_result` with `is_error` and `non_execution_kind: "permission-rule"`, and it is not counted in `permission_denials`.
3. **`--strict-mcp-config` also drops plugin-bundled MCP servers.**
   - A generated plugin must not rely on its own `.mcp.json` when the run uses `--strict-mcp-config`. Pass the server through `--mcp-config` instead; its tool names are then `mcp__<server>__…`, not `mcp__plugin_…`.
   - Alternatively, drop `--strict-mcp-config` and keep connectors out with `ENABLE_CLAUDEAI_MCP_SERVERS=false` and `--setting-sources ""`.
4. **`--disable-slash-commands` must go from the `claude-code` backend if roles are to get their skills (§2.3).**
   - The flag kills `skills:` preloads in subagents as well as the skill listing.
   - A main-thread `--agent` never gets a `skills:` preload at all. A role launched with `--agent <plugin>:<role>` must have its skills put into its prompt by the generator, or be run as a subagent.
5. **A side observation from call 5.** Without `run_in_background: false`, Haiku launched the subagent *asynchronously*. The stream then carried **two `result` events**: the first one was the parent's interim answer, and the second one was the real answer.
   - The generated spawn instructions, or the backend, must force foreground spawns.
   - Alternatively, the parser must take the last `result`, not the first.

**Not settled.**
- Whether a `rate_limit_event` arrives mid-step when a window crosses a whole point. No call was long enough to move a window.
- Whether the per-model weekly windows ever appear in `unifiedWindows`.

The scratch configs, mods and logs are in the session scratchpad (`probes/`). They are not kept in the repository.

---

## 4. As built (2026-10-05)

Agents stay **off** by default; with them off nothing below runs except `histmersion agent generate`
and the quota and settings surfaces. No model was called: every runtime is tested against a fake
executable replaying recorded streams (the §3a events among them), and the Codex quota read against a
stub app-server answering the response captured in §1.2.

### Definitions (§2.1)

- **`agents/<name>.md`**, one per engine step: `probe`, `enrich-classify`, `enrich-findings`,
  `enrich-notes`, the four `article-*` reviewers, `goal-planner`, `compile-extractor`,
  `compile-writer`, `acquisition-ranker`. The manifest has `name`, `description`, `model-role`,
  `skills`, `include`, `tools.histmersion`, `tools.web`, `writes`, `max-turns`, `answer`, `runtimes`
  and, for the four reviewers, `interactive: {model, tools}`. A definition naming a tool an agent
  cannot be offered, an unknown role or runtime, or a missing include or skill is refused with why.
- The server's step prompts moved into these files; the review agents' "running inside the server"
  notes are the include fragments `agents/_shared/review-*.md`, and `docs/writing-guide.md` is the
  fragment `writing-guide`. The build copies `agents/`, `agents/_shared/` and `skills/` into the
  server's resources with an `index.txt` (native-image cannot scan the classpath);
  `agents.prompts-dir` still overrides any of them file by file.
- **One prompt function.** `AgentDefinitions.system(def, style)` = body, includes in order (the
  guide under its heading), the listed skills inlined, placeholders rendered. The engine sends it with
  bare tool names to the direct API; the generator writes the same text into the Claude Code agent,
  the Codex profile and the opencode agent. A test holds every generated Claude agent to it.
- The pipelines call `ctx.llm(name, StepAgent.X, user, schema)`: role, tools and prompt come from the
  definition, so the tools a step is offered and the tools its run token grants cannot differ.
- **Skills.** `skills/<name>/SKILL.md` name tools by `{{tool:<name>}}`. `allowed-tools` stays in the
  canonical file and is rendered only for the person's own Claude Code (where it saves permission
  prompts); every headless rendering drops it. The 19 skills were consolidated with the research
  workspace's copies first (the newer side of each; `ingest-paper` and `review-article` merged).

### The generator (§2.2)

- `<data dir>/agents/` (or `agents.runtimes.dir`), written when the server starts with agents on and
  by `histmersion agent generate [--runtime …] [--out <server dir>]` (`POST /api/agents/generate`, works
  with agents off). File by file, each atomically; a file the previous `manifest.json` listed and the
  new tree lacks is removed; nothing else is touched (`codex/home` is the runs' CODEX_HOME with its
  login and sessions; `claude-code/login/` is created empty).
- `claude-code/headless/` — the run plugin `histmersion-agents`: agents with their MCP tools and the
  whole prompt, skills (no `allowed-tools`), the quota-guard mod (`agents.runtimes.claude-mod`, on).
  `claude-code/roles/<agent>/settings.json` — `dontAsk`, the role's allow list, built-ins denied, **no
  `disableAllHooks`**. `claude-code/.claude-plugin/marketplace.json` + `claude-code/histmersion/` — the
  interactive plugin (marketplace `histmersion-local`), with `.mcp.json` to `<public-url>/mcp` and,
  with `interactive-mod`, a mod that only reports the person's own plan windows.
- `codex/home/` — `config.toml` (everything off, Histmersion's server required, no tools), one
  `<agent>.config.toml` profile per role (developer instructions = the prompt; `enabled_tools`; a
  tool-less role disables the server), `agents/<agent>.toml`, `skills/`; `codex/plugin/` for the
  person's own Codex.
- `opencode/xdg/` (an empty global config), `opencode/config/` (`opencode.json`: the MCP server with
  the run token header, `"*": deny`; `agents/<agent>.md` with deny-all-but-the-role permissions;
  `skills/`), `opencode/interactive/`.
- **The repository's `plugin/`** (and the root marketplace) is the interactive flavour without an MCP
  file, checked in: `just plugin` regenerates it, `PluginTreeSuite` fails when it is stale. The four
  review agents there are byte-identical to the hand-written ones. `.claude/skills` and
  `.opencode/skill` now point at `plugin/skills`.

### Run tokens (§2.4)

- Each CLI step attempt gets a token (`hmrt_` + 32 random bytes) bound to its agent definition, run
  and step, alive for the provider's timeout + 60 s, revoked when the process ends.
- One MCP server per definition at `/mcp/agent/<agent>`, holding only its tools. A netty gate in front
  of `/mcp/agent*` answers 401 without a live token, 403 for another agent's path, and routes the bare
  `/mcp/agent` to the token's agent — so every runtime configuration names ONE url.
- The token is also the only credential the quota route `POST /api/agents/quota/claude` accepts
  besides the API key; it opens nothing else.

### Runtimes (§2.4, §3a)

- **claude-code**: `--plugin-dir <headless> --agent histmersion-agents:<agent> --settings
  <roles/…/settings.json> --setting-sources ""`, `--tools ""` (WebSearch/WebFetch for a web role),
  `--strict-mcp-config --mcp-config <per-call file with the token>`, allow/deny lists, `dontAsk`,
  `--json-schema`; no `--disable-slash-commands`, no `disableAllHooks`; `ENABLE_CLAUDEAI_MCP_SERVERS=false`;
  an empty working directory. Without a generated plugin it falls back to `--system-prompt-file`.
  The LAST `result` event is the answer.
- **codex**: `codex exec --json -p <agent> -m <model> --output-schema <file> --skip-git-repo-check -C
  <empty dir> -o <file> -` with `CODEX_HOME` = the generated home and no API key (unless
  `use-api-key`); items → trace, `turn.completed.usage` → tokens, `turn.failed` on a usage limit →
  the plan's limit.
- **opencode**: `opencode run --agent <agent> --format json -m <provider/model> --dir <empty dir>` with
  `XDG_CONFIG_HOME`/`OPENCODE_CONFIG_DIR` on the generated tree, `OPENCODE_DISABLE_CLAUDE_CODE`,
  autoupdate off; the schema travels in the message, the last `text` part is parsed (a fence is
  tolerated).
- All three refuse to run beyond loopback when they spend a plan, and run in an empty work dir.

### Quota pools (§2.5)

- **Plan pools** `claude-plan` (`claude-code:*`) and `chatgpt-plan` (`codex:*`, and
  `opencode:openai/…` with `chatgpt-login`). Readings per (bucket, window), newest wins (V063); per
  pool the hold switch, saved floors, a provider refusal and the plan type (V064).
- **Sources.** Every `rate_limit_event` of every `claude -p` call (both `unifiedWindows`), recorded as
  it arrives; the mod's `session.measure` reports; `codex app-server` → `account/rateLimits/read`
  before a pick and after each Codex step, at most once per `poll-seconds` (windows keyed by length;
  `ordinaryUsageAllowed: false` and a reached limit are refusals; credits and upsells ignored).
- **Verdict** (pure, `PlanQuota.verdict`): hold first; a refusal; then every current window must keep
  MORE than its floor left; a window whose reset passed no longer counts; a stale reading needs
  `margin` points to spare; no reading = unknown = usable (the first run reads it).
- **The pick**: a plan model below its floor is passed over until the earliest blocking reset; a
  PAID model after it in the chain is passed over too unless the role's `fallthrough` is on (V065,
  `agents.quota.fallthrough`); a run with nothing left pauses with **`plan-quota`** and `resume_at`,
  and the existing resumer re-queues it. Metered allowances still apply on top, unchanged.
- **Mid-step.** claude-code stops the process when a reading puts the pool below its floor; the mod
  denies the next tool call and aborts the turn (read back as `terminal_reason: "aborted_streaming"`);
  both end the attempt as the plan's limit, which does not use up a validation retry.
- **Concurrency**: `max-calls-in-flight` per plan pool (default 1), a semaphore around the call,
  on top of `max-llm-calls-in-flight`.
- **Surfaces**: `histmersion agent quota [hold|release <pool> | floors <pool> <floors>|--reset |
  fallthrough <role> on|off]`; REST `GET /api/agents/quota`, `PUT /api/agents/quota/{pool}`, `PUT
  /api/agents/quota/fallthrough/{role}`, `POST /api/agents/quota/claude`; MCP `agent_models {quota:
  true}` and `set_agent_models {pool, hold, floors}` / `{role, fallthrough}`; the web UI's agent models
  window shows a bar per window with its floor marker, reset, reading age and source, and the hold
  switch. `agent doctor` checks the codex and opencode executables, the Codex login's presence (never
  read) and whether the trees were generated.

### Deviations

1. **The review agents' bodies keep the `histmersion` CLI vocabulary.** The interactive panel still
   runs them with Bash; the headless runs get the server notes (which map each command to its tool)
   as includes. Rewriting the bodies to placeholders would change the interactive panel.
2. **No step agent lists skills yet.** The inlining works (tested); attaching e.g. `histmersion-query`
   to the skeptic would add thousands of tokens to every panel call on every runtime.
3. **One MCP server per agent definition** behind the gate, not a filtered `tools/list` on one server
   (chimp builds a server's tool list once). The run token goes into a per-call `mcp.json` for Claude
   Code rather than `${HISTMERSION_RUN_TOKEN}` expansion (untested in `--mcp-config`); the generated
   `roles/` therefore hold `settings.json` only.
4. **Two Claude Code plugins**: the run plugin (`histmersion-agents`, prompts with the server notes)
   and the interactive one (`histmersion`, the hand-written bodies) — the doc's single plugin would
   have given the person's sessions the "running inside the server" notes.
5. **Codex rollouts are not read**; the app-server read after each step covers the same snapshot.
   No Codex `PreToolUse` hook and no opencode `tool.execute.before` plugin are generated: their pools
   are guarded before each call and by killing the process.
6. **The app-server is one short process per read** (initialize, initialized, the read), not a
   long-lived child; `account/rateLimits/updated` is not subscribed.
7. **"On config change"** is "on start": config is only read at start; `agent generate` regenerates
   on demand (e.g. after editing a `prompts-dir` override).
8. **`agent doctor` does not run the vendors' offline validators** (`claude plugin validate`, `codex
   mcp get`, `opencode debug agent`) — it checks the executables and that the trees exist.
9. The pause reason's wire value is `plan-quota` (the other reasons are words too).
10. **No new MCP tools** (still 60): the quota is part of `agent_models` / `set_agent_models`.
    Generation is not on MCP — it writes server-side files, an operator's act like `just install`.
11. The CLI verb is `histmersion agent generate` (the `agent` group), not a new `agents` group.

### Not settled

- Whether a long Claude step gets a second `rate_limit_event` (§3a); the mod covers it if not.
- Codex (§3 P6, P7) and opencode (P8, P9) behaviour on a real run; the weekly Codex pool was spent
  on 5 October.

### Switching the research workspace (not done)

1. Start the server with agents on once, or run `histmersion agent generate --runtime claude-code`.
2. `claude plugin marketplace add "$HOME/Library/Application Support/Histmersion/agents/claude-code"`
3. `claude plugin install histmersion@histmersion-local --scope project` (in the workspace).
4. Restart Claude Code; `claude plugin details histmersion` lists 19 skills and the four agents.
   Check the names the agents are listed under: if Claude Code namespaces plugin agents
   (`histmersion:article-skeptic`), the review-article skill's `subagent_type: article-…` must use that
   name — untested here. The plugin's `.mcp.json` points at the same URL as the workspace's own
   `.mcp.json`, so Claude Code treats it as a duplicate and the tools stay `mcp__histmersion__*`.
5. Delete the workspace's `.claude/skills/*` and `.claude/agents/article-*.md` (identical or older now).
