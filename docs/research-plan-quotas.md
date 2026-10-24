# Plan quotas: governing agents that run on a subscription (research, 2026-10-05)

Histmersion's allowances (docs/research-embedded-agents.md §15) count tokens and USD per rolling window. That fits an
API key, which bills per token. It does not fit a subscription. A subscription shows **percentages of a session and
a weekly window**. The provider decides what a percent costs, and every client on the account spends the same pool.

The control a subscription allows is therefore different: **do not start (or continue) work while the session or
weekly window has less than X % / Y % left**, and wait for the reset.

This note establishes what each provider exposes, how to read it without spending usage, what the terms say, and
what to build.

**Method.**
- Docs, changelogs and the open-source Codex code (tag `rust-v0.160.0`) were read on 2026-10-05.
- Local inspection only:
  - `claude --version` (2.1.289) and `codex --version` (0.160.0);
  - `--help`;
  - `strings` on the installed Claude Code binary;
  - `codex app-server generate-json-schema` (offline);
  - the existing session logs under `~/.codex/sessions`.
- No command that generates a response or touches plan usage was run.
- **Uncertain** marks what this left open.

---

## 1. Claude subscription (Claude Code)

### 1.1 What the account has

- **Windows.**
  - A rolling **5-hour** "session" window.
  - A **7-day** window.
  - Per-model weekly buckets. The binary's `rateLimitType` enum is `five_hour | seven_day | seven_day_opus | seven_day_sonnet | seven_day_overage_included | overage`. Changelog 2.1.283 also mentions a "weekly Fable limit".
