# AI providers, budgets and quotas

This page is for the **built-in agents** only. Nothing here is needed to use Histmersion
([Do I need AI?](../getting-started/ai-optional.md)), nor to connect your own assistant
([Using your AI assistant](../ai-assistant.md)).

## 1. Switch the agents on

```hocon
histmersion {
  agents {
    enabled = true
    roles {
      extractor   = "anthropic:claude-sonnet-4-5"
      reviewer    = "anthropic:claude-sonnet-4-5"
      cold-reader = "local:qwen2.5-coder:32b"
      skeptic     = "anthropic:claude-sonnet-4-5"
      fixer       = "anthropic:claude-sonnet-4-5"
      planner     = "anthropic:claude-sonnet-4-5"
      writer      = "anthropic:claude-sonnet-4-5"
    }
    prices = "claude-sonnet-4-5=<input>/<output>; qwen2.5-coder:32b=0/0"   # USD per million tokens
  }
}
```

The model names are examples: use the ones your provider offers, with its current prices in place
of `<input>/<output>`. `agents.enabled` needs a **restart** (not just `config reload`).

### Roles and chains

Each step of a pipeline has a **role**: `probe`, `extractor`, `reviewer`, `cold-reader`, `skeptic`,
`fixer`, `planner`, `writer`. A role's setting is a **chain** of models, tried in order:

```hocon
agents.roles.reviewer = "anthropic:claude-sonnet-4-5 > local:qwen2.5-coder:32b"
```

Each call uses the first model in the chain that still has allowance left. `histmersion agent models`
shows each chain and which model the next call would use; `agent models set <role> <provider:model>…`
saves a chain over the settings file, `agent models reset <role>` drops it.

## 2. Providers

| provider | `provider:model` | needs |
|---|---|---|
| `anthropic` | `anthropic:<model>` | `ANTHROPIC_API_KEY` in the server's environment |
| `openai` | `openai:<model>` | `OPENAI_API_KEY` |
| `local` | `local:<model>` | an OpenAI-compatible server on your machine (llama.cpp's `llama-server`, LM Studio, vLLM, Ollama) at `agents.providers.local.base-url` (default `http://127.0.0.1:11434/v1`) |
| `claude-code` | `claude-code:<model>` | your own `claude` CLI, logged in to your own Claude account |
| `codex` | `codex:<model>` | your own `codex` CLI, logged in once in the runs' own Codex home |
| `opencode` | `opencode:<provider>/<model>` | your own `opencode` |
| a plugin | `<its name>:<model>` | as the plugin says ([Installing plugins](../plugins/installing.md)) |

Setting keys and logins: [Credentials and secrets](credentials.md#where-you-need-credentials).

**API keys are the plain way to run the agents.** The three command-line providers run *your own*
tool on *your own* subscription. They are meant for your personal use on your own machine; read your
plan's terms before you enable them. They are **refused while the server listens beyond your own
computer** (unless you state, with `allow-non-loopback`, that every client is you), so that no one
else can spend your plan. `plugins.ai-<name>.enabled = false` takes a provider out of every chain.

```bash
histmersion agent doctor                 # keys, model listings, prices, the CLI tools and their logins — spends nothing
histmersion agent probe cline2014        # ONE real model call with one read-only tool, checked against the record
```

## 3. Prices

A **metered** model — an API key, a local model, a CLI tool on an API key — needs a price, or it is
refused (an unpriced model would count as free):

```hocon
agents.prices = "<model>=<input>/<output>[/<cache-read>/<cache-write>]; …"   # USD per million tokens
```

A free local model is `"<model>=0/0"`. A model drawing on a **subscription** (`claude-code:*`,
`codex:*`, `opencode:openai/*` with a ChatGPT login) needs no price: it costs 0 USD against the
budgets, its tokens still count, and its plan floors govern it.

## 4. Budgets

| budget | default | when reached |
|---|---|---|
| per run, USD | `agents.budgets.run-usd` = 2.0 | the run pauses; `agent resume <id> --budget-usd …` raises it |
| per run, tokens | `agents.budgets.run-tokens` = 400 000 (`review-article`: 1 200 000) | the same |
| all runs, per UTC day | `agents.budgets.daily-usd` = 10.0 | runs pause |
| all runs, per UTC month | `agents.budgets.monthly-usd` = 150.0 | runs pause |
| per manager, per day | its `daily-usd` (`agents.managers.daily-usd`, 0 = none) | no new run starts |

The defaults are placeholders, not advice. A goal can carry its own cost cap too.

## 5. Allowances

An **allowance** caps a model or a whole provider per rolling window, with a reserve:

```hocon
agents.allowances = "claude-code:* = 2M tokens/5h, 20M tokens/7d, reserve 20%; anthropic:claude-sonnet-4-5 = 5 usd/day"
```

Windows: `5h`, `7d`, `day`, `week`, `month`. When less than the reserve is left, the chain's next
model is used; when every model of a role is used up, the run pauses and resumes by itself when a
window frees. `histmersion agent allowance <target> --limit …` saves one; `--remove` goes back to the
settings file.

## 6. Subscription quotas (plan pools)

A Claude or ChatGPT subscription has usage windows (for example five hours and seven days). The
server reads how much of each is used and keeps a **floor** for you:

```bash
histmersion agent quota                               # each window: used, floor, reset
histmersion agent quota floors claude-plan 'five_hour=30, seven_day=25'
histmersion agent quota hold claude-plan              # no agent run uses this plan until…
histmersion agent quota release claude-plan
histmersion agent quota fallthrough reviewer on       # let this role fall back to a PAID key below the floor
histmersion agent quota refresh                       # a fresh reading now
```

Defaults: a run uses the Claude plan only while more than 25 % of both the 5-hour and the 7-day
window is left (`agents.quota.claude-plan.floors`); the ChatGPT plan, 25 % of the 5-hour (300 min) and
20 % of the weekly (10080 min) window. Below the floor the run **pauses** until the window resets,
unless the role may fall through to a paid key. Reading the ChatGPT plan spends nothing; reading the
Claude plan takes one very small `claude -p` turn.

## 7. Concurrency

`agents.max-concurrent-runs` (4) runs at once, and `agents.max-llm-calls-in-flight` (4) model calls at
once across all runs; one call at a time per subscription. Both need a restart to change.

The web page's agent windows (runs, managers, approvals, and the models view) show all of this.
