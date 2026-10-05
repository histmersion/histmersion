# Model providers: the Claude subscription, API keys, local models (research, 2026-10-05)

What Histmersion's embedded agents (docs/research-embedded-agents.md) may run on, and on what terms.
Three questions:

1. May Histmersion drive Claude Code headlessly (`claude -p`) on the user's own Claude subscription?
2. Which local runtime fits Apple Silicon and our pipelines?
3. What follows for the build (§3)?

Everything quoted was fetched on 2026-10-05. **Uncertain** marks a point the sources leave open.

---

## 1. `claude -p` on a Claude subscription

### 1.1 What the documents say

**Claude Code, Legal and compliance** (<https://code.claude.com/docs/en/legal-and-compliance>)

- Who governs what:
  > "Commercial Terms of Service — for Team, Enterprise, and Claude API users"
  > "Consumer Terms of Service — for Free, Pro, and Max users"
- Usage:
  > "Advertised usage limits for Pro and Max plans assume ordinary, individual usage of Claude Code and the Agent SDK."
- Credentials:
  > "**OAuth authentication** is intended exclusively for purchasers of Claude Free, Pro, Max, Team, and Enterprise subscription plans and is designed to support ordinary use of Claude Code and other native Anthropic applications."
  >
  > "**Developers** building products or services that interact with Claude's capabilities, including those using the Agent SDK, should use API key authentication through Claude Console or a supported cloud provider. Anthropic does not permit third-party developers to offer Claude.ai login into their own applications, or to route requests through Free, Pro, or Max plan credentials on behalf of their users. Moreover, developers may not collect, store, or intermediate Claude.ai credentials or session tokens — sign-in to a Claude account must complete through Anthropic's own flow."
  >
  > "This does not restrict how customers provision and manage their own API keys … Nor does it prevent an end user from signing in to the unmodified Claude Code binary with their own Claude subscription, including where a platform hosts Claude Code as described under *Can customers offer Claude Code in their products?* above."
- Shipping Claude Code inside a product:
  > "preinstalling or running Claude Code in your products or services … requires agreeing to our Commercial Terms of Service and complying with the conditions below: **The Claude Code binary must not be modified.** … **Customers may not pay for, resell, or intermediate Claude usage on their end users' behalf.** Each end user must authenticate with their own Anthropic API key, Claude subscription plan credentials, or 3P inference provider credential"
- Naming:
  > "You can accurately say, in plain text, that your product has Claude Code preinstalled or that it runs Claude Code. But you can't use the Claude Code or Anthropic names or logos as part of your own product, feature, or company name"

**Consumer Terms** (<https://www.anthropic.com/legal/consumer-terms>, effective 8 October 2025)

- Automated access is forbidden with an exception:
  > "Except when you are accessing our Services via an Anthropic API Key or where we otherwise explicitly permit it, to access the Services through automated or non-human means, whether through a bot, script, or otherwise."
- Credentials may not be shared:
  > "You may not share your Account login information, Anthropic API key, or Account credentials with anyone else or make your Account available to anyone else."
- Personal use only:
  > "you agree that you will not use our Services for any commercial or business purposes"

**Headless mode, as Anthropic documents it** (<https://code.claude.com/docs/en/headless>, <https://code.claude.com/docs/en/authentication>)

- `claude -p` is documented "for scripts and CI/CD".
- `--output-format json` returns "`total_cost_usd` and a per-model cost breakdown, so scripted callers can track spend".
- On the long-lived token:
  > "For CI pipelines, scripts, or other environments where interactive browser login isn't available, generate a one-year OAuth token with `claude setup-token` … This token authenticates with your Claude subscription"
- Precedence:
  > "In non-interactive mode (`-p`), the key is always used when present."

  So an `ANTHROPIC_API_KEY` in the environment silently turns a subscription call into an API-billed one.
- Bare mode:
  > "In bare mode, Claude Code never reads OAuth credentials or the system keychain."

  `--bare` is therefore incompatible with subscription use.

**Agent SDK with a Claude plan** (support article, last updated 16 June 2026,
<https://support.claude.com/en/articles/15036540-use-the-claude-agent-sdk-with-your-claude-plan>)

- It announced a monthly "Agent SDK credit" covering "Personal Claude Agent SDK projects", "The `claude -p` command in Claude Code (non-interactive mode)" and "Third-party apps that authenticate with your Claude subscription through the Agent SDK".
- The 15 June update paused that credit:
  > "Claude Agent SDK, `claude -p`, and third-party app usage still draw from your subscription's usage limits."

**Agent SDK overview** (<https://code.claude.com/docs/en/agent-sdk/overview>)

> "Unless previously approved, Anthropic does not allow third party developers to offer claude.ai login or rate limits for their products, including agents built on the Claude Agent SDK. Use the API key authentication methods described in the Quickstart instead."

**The 2026 enforcement against third-party harnesses** (The Register, 20 February 2026,
<https://www.theregister.com/2026/02/20/anthropic_clarifies_ban_third_party_claude_access/>)

The article quotes the terms:

> "Using OAuth tokens obtained through Claude Free, Pro, or Max accounts in any other product, tool, or service — including the Agent SDK — is not permitted"

It quotes an Anthropic engineer:

> "Third-party harnesses using Claude subscriptions create problems for users and are prohibited by our Terms of Service. They generate unusual traffic patterns without any of the usual telemetry that the Claude Code harness provides"

The tools blocked (OpenClaw, OpenCode and others) **extracted the OAuth token and called the API themselves**. They did not run the Claude Code binary.

### 1.2 Verdicts

**(a) The user's own automation, on their own machine, through their own login: permitted, within limits.**

These conditions keep it permitted:

- Histmersion runs the **unmodified `claude` binary** as a subprocess.
- The user signs in through **Anthropic's own flow**: `claude` → `/login`, or `claude setup-token`.
- Histmersion never reads, stores or forwards a credential. It does not touch the Keychain or `~/.claude/.credentials.json`, and it does not pass `CLAUDE_CODE_OAUTH_TOKEN` itself.
- The use is the person's own non-commercial research.

Why this reading holds:

- Anthropic itself documents `claude -p` for scripts.
- Its support article states that `claude -p` "draw[s] from your subscription's usage limits".
- The legal page says the restrictions do not prevent "an end user from signing in to the unmodified Claude Code binary with their own Claude subscription". That is the "where we otherwise explicitly permit it" exception the Consumer Terms leave open for automated access.
- This is not the harness pattern Anthropic banned (token extraction). Every call goes through Claude Code with its own telemetry.

**Uncertain:**

- What "ordinary, individual usage" means for an always-on server firing many `-p` calls. No document gives numbers. Heavy autonomous use may exhaust the plan or be treated as abuse.
- Whether the paused Agent SDK credit, if it returns, will meter `-p` separately.

The caps in §3 exist for both reasons. The safe default is **off**, opt-in per role, with a token cap per rolling window below what the user wants to keep for interactive work.

**(b) Shipping Histmersion to other people who use their own subscriptions (#416): not safe to offer.**

The documents point both ways:

- Against: "Anthropic does not permit third-party developers to offer Claude.ai login into their own applications, or to route requests through Free, Pro, or Max plan credentials on behalf of their users". Also "Unless previously approved … third party developers [may not] offer claude.ai login or rate limits for their products".
- For: "Nor does it prevent an end user from signing in to the unmodified Claude Code binary with their own Claude subscription". The paused credit also listed "Third-party apps that authenticate with your Claude subscription through the Agent SDK".

An open-source, self-hosted Histmersion that shells out to a `claude` its user installed and logged into is arguably that end user running the unmodified binary. But advertising it as "use your Claude subscription" is exactly "offer[ing] … rate limits for their products".

**Verdict:**

- The provider may stay in the code, **off by default and undocumented as a selling point**.
- The README and setup docs should present an **API key** as the way to run agents.
- Anyone enabling `claude-code` does so for their own installation, on their own machine.
- Before the open-source release advertises the provider, ask Anthropic (<https://www.anthropic.com/contact-sales>, as the legal page suggests).
- A server reachable by **other people** must not spend the owner's plan on their requests. That is routing "requests through … plan credentials on behalf of their users". The provider is therefore refused when the server listens on a non-loopback address, unless the owner states that every client is themselves (§3.2).

**API keys (Anthropic, OpenAI): permitted without these restrictions.**

- Anthropic keys fall under the Commercial Terms. The legal page explicitly allows "configuring an API key … for use by the customer's own authorized users … provided the resulting usage is billed to the key owner".
- OpenAI keys fall under OpenAI's Services Agreement (<https://openai.com/policies/services-agreement/>). It has no comparable restriction on scripted use. **Not re-read in detail for this note.**

**Naming.** Calling the provider `claude-code` in configuration ("runs Claude Code") is the plain-text description the legal page allows. No UI label may brand a Histmersion feature as "Claude Code".

---

## 2. Local models on Apple Silicon

The machine: an M4 Pro with 48 GB unified memory, on macOS 26.7.

### 2.1 What our pipelines need

| need | why |
|---|---|
| OpenAI-compatible `/v1/chat/completions` | the `local` provider is sttp-ai's OpenAI client |
| tool calling | classify, the review panel and the planner call `get_source`, `search`, `facets`… |
| JSON-schema structured output (`response_format: json_schema`) | every step's answer is a typed schema |
| **both in one request** | a step offers tools AND asks for the typed answer |
| 32k+ context | enrich-source sends 12k-character body windows plus a prompt; review sends whole articles (≤ 60k chars) |
| a headless daemon | Histmersion runs as a LaunchAgent; nobody opens a GUI |

### 2.2 Candidates

| runtime | install / ops | tools | JSON schema | Apple Silicon | licence, upkeep |
|---|---|---|---|---|---|
| **llama.cpp `llama-server`** | `brew install llama.cpp`; one binary, one model per process (`-hf org/model:Q4_K_M`), runs fine as a LaunchAgent | yes, with `--jinja` ("OpenAI API compatible chat completions … with tool calling support") | "Schema-constrained JSON response format" via grammar | Metal, the reference implementation | MIT; very active |
| **LM Studio** (`llmster` daemon + `lms`) | installer script; `lms daemon up`, `lms server start`; GUI-free since 0.4 (28 Jan 2026); manages downloads | yes ("Tool Use") | yes: "For `GGUF` models: utilize `llama.cpp`'s grammar-based sampling APIs. For `MLX` models: using Outlines" | llama.cpp and MLX engines | proprietary, free including at work (since July 2025); active |
| **MLX-LM `mlx_lm.server`** | `pip install mlx-lm` | not documented | **not supported**: it ignores `response_format` (mlx-lm #1007); "not recommended for production as it only implements basic security checks" | the fastest on MLX | MIT; active, but its server is a demo |
| **vLLM + vllm-metal** | Python env plus the plugin; first official release v0.28.0 (Sept 2026) | yes (vLLM's parsers) | yes ("structured outputs" in v0.28.0) | Metal/MLX, concurrent batching | Apache-2.0; young on Mac (**uncertain** maturity) |
| **Ollama** | app or brew; its own model store and daemon | yes | yes (`format`) | llama.cpp-based, Metal | MIT; active |
| LocalAI, Jan | LocalAI: a Go server over llama.cpp and others; Jan: a desktop app with a local server | yes (through llama.cpp) | through llama.cpp grammars | through llama.cpp | MIT / AGPL; **not evaluated in depth** |

**Why Ollama disappointed here** (§11 of the agents study):

- Its Homebrew install was removed while the daemon kept running.
- The daemon still listed models, but every generation failed with `llama-server binary not found`.
- So a daemon can look healthy while being unable to answer, which is why `agent doctor` must check reachability and the presence of the model.

Other complaints are widely reported:

- Its default context window is small, and a prompt longer than it is **truncated silently**. On the OpenAI-compatible endpoint the context cannot be raised per request; a Modelfile is needed. Our 12k-character windows would be cut.
- It keeps a second model store, separate from LM Studio and llama.cpp.
- It has known structured-output bugs on its newer engines (e.g. ollama #18567, #18717).

**Uncertain: tools and `response_format` in the same request.** llama-server has answered "Either 'json_schema' or 'grammar' can be specified, but not both" when a tool grammar and a schema met, according to issue reports. It may be fixed in current builds. `histmersion agent probe <citekey> --model local:<model>` exercises exactly this combination: one tool call, then a typed answer. Run it once before trusting a local model.

### 2.3 Models (48 GB, leaving room for Postgres, the server and the OCR sidecar)

Function-calling round-ups (e.g. <https://insiderllm.com/guides/function-calling-local-llms/>, updated July 2026) rank these:

| model | resident at Q4 | notes |
|---|---|---|
| Qwen 3.6 35B-A3B (MoE) | ~24 GB | ~3B active parameters, so fast |
| Qwen 3.6 27B (dense) | ~17 GB | slower, often more careful |
| Gemma 4 26B-A4B | ~15 GB | — |
| Llama 3.3 70B | ~42 GB | too tight on 48 GB with everything else running |

The Qwen 3 generation is Apache-2.0. **Uncertain:** the exact licences of Qwen 3.6 and Gemma 4; check the model card before use.

### 2.4 Recommendation

**llama.cpp `llama-server`, with Qwen 3.6 35B-A3B at Q4_K_M and a 32k context.** Two slots, run as its own LaunchAgent:

```bash
llama-server -hf Qwen/Qwen3.6-35B-A3B-GGUF:Q4_K_M --jinja -c 65536 --parallel 2 \
  --host 127.0.0.1 --port 8080
```

Context is split between slots, so this gives 32k per slot. Then point Histmersion at it:

```
HISTMERSION_AGENTS_LOCAL_URL=http://127.0.0.1:8080/v1
HISTMERSION_AGENTS_PRICES="Qwen3.6-35B-A3B=0/0"
```

The price key must be the model id the server reports; `histmersion agent doctor` lists it.

Why this pair:

- It is the most direct path to grammar-constrained JSON schemas, which our validators otherwise have to retry for.
- It is open source and a single binary under launchd. Nothing else to keep running.
- The MoE model's ~3B active parameters keep long enrich windows tolerable on an M4 Pro.

**LM Studio** is the friendlier alternative when downloading and switching models through a UI matters more than an open licence. It wraps the same llama.cpp grammars.

**MLX-LM's server** is not suitable until it honours `response_format`.

**vLLM-metal** is worth re-checking in 2027 for concurrent runs.

Nothing was installed for this note.

---

## 3. What was built from this (see docs/research-embedded-agents.md §15)

1. **`claude-code` provider** (verdict (a) allows it): `claude -p` per LLM step.
   - Invocation:
     - `--output-format stream-json --verbose`, `--json-schema`, `--system-prompt-file`;
     - `--tools ""` (no built-in tools), `--strict-mcp-config`, and an MCP config pointing at Histmersion's own read-only `/mcp/agent` endpoint;
     - `--allowedTools` = the step's allowlist, every other agent tool in `--disallowedTools`, `--permission-mode dontAsk`;
     - `--no-session-persistence`, hooks disabled, an empty working directory.
   - Off by default.
   - The server strips `ANTHROPIC_API_KEY` and `ANTHROPIC_AUTH_TOKEN` from the child's environment unless `use-api-key = true`, so a stray key cannot silently bill the API.
   - It never touches credentials.
   - It is refused on a non-loopback bind unless `allow-non-loopback = true` (verdict (b)).
   - `agent doctor` warns that it spends the plan's allowance.
2. **`agent doctor`.** Per provider:
   - the key, or `claude` and `claude auth status`;
   - a free model listing (`GET /v1/models`), never a generation;
   - the configured models present upstream;
   - every chained model priced.
3. **Model chains with allowances.** Per role, an ordered list of models. Per model or per provider (`claude-code:*`), limits on tokens and/or USD per rolling window, with a reserve.
   - The engine takes the first model with headroom above its reserve.
   - When none has headroom, the run pauses (`allowance`) with the time the earliest window frees enough, and is resumed then automatically.
   - For `claude-code` the plan's real remaining usage is not observable from outside. Its allowance is only the cap the user configures, counted from the usage `claude -p` reports.