- **Shared pool.** "Both Pro and Max plans offer usage limits that are shared across Claude and Claude Code, meaning all activity in both tools counts against the same usage limits" (<https://support.claude.com/en/articles/11145838-using-claude-code-with-your-pro-or-max-plan>, updated 19 Aug 2026).
- **Units.** Remaining usage is given only as **a percentage of each window plus a reset time**. No absolute number is published.

### 1.2 Programmatic surfaces

| surface | windows | fields | in `claude -p`? | status |
|---|---|---|---|---|
| **Status line JSON** (stdin of the user's `statusLine` command) | `five_hour`, `seven_day` (+ `spend_limit` behind a gateway) | `used_percentage` 0–100, `resets_at` epoch s | **no**, TUI only | documented, since 2.1.80 |
| **`rate_limit_event`** in `--output-format stream-json` / Agent SDK | the "representative" window; plus `unifiedWindows` | `status` (`allowed` / `allowed_warning` / `rejected`), `resetsAt`, `rateLimitType`, `utilization` (0–1), overage fields | **yes** | event documented; the details are partly `@internal` |
| `anthropic-ratelimit-unified-*` response headers | 5h, 7d, overage, "grace", "slow" | utilization, reset, status, surpassed-threshold | only through the event above | not a public contract |
| `/usage`, `/status` | all, including per-model weekly rows | bars and reset dates | interactive only | UI |
| `GET /api/oauth/usage` (what `/usage` calls) | all | — | — | needs the OAuth token, so **off limits** (§3) |
| OpenTelemetry | — | cost and token metrics only; no rate-limit metric or event in the documented list | — | <https://code.claude.com/docs/en/monitoring-usage> |

**Status line** (<https://code.claude.com/docs/en/statusline>):

> "`rate_limits.five_hour.used_percentage`, `rate_limits.seven_day.used_percentage` | Percentage of the 5-hour or 7-day rate limit consumed, from 0 to 100"

> "`rate_limits`: appears only for claude.ai Pro and Max subscribers … and only after the first API response in the session. Each window … may be independently absent, and Claude Code drops a window once its `resets_at` time passes."

- Added in 2.1.80: "Added `rate_limits` field to statusline scripts … (5-hour and 7-day windows with `used_percentage` and `resets_at`)".
- The user's own `~/.claude/statusline-command.sh` already reads both fields.

**`rate_limit_event`.**
- It is in the SDK message union (`SDKRateLimitEvent`).
- The 2.1.289 binary defines it as `{type:"rate_limit_event", rate_limit_info, uuid, session_id}`, described as "Rate limit event emitted when rate limit info changes".
- Issue anthropics/claude-code#50518 (18 Apr 2026, SDK 0.2.112, closed "not planned") reported that in normal operation the event omits `utilization`: `{ "status": "allowed", "resetsAt": …, "rateLimitType": "five_hour" }`. Utilization appeared only once a threshold was crossed.
- The 2.1.289 binary now also carries `unifiedWindows`, described in its own schema as:

  > "@internal Per-window usage for the session (5-hour), weekly (7-day), and overage-included weekly … subscription rate-limit windows, as read from the anthropic-ratelimit-unified-\* response headers. utilization is the fraction of the window used (usually 0-1 …; values above 1 occur when usage legitimately runs past a window's cap) … both windows are tracked on every observation, and events are emitted when a window's rounded percentage or reset time moves, not only on status transitions. … always absent for API-key, Bedrock, and Vertex sessions"

- Changelog 2.1.275: "editor windows and non-interactive sessions on one machine now share a read made in the last minute instead of each calling the usage endpoint". So `-p` sessions read plan usage too.

**Uncertain:**
- Whether `unifiedWindows` actually reaches `claude -p` stream-json output. It is marked `@internal` and was not observed, because no call was made.
- Whether the per-model weekly buckets appear in it. The schema lists only `five_hour`, `seven_day` and `seven_day_overage_included`.

**Confidence.**
- **High:** the status line exposes 5h and 7d percentages and resets.
- **High:** `-p` emits `rate_limit_event` with at least status, window type and reset.
- **Medium:** full per-window utilization is available headless on 2.1.289.

**No non-generating read for an automated process.** Every surface needs either a TUI session (status line, `/usage`) or a response in flight (the event). The one direct read is the OAuth endpoint, and calling it means handling the credential.

---

## 2. ChatGPT subscription (Codex CLI)

### 2.1 What the account has

**Windows.** A `primary` and a `secondary` window, each with `used_percent`, `window_minutes` and `resets_at`.
- Observed locally:
  - `primary` = 300 min (5 h);
  - `secondary` = 10080 min (7 d);
  - `plan_type: "plus"`, `limit_id: "codex"`.
- Older logs also show `primary` with `window_minutes: 10080`. So **"primary" is not always the 5-hour window**; read `window_minutes`.
- OpenAI's pricing page (<https://learn.chatgpt.com/docs/pricing>, redirected from developers.openai.com/codex/pricing) says:

  > "Local messages and cloud chats share your plan's usage allowance. Weekly limits may also apply."

  > "Pro plans currently have no five-hour limit."

- It gives Plus estimates only as wide ranges per model ("GPT-6.1 Sol (15-160)" local messages per 5 h).
- It adds: "If you reach your usage limits during an active turn, the agent will be able to continue working on that turn, subject to fair use limits."

### 2.2 Programmatic surfaces (all verified in source or in local files)

1. **Response headers**, parsed in `codex-rs/codex-api/src/rate_limits.rs`:
   - `x-<limit>-primary-used-percent`, `-primary-window-minutes`, `-primary-reset-at`, and the same for `secondary`. `<limit>` is e.g. `codex`, so `x-codex-primary-used-percent`.
   - `x-codex-rate-limit-reached-type`.
   - An SSE rate-limit event carries the same fields.
2. **Session logs (rollouts).**
   - Every `token_count` event in `~/.codex/sessions/YYYY/MM/DD/rollout-*.jsonl` carries a full snapshot:
     ```json
     "rate_limits":{"limit_id":"codex","primary":{"used_percent":2.0,"window_minutes":300,"resets_at":1790552145},
       "secondary":{"used_percent":0.0,"window_minutes":10080,"resets_at":1791138940},
       "credits":{"has_credits":false,"unlimited":false,"balance":"0"},"plan_type":"plus","rate_limit_reached_type":null}
     ```
   - 905 such snapshots exist locally, written by the TUI, the VS Code extension, the desktop app and `codex exec`. The one `"source":"exec"` rollout has them too.
   - `codex exec --ephemeral` writes none.
3. **`codex exec --json`.**
   - The events are `thread.started`, `turn.started`, `turn.completed {usage}`, `turn.failed`, `item.*` and `error` (`codex-rs/exec/src/exec_events.rs`).
   - `usage` is tokens only. **No rate-limit snapshot in the exec JSONL**, so it must be read from the rollout file of that run.
4. **The app-server protocol** (`codex app-server`, marked "[experimental]"):
   - **`account/rateLimits/read`** returns `GetAccountRateLimitsResponse`:
     - `rateLimits`;
     - `rateLimitsByLimitId` ("Multi-bucket view keyed by metered `limit_id`");
     - `ordinaryUsageAllowed` ("Null means unavailable; clients must not infer recovery from percentages or reset times");
     - `rateLimitResetCredits`.
   - Each window is `{usedPercent, windowDurationMins, resetsAt}`.
   - **`account/rateLimits/updated`** is a "Sparse rolling rate-limit update" notification.
   - The backend call behind it is `GET …/wham/usage`-style (`backend-client`, `get_rate_limits_for_usage`). It **reads; it does not generate**.
   - This is the **one non-generating read** of either provider, and the official binary makes it.
   - Also present: `account/rateLimitResetCredit/consume`. That **spends a reset credit**, and Histmersion must never call it.
5. **`/status`** in the TUI shows the same percentages. The pricing page says "find your current limits in the usage dashboard" or use "`/status`".

**Confidence:**
- **High:** headers, rollout snapshots, and the exec JSONL lacking them (source and local files).
- **High:** the existence and shape of `account/rateLimits/read` (generated schema, source).
- **Medium:** its stability. The app-server is experimental, and the method was not exercised here.

---

## 3. Terms

**Anthropic** (sourced in research-model-providers.md §1):
- Running the unmodified `claude` binary on one's own login for one's own research is permitted.
- Two limits:
  - "Advertised usage limits for Pro and Max plans assume ordinary, individual usage".
  - "developers may not collect, store, or intermediate Claude.ai credentials or session tokens".
- What follows for reading quotas:
  - **Reading** `rate_limit_event` from our own `claude -p` stream and **pausing** is plainly within the terms. It uses the binary's own output and spends less, not more.
  - Asking the user to let their status-line script also write its JSON to a file is the user configuring their own tool. Histmersion only reads a file the user chose to produce.
  - **Calling `/api/oauth/usage` ourselves is not allowed.** It needs the OAuth token from the Keychain or `.credentials.json`, which is "collect … intermediate … session tokens". It is also the token-extraction pattern behind the February 2026 enforcement.

**OpenAI:**
- Its Terms of Use (<https://openai.com/policies/row-terms-of-use/>; the page refused automated fetches, so it is quoted via search) forbid:
  - "circumvent any rate limits or restrictions or bypass any protective measures";
  - "except as permitted through the API, use any automated or programmatic method to extract data or output from the Services".
- **Uncertain:** whether the second clause reaches `codex exec` on a ChatGPT login.
- OpenAI's own Codex docs treat it as supported. <https://learn.chatgpt.com/docs/non-interactive-mode> says "API keys are the right default for automation" but describes seeding `~/.codex/auth.json` "for trusted runners or teams needing ChatGPT rate limits", with "Treat `~/.codex/auth.json` like a password".
- Reading rollout files we own, or asking the official `codex app-server` for `account/rateLimits/read`, never touches the token. Pausing honours the limits rather than circumventing them.
- Histmersion must not read `auth.json`. It must not auto-consume reset credits or buy credits.

**Neither provider** states anything about *third-party throttles that pause on thresholds*. None is needed: such a throttle only lowers usage. The open question remains the general one, whether an always-on server is "ordinary, individual usage"; a quota floor makes it more ordinary, not less.

---

## 4. Do the limits vary with traffic?

**Anthropic: yes, at least once, officially. The specifics are partly secondary.**
- 26 March 2026: Thariq Shihipar (Anthropic) on X, quoted by The Register (<https://www.theregister.com/2026/03/26/anthropic_tweaks_usage_limits/>):

  > "To manage growing demand for Claude we're adjusting our five hour session limits for free/Pro/Max subs during peak hours."

  - Peak hours: 05:00–11:00 PT.
  - He also said: "Your weekly limits remain unchanged … just how they're distributed across the week is changing".
  - And: "~7 percent of users will hit session limits they wouldn't have before".
- 6 May 2026: Anthropic posted "Higher usage limits for Claude and a compute deal with SpaceX". According to coverage (9to5Google, <https://9to5google.com/2026/05/06/claude-code-is-getting-higher-usage-limits-doubled-for-most-users/>), it doubled Claude Code's five-hour limits and removed the peak-hours reduction for Pro and Max.
  - **Not read at the primary source here.**
  - Whether peak shaping could return is **unknown**.
- The help centre names model, effort, features and conversation length as factors (<https://support.claude.com/en/articles/11647753-understanding-usage-and-length-limits>).
- The headers include `slow-*` and `grace-*` families, so the server has throttling states beyond "allowed/rejected". **Their semantics are undocumented.**

**OpenAI: officially per-task and per-model variance. Demand-based variance is not stated.**
- From the pricing page: "Tasks that look similar can consume different amounts of your allowance. Model choice, context, reasoning, tool use, retrieval, and caching all affect usage".
- Limits are also changed by fiat: a reset of all paid plans on 28 April 2026 (community thread <https://community.openai.com/t/codex-rate-limits-reset-for-all-paid-plans-april-28-2026/1379921>), and plan re-pricing reported for 29 September 2026 (secondary blogs).
- **Rumour, unverified:** that OpenAI weights usage by current demand. No OpenAI statement was found.

**Consequence.** The user is right on the substance.
- What a percent costs in tokens is not stable across models, effort levels, time of day (Anthropic, at least historically) or plan changes.
- A token or USD cap is only a proxy.
- **The provider's own percentage is the authority.**

---

## 5. Design: a `plan` allowance kind

### 5.1 Two kinds of allowance

| | `metered` (exists) | `plan` (new) |
|---|---|---|
| applies to | API keys, local | `claude-code:*`, `codex:*` (a future provider) |
| unit | tokens / USD per rolling window | **% left per provider window**, plus reset time |
| source of truth | `agent_steps` (our ledger) | **the provider's latest snapshot** |
| others spending | none | the human, other agents, other machines |
| rule | spend + next ≤ cap − reserve | **pause while any window's left % < its floor** |

**Configuration** (per provider; a floor per window *kind*, not per index):

```
agents.allowances."claude-code:*" = { kind = plan, floors = { five_hour = 30, seven_day = 25, seven_day_opus = 25 } }
agents.allowances."codex:*"       = { kind = plan, floors = { "300m" = 30, "10080m" = 20 } }
```

- Codex windows are keyed by `window_minutes`, because `primary` and `secondary` are not fixed.
- A window present upstream but without a floor uses a default floor (e.g. 10 %). A **`rejected` / `rate_limit_reached_type` always blocks**, whatever the floors.
- The **weekly floor matters most** for the shared-pool case. Running the 5-hour window dry costs the human an afternoon; running the week dry costs them days.
- A `plan` allowance may keep an optional metered cap (tokens per window) as a secondary brake. The UI labels it **"estimate"**.

### 5.2 Reading without spending

**Claude.** Piggy-back only.
- `ClaudeCodeClient` already parses stream-json. It should also record every `rate_limit_event` into a new `plan_quota_snapshots` row: provider, window, utilization, resets_at, status, observed_at and source.
- When `unifiedWindows` is present, store each window. Otherwise store the representative window, and `utilization` when given.
- **Optional second source:** a "status-line tap". The doctor offers a one-line addition to the user's status-line script, `tee`-ing the JSON to `~/Library/Application Support/Histmersion/claude-statusline.json`. Histmersion reads that file's `rate_limits` and its mtime.
  - It is opt-in and user-installed.
  - It reflects the *human's* interactive spending too, which is exactly the shared pool.
- **No probe call.** A `-p` call to "check" spends usage and is the thing to avoid. *(Revised by §8: a
  pool paused only on a stale reading never recovered without one, so a minimal probe is now taken
  when a run waits on such a pool — at most once per `stale-minutes`, and `refresh = off` restores
  this rule.)*

**Codex.**
1. **Piggy-back:** after each `codex exec` step, read the last `token_count.rate_limits` from that run's rollout file. Do not use `--ephemeral` for this reason. Setting `CODEX_HOME` to a Histmersion-owned directory keeps those rollouts apart from the user's (**uncertain:** whether the login must then be repeated there through `codex login`, never by copying `auth.json`).
2. **Non-generating poll:** keep one `codex app-server` child (stdio JSON-RPC) and call `account/rateLimits/read`. It is cheap and spends no usage.
   - Use it **before a run starts or resumes** and on the resume timer.
   - Throttle it to at most once a minute. Claude Code 2.1.275 shares one usage read per minute for the same reason, and 2.1.284 backs off after the endpoint rate-limits.
   - Subscribe to `account/rateLimits/updated`.
3. **Optional:** tail the newest rollout under `~/.codex/sessions` to see the human's own Codex spending.

**When to check.**
- **Before every LLM attempt**, as the metered pick does now. Use the freshest snapshot; this costs nothing.
- **Staleness rule.** A snapshot older than N minutes (default 10) or older than the window's reset is "unknown":
  - Codex: poll.
  - Claude: allow **one** step if the last known left % is ≥ floor + margin; otherwise treat it as blocked until a fresh observation.
- **Mid-step.** The existing `AllowanceGuard` also reacts to a `rate_limit_event` with `status: allowed_warning`, or utilization crossing a floor, inside a running `-p` call: it ends the tool loop and moves to the next model. For Claude this is the only way to see the pool move during a long step.

### 5.3 Waiting

- A window under its floor marks the model passed over until that window's `resets_at`.
  - Use the rolling reset the provider gives. Do not compute it ourselves.
  - The 5-hour window resets in full; Claude's 7-day window also moves its reset.
- **Resume.** When no model in the chain is usable, the run pauses with reason `plan_quota`, and `resume_at` is the earliest relevant reset.
  - On resume, re-read first: Codex polls; Claude takes the status-line tap or a probe-free single step under the staleness rule.
  - The pool may have been spent by the human meanwhile. A run that wakes to a still-low window just pauses again.
- **Do not trust reset time alone for Codex** when `ordinaryUsageAllowed` is present. The schema says clients "must not infer recovery from percentages or reset times".

### 5.4 Shared pool

- Histmersion's **floors are global per provider**, not per run. Concurrent runs and parallel review-panel calls all check the same snapshot.
- Accept that steps already in flight can overshoot by one call each (as §15 deviation 3 already says). Lower `max-llm-calls-in-flight` for `plan` providers (e.g. 1–2).
- **Attribution.** Show "Histmersion spent ≈ N tokens in this window" (from `agent_steps`) next to the provider's "% used". The difference is everyone else. This is informative only and never used for the decision.

### 5.5 UI (agent models window, doctor, run page)

- **Per provider:** one bar per window with % used, its floor marker, reset time, the snapshot's age and source ("from run X 3 min ago", "status line 40 s ago", "app-server poll"), and the plan type. Show **"unknown"** honestly when there is no snapshot.
- **Paused runs:** "paused — Claude weekly 81 % used (floor 75 %), resets Thu 14:00".
- A **manual pause switch** per provider: "hold all agent use of this subscription". It is the human's "I need my quota today" button, and it is honoured before anything else.
- **Doctor:**
  - whether rate-limit data has ever been observed;
  - the Claude Code version (≥ 2.1.80 for the status line; `unifiedWindows` seen or not);
  - whether `codex app-server` answers `account/rateLimits/read`.

### 5.6 When nothing is readable

In order:

1. **React to "limit reached".** This exists as `ModelLimitReached`.
   - Claude: `rate_limit_event.status = rejected` with `resetsAt`, or the result error.
   - Codex: `turn.failed` / `rate_limit_reached_type`, with the reset from the snapshot or the error.
   - Wait until the reset, else `limit-cooldown-minutes`.
   - **Or until a reading says otherwise (#1827).** The cool-down is lifted as soon as a reading of the
     model's pool OBSERVED after the limit was reported gives a usable verdict for that model — a plan
     whose limits were reset early (0% used) no longer keeps the model parked until the old reset or a
     restart. `agent quota refresh <pool>` takes such a reading and names the models it brings back;
     `agent quota` lists the active cool-downs ("its plan reported its usage limit") and `agent models`
     marks them "cooling down", apart from an allowance's pause.
2. **The manual pause switch** (§5.5).
3. **The optional metered proxy:** tokens per rolling window, labelled "estimate — does not see your other usage, and the provider's weighting changes".

### 5.7 Chains that mix kinds

The pick stays one function. Each chain entry is checked against **every allowance covering it**, whatever its kind.

- Typical chain: `claude-code:opus > anthropic:claude-opus (API, metered $) > local:qwen`.
- **Order of effect:**
  1. The plan model is used while above its floors.
  2. Below them it is passed over until reset, and the API model takes over within its USD allowance.
  3. When that is spent too, the local model takes over, or the run pauses with the **earliest** of the plan's reset and the metered window's freeing time.
  4. A paused run re-picks from the top on resume, so it returns to the subscription as soon as it is back over its floor.
- **Opt-in fall-through.** Falling from a plan to a paid key costs real money. Per chain, `on-plan-floor = next | pause` (default **`pause`**), so a weekly floor never silently becomes an API bill. This is consistent with §15 deviation 4: only allowance or limit signals move down the chain.
- **Pause reasons.** A `plan` pause and a `metered` pause are separate reasons (`plan_quota` vs `allowance`). The UI can then say whether waiting or spending will help.

### 5.8 What not to build

- A direct call to `api.anthropic.com/api/oauth/usage` or `chatgpt.com/backend-api/wham/usage` with a token we read ourselves. That is credential handling, against both providers' rules.
- Any automatic use of Codex reset credits or purchase of credits.
- A "probe" generation to learn the quota *on a timer or for its own sake*. (§8 takes one only when a
  run waits on a pool whose reading is too old to decide on, or when a person asks.)
- Converting % to tokens as a hard rule.

---

## 6. Summary of evidence

| | Claude (Max, Claude Code 2.1.289) | ChatGPT (Codex CLI 0.160.0) |
|---|---|---|
| windows | 5 h, 7 d, per-model weekly (Opus/Sonnet/…) | primary/secondary by `window_minutes` (observed 300 and 10080); Pro "no five-hour limit" |
| unit | % used + reset epoch | % used + reset epoch (+ credits) |
| during our own calls | `rate_limit_event` in `-p` stream-json (status, type, reset; utilization at thresholds; `unifiedWindows` **uncertain**) | rollout `token_count.rate_limits` (not in `exec --json`) |
| non-generating read | **none** for automation (status-line tap if the user opts in) | **`codex app-server` → `account/rateLimits/read`** (experimental) |
| forbidden route | OAuth usage endpoint with an extracted token | reading `auth.json`; consuming reset credits |
| traffic-dependent | officially once (peak hours, Mar 2026), reportedly lifted for Claude Code May 2026 | per model/task officially; demand-based only rumoured |

---

## 7. As built: a plan model needs no price (2026-10-06, #913)

Before this, every chained model had to be in `agents.prices`, plan models included. An unpriced
`claude-code:sonnet` showed as "not ready", so the live server carried
`HISTMERSION_AGENTS_PRICES="haiku=0/0; sonnet=0/0; …"` as a workaround. A `0/0` price on a plan model
also made it look free to the USD allowances, which it is not.

**What decides it.** `PlanQuota.poolOf(model, logins)`. `logins` comes from config
(`AgentsConfig.logins`):

| model | draws on | needs a price |
|---|---|---|
| `claude-code:*` | `claude-plan`, unless `agents.providers.claude-code.use-api-key` | only with `use-api-key` |
| `codex:*` | `chatgpt-plan`, unless `agents.providers.codex.use-api-key` | only with `use-api-key` |
| `opencode:openai/*` with `chatgpt-login` | `chatgpt-plan` | no |
| other `opencode:*`, `anthropic:*`, `openai:*`, `local:*` | nothing (metered) | **yes** |

A CLI set to `use-api-key` bills the API. It now draws on no pool, so the pool's floors and its
`max-calls-in-flight` no longer apply to it, and the chain treats it as a paid model when deciding
fall-through.

**How a model is paid for** is one value, `Agents.Billing`: `Plan(pool)` or `Metered(price)`.
`AgentsHandlers.resolve` returns it next to the client, and every cost the engine computes goes
through it: the step's `cost_usd`, the run budget interceptor, the allowance guard and the probe.

- A plan model costs **0 USD**, so the run's, the day's and the month's USD budgets never stop it.
- Its tokens are recorded as before. **The run's token budget still applies** and pauses the run.
- Plan floors, `max-calls-in-flight` and the metered allowances that name it apply unchanged.
- A price written for a plan model is ignored. A plan model's cost is never read from `agents.prices`.
- **An unpriced metered model is still refused**, with the same "has no price" error. For a CLI on
  `use-api-key` the error says that the CLI bills the API. A typo still cannot make a model free.

**Surfaces.**
- `agent models` names each ready chained model's billing: `plan pool: claude-plan`, or
  `price: 3/15 USD per million tokens`. This is the `billing` field of `ChainEntry`. The web UI shows
  it in the entry's tooltip.
- `agent doctor`'s per-model check reads `plan pool: claude-plan; its client can be built` in place of
  `priced, and its client can be built`.

**Deploy.** The `HISTMERSION_AGENTS_PRICES` workaround can be dropped. Keep the prices of any metered
model. No migration.

**Not done.** `claude -p` reports an API-equivalent `total_cost_usd`. `ClaudeCodeClient` parses it,
but it is not yet recorded beside the step's tokens. Recording it as information would need a column
on `agent_steps` and a way to carry it through sttp-ai's `AgentResult`.


---

## 8. As built: a stale reading is refreshed, not waited out (2026-10-07, #989)

**The bug.** Readings came only from runs. On 2026-10-06 the Claude plan's last reading (`seven_day`
49 % left, floor 40 %) went stale. A stale reading lets a run go on only with `margin` points to spare,
and 49 < 40 + 10, so the pool reported "paused … frees at <the weekly reset>". No run could start, so
nothing could read the plan again, and the pool stayed paused for 2.5 days although it was above its
floor. A pool with NO reading counted as usable, so a ChatGPT pool whose Codex login was missing was
offered to runs that would fail.

**The rule now.** `PlanQuota.refreshNeed` and `PlanQuota.refreshDecision` (pure, core) decide; a pick
that wants the pool asks first.

| situation | what happens |
|---|---|
| fresh reading, every window above its floor | used; nothing is read |
| fresh reading, a window at or below its floor | paused until that window's reset; nothing is read |
| stale reading that would pass if it were fresh (the #989 case) | **a fresh reading first**, then the verdict on it |
| stale reading at or below the floor anyway | paused; a fresh reading would not change the answer |
| no reading (or every window's reset has passed) | **a fresh reading before the first use** |
| a hold, or a provider refusal in force | paused; nothing is read |
| the runtime has no login | **unavailable: no login** — the model is never offered, nothing is read |

- **Throttle and single flight.** At most one fresh reading per pool per `stale-minutes` (the last
  attempt counts, successful or not). One at a time per pool (`RefreshGate`): a second pick waits for
  the one in flight and decides on what it read.
- **Only when a run wants the pool.** The model pick asks (`ModelPicker.pick`, trigger `pick`). The
  allowance check (`allowance-check-seconds`) asks for each run paused with `plan-quota` that would
  not resume by itself before then (trigger `paused-run`): it picks that run's role again, and a run
  whose role now gets a model is queued at once, before the reset it was waiting for. A hold that was
  released frees its runs the same way. An idle server, or `agent quota` / `agent models` / the web
  page looking, reads nothing.
- **A held pool does not hide another pool's reset.** A run pauses until the EARLIEST time any
  model of its chain frees by itself (`jobs.resume_at`); a model that never frees by itself (its pool
  is held, or its reserve is as large as its limit) is left out of that minimum rather than voiding
  it. The pause names the reset and every held pool ("next reset at … (claude-code:opus), when the
  run resumes by itself; the ChatGPT plan is held (release it with `histmersion agent quota release
  chatgpt-plan`)"). Only a chain where nothing frees by itself pauses with no resume time
  (2026-10-08: the Claude plan at its five-hour floor plus a held ChatGPT plan read as "no window
  frees by itself").
- **Logged.** Every attempt goes to the server log and to `plan_quota_refreshes` (V069: pool, time,
  mode, trigger, ok, detail). A run's own refreshes are added to its warnings
  (`extractor: Claude plan: fresh reading (probe: five_hour 20% used, seven_day 30% used)`), and a run
  resumed early says so. `agent quota` shows the mode, "refreshed N times today" (UTC) and the last one.

### 8.1 How each pool is read

**ChatGPT plan.** `account/rateLimits/read` through `codex app-server`, as the poll already does
(§2.2). It generates nothing and **costs nothing**. On only with `agents.providers.codex.app-server`.

**Claude plan.** Looked for, without reading any credential, on Claude Code 2.1.289:

- `claude --help`, `claude auth --help`: no usage or status command for non-interactive use.
  `claude auth status` says whether and how the CLI is logged in, nothing about the windows.
- The `-p` stream: `system/init` carries no rate-limit field. `rate_limit_event` is emitted when the
  rate-limit state changes, and that state is filled from the headers of a model response.
- §3a P3 measured it: `$.session.usage().rateLimits` is `[]` at `session.start` and filled only after
  the first response.
- The binary's own startup quota check (interactive sessions only) is itself a model request:
  `messages.create` with `max_tokens: 1`, the single user message `"quota"`, on the small fast model.
- The direct read, `GET /api/oauth/usage`, needs the OAuth token; that stays off limits (§3).

So there is **no zero-cost read** for an automated process, and `auto` means the probe. The probe is
the smallest turn `claude -p` can make, the same shape as Claude Code's own check:

```
claude -p --output-format stream-json --verbose --model <probe-model> --max-turns 1 --no-session-persistence
  --setting-sources "" --system-prompt "Reply with one word." --tools "" --strict-mcp-config
  --disable-slash-commands --permission-mode dontAsk  [extra-args]        stdin: quota
CLAUDE_CODE_MAX_OUTPUT_TOKENS=<probe-max-tokens>; ANTHROPIC_API_KEY/AUTH_TOKEN removed (unless use-api-key)
```

- It runs in an empty scratch directory, with no plugin, no mod, no MCP server, no skills and no
  settings files; the login stays (§3a P2).
- Its `rate_limit_event` (both windows, `unifiedWindows`, §3a P1) is recorded with source `probe`.
- A probe the plan refuses records what it says: a `rejected` event as a refused window, or, with
  only a "usage limit" result, a refused `usage_limit` window until the reset it names (else
  `limit-cooldown-minutes`).
- A probe that says nothing about the plan (an error, a timeout) is logged as a failed attempt; the
  pool keeps its verdict until the next attempt, `stale-minutes` later.

**Cost per refresh — an estimate, not measured here** (no plan-spending call was made while building
this):

- One Haiku request: the one-line system prompt, the word "quota", and at most `probe-max-tokens`
  (16) output tokens. Claude Code adds a few fixed blocks of its own, so input stays in the tens to
  low hundreds of tokens.
- At Haiku's list price that is well under US$0.001 a call. For comparison, §3a's call 1 (two turns,
  a tool, Claude Code's tool prompts) reported US$0.011.
- On a plan, the provider decides what a call costs in %. One such call should not move a window
  by a whole point (§3a: short calls never did).
- At most one per pool per `stale-minutes` while a run waits: 6 an hour, 144 a day in the worst case
  (a run that waits all day on a pool whose probe keeps failing). Normally it is one per stale spell.
- `refresh = off` restores the old behaviour.

### 8.2 Logins

- **Claude plan:** `claude auth status`, read the way the doctor reads it. Logged out, or logged in
  with an API key while the plan is wanted, is **no login**. An answer that is not JSON or a command
  that fails is "not known", and does not make the pool unavailable: a flaky check must not take a
  working plan away.
- **ChatGPT plan:** the runs' `CODEX_HOME` has an `auth.json`. The file's presence is checked; it is
  never opened.
- Checked at most once per `poll-seconds`; `agent quota refresh` checks again first.
- Only the model whose own runtime spends the pool is gated: `claude-code:*` on the Claude plan,
  `codex:*` on the ChatGPT plan. An `opencode:openai/*` model on a ChatGPT login signs in by itself.
- An unavailable model is passed over like an unbuildable one. A chain with nothing else fails the
  step with the reason ("unavailable: no login — …"), it does not wait.
- `agent quota` shows the pool as **unavailable: no login**, and `agent models` shows its models as
  not usable with that reason.

### 8.3 Surfaces

- `histmersion agent quota refresh [<pool>]` · `POST /api/agents/quota/refresh {pool?}` · MCP
  `set_agent_models { refresh: true, pool? }` — take a fresh reading now, whatever its age (the
  throttle does not apply; single flight does). This is useful after `codex login` or `/login`, and
  for debugging. A write: it needs the API key when one is set.
- The web page's plan block has a **refresh** button per pool and the refresh line.
- `agent quota` (Markdown and `PoolStatus`) adds `available`, `login`, `refresh` (the mode),
  `refreshesToday` and `lastRefresh`.

**Config** (`agents.quota.claude-plan`): `refresh = auto | probe | off`
(`HISTMERSION_AGENTS_CLAUDE_PLAN_REFRESH`, default `auto`), `probe-model = "haiku"`
(`HISTMERSION_AGENTS_CLAUDE_PLAN_PROBE_MODEL`), `probe-max-tokens = 16`, `probe-timeout-seconds = 120`.
An unknown `refresh` value takes no fresh reading, and `agent quota` names the bad value.

**Deploy.** Migration V069 (`plan_quota_refreshes`). No other step; the new config keys have defaults.
On the live server, the stale Claude pool of #989 takes one probe the next time the allowance check
finds a run waiting on it, or at once with `histmersion agent quota refresh claude-plan`.

**Not done.** A refresh for the human's own spending while no run waits (the status-line tap of §5.2)
is still not built. The refresh log is not pruned: at most about 300 rows a day.

## 9. As built: a scheduled reading while the autopilot has work (2026-10-24, #4035)

§8 reads a pool only when a run's pick wants it and its reading is stale, so with no run picking (the
pool below its floor, or the scheduler holding its items back) the reading the scheduler decides on
can be days old: the last Claude reading on the live server was from 2026-10-17. Each pool now also
has a schedule, `agents.quota.<pool>.refresh-every-minutes`, checked after every managers' tick:

- the autopilot is on, nothing is draining, and the last scheduler pass left work **waiting** on the
  pool — an item whose roles draw on it, or one starved because its role chains are below their plans'
  floors (the pools of every model in those chains: a fresh reading may show the window has reset);
- neither the pool's newest reading (whoever took it: a run, the probe, a mod) nor the last attempt to
  take one is younger than the interval — a failed attempt waits the interval too;
- the pool has a login and a way to read (`claude-plan.refresh` not `off`; Codex's `app-server` on).

It takes the same reading as §8.1 (the Claude probe, the ChatGPT app-server read) inside the pool's
single-flight gate, and logs it with trigger `schedule`. Defaults: the Claude plan every 180 minutes
(at most eight haiku turns a day, and only while work waits on it), the ChatGPT plan every 30 (its
read spends nothing). `0` turns a schedule off. `agent quota`'s refresh line names the schedule.

**Config**: `agents.quota.claude-plan.refresh-every-minutes = 180`
(`HISTMERSION_AGENTS_CLAUDE_PLAN_REFRESH_EVERY_MINUTES`), `agents.quota.chatgpt-plan.refresh-every-minutes = 30`
(`HISTMERSION_AGENTS_CHATGPT_PLAN_REFRESH_EVERY_MINUTES`). No migration.
