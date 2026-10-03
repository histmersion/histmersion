# Embedded research agents: design study

**Status:** research and proposal (2026-10-03). Nothing in it is built yet. It sits alongside
[`design.md`](design.md) (what is built) and [`plan.md`](plan.md) (the roadmap).

## 0. The problem in one paragraph

Today Histmersion only stores data. Everything that needs judgment runs in an external Claude Code
session: the skills under `skills/` (mirrored in the research workspace's `.claude/skills/`), the four
review subagents (`article-rules-reviewer`, `article-cold-reader`, `article-skeptic`, `article-fixer`),
and the hooks (`article-gate.sh`, `calibre-gaps.sh`). That session also does deterministic work it should
not have to do. It copies `updated_at` strings between shell commands for `--if-updated-at`. It merges
three reviewers' JSON outputs. It counts review rounds, ticks queue items and greps lint output. A person
has to keep the session open and watch it. This document looks at how to move the work inside the
server, so that the CLI and web UI can start it, trace it, budget it and approve it. Code takes over the
deterministic steps. The model is left with only the judgment calls.

## 1. What exists today that the design can build on

The repo already contains most of the pieces.

| Existing piece | Where | Why it matters here |
|---|---|---|
| One `Handlers` implementation behind REST, MCP and CLI | `server/Handlers.scala`, `McpTools.scala` (≈49 tools) | An agent's tools can call the same functions directly, in-process, without going through MCP over HTTP |
| Tool input types with a derived Tapir `Schema` and circe codec (Kindlings) | `core` DTOs, `McpTools` inputs | Those are the exact typeclasses that `sttp-ai` needs to turn a function into an LLM tool (§2.1) |
| A `jobs` table and a bounded Ox worker pool that marks interrupted jobs as failed on restart | `V010`, `Jobs.scala` | The starting point for agent runs. It currently has no steps and cannot resume |
| `goals` and `agent_state` tables | `V019` | Topic/goal management already has a table. It only needs a planner and a scheduler |
| `queue_items` (acquire/tasks/log) and `jobs.acquire_item` | `V009`, `V021`, `V028` | Code can already close a queue item when an ingest finishes. Pipelines can use the same mechanism |
| `article_versions` (the previous body is saved on every write) | `V013` | Before/after diffs for an agent's article edits come with no extra work |
| Optimistic concurrency (`ifUpdatedAt` → 412) | patch endpoints | An embedded agent and an external one can safely write the same rows |
| `lint` rules: `article-filler`, `article-uncited-paragraph`, `superseded-without-edge`, `year-citekey-mismatch`, `non-canonical-tag`, `undated-findings`, `unlocated-findings`… | `core/Lint.scala` | Most of the mechanical review checks (`M<n>`) already exist as code |
| Bearer-token write gate | `AuthHandler.scala`, `auth.api-key` | Runs started from the UI must be authenticated as writes |
| HOCON config with env overrides, "no hardcoded constants" | `reference.conf`, plan §1.9 | Providers, prices and budgets go here |
| Calibre is host-local and only the CLI touches it | `cli/Calibre*.scala` | An embedded pipeline cannot push to Calibre itself. It has to leave a gap for `calibre gaps`/`sync` to pick up (§3) |

## 2. Candidate frameworks

Every entry was checked on GitHub or the vendor docs on 2026-10-03. Stars and versions are as of that
date.

### 2.1 SoftwareMill, and what "Orca" is

- **There is no SoftwareMill "Orca".** **Orca** is by **VirtusLab**:
  [github.com/virtuslab/orca](https://github.com/virtuslab/orca) ("Deterministic, AI-driven development
  flows", Apache-2.0, v0.1.10 on 2026-09-24, about 180 stars). SoftwareMill has been part of the
  VirtusLab Group since April 2023 ([announcement](https://softwaremill.com/softwaremill-joins-virtuslab-group-a-powerful-merger-for-tech-innovation/)),
  which probably explains the mix-up.
  - What it is: flows written as Scala 3 scala-cli scripts. Each flow drives coding-agent CLIs
    (Claude, Codex, OpenCode, Pi, Gemini), so it does not call model APIs itself. Each `stage()` is
    git-committed as it finishes, and a re-run continues from the last commit.
  - Fit: the idea is the right one (deterministic Scala orchestration with agent stages, resumable
    per stage). The mechanism does not fit. Its unit of state is a git working tree, and its "agent"
    is a logged-in coding CLI. It is also at version 0.1.x. **Borrow the pattern; do not depend on it.**
- **SoftwareMill's actual offering is [`sttp-ai`](https://github.com/softwaremill/sttp-ai)**
  (formerly `sttp-openai`). Apache-2.0, v0.11.4 on 2026-10-01, actively released. Its documented
  features:
  - Provider clients for OpenAI, Claude (Messages API, including `CacheControl` and cache-token
    accounting), Gemini, and OpenAI-compatible endpoints such as Ollama, OpenRouter and Groq.
  - An effect-polymorphic **agent loop** (`ClaudeAgent` / `OpenAIAgent` / `GeminiAgent`, with a
    `.synchronous(...)` `Identity` variant) with `maxIterations`, `maxTokens`, a per-iteration model
    choice and typed results (`deriveResponseSchema[T]`).
  - Tools from case classes or a whole trait (`AgentTool.fromFunction`, `AgentTools.derive[Service]`)
    using a **Tapir `Schema` + circe codec**.
  - **Interceptors** (`aroundToolCall`, `aroundLlmCall`, `decide`), a `BudgetInterceptor` with token
    and cost caps over a `PriceTable` you supply, and per-call usage including cached tokens.
  - An `mcp` module that loads tools from an MCP server through **Chimp's** client.
  - An **`agent-testkit`** (`ScriptedAgent`, `ScriptedResponse`) that runs the real loop against a
    scripted model.
  - **Version alignment with Histmersion is unusually good**: sttp-ai pins tapir 1.13.31 and
    sttp-client4 4.0.26, the same versions Histmersion uses. Its `mcp` module needs chimp 0.6.0
    (Histmersion has 0.5.2). That bump only matters if we use the `mcp` module, which this design does
    not need. sttp-ai is built with Scala 3.3.8 LTS, so a 3.8 project can consume it.
  - Gaps:
    - Native-image compatibility is not stated. It is likely, given direct style, the JDK HttpClient
      backend and derivation at compile time instead of reflection, but **unverified**. A spike is
      needed.
    - It has no durability or resume. One `agent.run` is an in-memory loop.
    - It has no OpenTelemetry integration. An interceptor can emit spans.
- Related SoftwareMill pieces Histmersion already uses: **Ox** (structured concurrency, which runs the
  job pool), **Chimp** (MCP server, and a client from 0.6) and **Tapir**.

### 2.2 Other JVM options

- **[LangChain4j](https://github.com/langchain4j/langchain4j)** (Apache-2.0, 1.21.0, about 13k stars).
  Unified providers, an MCP client, and `langchain4j-agentic` (sequential, loop, parallel, conditional
  and supervisor patterns; [docs](https://docs.langchain4j.dev/tutorials/agents/)). It is Java-first
  and builds on reflection and proxies (`AiServices`), so native-image works mainly through
  Quarkus/Spring AOT. It would be a second JSON/schema stack next to circe/Kindlings.
- **[Spring AI](https://github.com/spring-projects/spring-ai)** (Apache-2.0, 2.1.0-M1). Needs Spring
  Boot. That is out of the question for a direct-style Tapir/Netty server.
- **[Koog](https://github.com/JetBrains/koog)** by JetBrains (Apache-2.0, 1.3.0). Kotlin, with
  graph/strategy agents, MCP, agent persistence or checkpoints, OpenTelemetry exporters (Langfuse,
  Weave) and many providers including Ollama. It is the most complete JVM agent framework, but it
  brings Kotlin coroutines and kotlinx-serialization into a Scala 3 native image. That is possible but
  costly, and its native-image status is **unverified**.
- **[Embabel](https://github.com/embabel/embabel-agent)** (Apache-2.0, 1.5.2). Kotlin/Spring, with GOAP
  (goal-oriented action planning, where the framework plans the order of actions). It requires Spring.
  Rejected for that reason, but its "goals → actions planned over typed domain objects" idea maps well
  onto `goals` (§5, P3).
- **Akka SDK agents** ([akka/akka-sdk](https://github.com/akka/akka-sdk)). **BSL 1.1** licence and an
  Akka runtime. Rejected.
- **[Anthropic Java SDK](https://github.com/anthropics/anthropic-sdk-java)** (MIT, v2.68.0). The
  official client, with a beta `BetaToolRunner` ([tool runner docs](https://platform.claude.com/docs/en/agents-and-tools/tool-use/tool-runner)).
  It supports one provider only and uses Jackson and reflection to derive tools from classes.
  **[OpenAI Java SDK](https://github.com/openai/openai-java)** (Apache-2.0, v4.75.1) has the same
  limitation. Both are good references; neither is a better base than sttp-ai.
- **[DBOS Transact Java](https://github.com/dbos-inc/dbos-transact-java)** (MIT, 1.2.0). Durable
  workflows that checkpoint each step to **Postgres** with no extra service, which is exactly the
  durability model Histmersion needs. It registers workflows through proxies, so native-image cost is
  **unknown**. Recommendation: copy the pattern (one checkpoint row per step, replay on restart), which
  is a few hundred lines on top of Magnum and Ox.

### 2.3 Options outside the JVM (sidecar)

- **Claude Agent SDK** ([Python](https://github.com/anthropics/claude-agent-sdk-python), MIT;
  [TypeScript](https://github.com/anthropics/claude-agent-sdk-typescript); [overview](https://code.claude.com/docs/en/agent-sdk/overview)).
  - "A library that runs the Claude Code binary": built-in tools, hooks, subagents, MCP, permissions,
    sessions, and skills/plugins loaded from `.claude/`. It is the closest match to *today's*
    workflow, because the skills and agent files would run unchanged.
  - Limits: Anthropic models only. It is governed by Anthropic's Commercial Terms. Third-party
    products may not use claude.ai login or rate limits unless previously approved, so it must use API
    keys. It adds a Node/Python sidecar plus the Claude Code binary to the bundle.
- **[OpenAI Agents SDK](https://github.com/openai/openai-agents-python)** (MIT, v0.23.1). Handoffs,
  guardrails, tracing, MCP, and other providers through LiteLLM. Temporal has an official integration
  ([docs](https://docs.temporal.io/develop/python/integrations/openai-agents)).
- **[LangGraph](https://github.com/langchain-ai/langgraph)** (MIT). Graph state machines with
  checkpointers, including Postgres. A Python sidecar.
- **[pydantic-ai](https://github.com/pydantic/pydantic-ai)** (MIT, v2.54.0). Typed agents in Python,
  many providers. It would fit next to the existing Python converter.
- **Temporal** ([Java SDK](https://github.com/temporalio/sdk-java), Apache-2.0, v1.40.0).
  - The strongest durable-execution engine.
  - It needs a Temporal server (an extra service plus its own database) in a bundle that is
    deliberately one LaunchAgent.
  - The Java SDK supports native-image only with user-supplied metadata
    ([AOT notes](https://github.com/temporalio/sdk-java/blob/master/docs/AOT-native-image.md)).
- **Prefect** ([Apache-2.0](https://github.com/PrefectHQ/prefect)) and **Inngest**
  ([SSPL with a delayed Apache-2.0 licence](https://github.com/inngest/inngest)). Both are separate
  orchestration services. Too heavy for a single-user local bundle.
- **Tracing backends:**
  - **[Langfuse](https://github.com/langfuse/langfuse)**: MIT outside `ee/`. The copyright is now
    ClickHouse, Inc. It accepts OpenTelemetry.
  - **OpenTelemetry GenAI semantic conventions**: moved to
    [open-telemetry/semantic-conventions-genai](https://github.com/open-telemetry/semantic-conventions-genai).
    Their stability level is **not confirmed here**; treat them as still evolving.

### 2.4 Comparison

| Option | Language fit (Scala 3 + native-image) | Reuse Histmersion tools in-process | Multi-provider (Anthropic / OpenAI / local) | Durability / resume | Tracing | Cost control | Licence |
|---|---|---|---|---|---|---|---|
| **sttp-ai** (SoftwareMill) | **Native Scala 3**, same tapir/sttp versions, direct-style `Identity`; native-image unverified but likely | **Yes**: `AgentTool.fromFunction` over existing Kindlings `Schema`+codec types | Yes / yes / yes (OpenAI-compatible base URL) | None (bring your own) | Interceptors → own tables / OTel | `BudgetInterceptor`, usage incl. cache tokens, `PriceTable` | Apache-2.0 |
| VirtusLab Orca | Scala 3 scripts, but drives CLIs | No (agents are external CLIs) | Via whichever CLI | Git-commit per stage | CLI logs | None of its own | Apache-2.0 |
| LangChain4j | Java; reflection/proxies, native via Quarkus/Spring | Via its `@Tool` model; second schema stack | Yes | Partial (agentic module); no durable store | Listeners; OTel via integrations | Manual | Apache-2.0 |
| Koog | Kotlin; coroutines in a Scala native image | Kotlin tool DSL wrappers | Yes, incl. Ollama | Agent persistence (checkpoints) | OTel, Langfuse, Weave | Manual | Apache-2.0 |
| Spring AI / Embabel | Spring Boot required | Via Spring beans | Yes | No / planner, not durable | Micrometer | Manual | Apache-2.0 |
| Akka SDK agents | Akka runtime | — | Yes | Yes (event-sourced) | Yes | — | **BSL 1.1** |
| Anthropic / OpenAI Java SDK | Java, Jackson; single provider each | Manual loop or beta tool runner | One each | No | No | Usage fields | MIT / Apache-2.0 |
| Claude Agent SDK (sidecar) | Python/TS + Claude Code binary | Via MCP to `/mcp` (loopback) or SDK in-process MCP | **Anthropic only** (API key, Bedrock, Vertex) | Sessions resume; no step checkpoints | Hooks; cost per result | `maxTurns`, cost reporting | Anthropic Commercial Terms |
| OpenAI Agents SDK (sidecar) | Python | Via MCP | Yes (LiteLLM) | With Temporal | Built-in tracing | Manual | MIT |
| LangGraph / pydantic-ai (sidecar) | Python | Via MCP | Yes | Postgres checkpointer (LangGraph) | LangSmith / Logfire / OTel | Manual | MIT |
| Temporal | Java SDK needs AOT config; extra server | Activities call Handlers | n/a (engine) | **Best in class** | OTel | n/a | Apache-2.0 / MIT |
| DBOS (pattern) | Java, proxies; pattern is trivial to re-implement | n/a | n/a | Postgres step checkpoints | — | n/a | MIT |

## 3. Architecture options

**(a) In-process Scala engine.** A pipeline engine in `server` runs ordinary functions as steps. Some
steps call an LLM through sttp-ai, and each tool the LLM may use is a thin `AgentTool` around a
`Handlers` method.

- Pros:
  - One binary and one language.
  - The tools are typed functions with no Markdown round-trip, and they can be allowlisted per step.
  - Every write takes the existing code path, including `ifUpdatedAt`, `article_versions` and the
    restore lock.
  - Tests use munit with `ScriptedAgent`.
  - It fits the native bundle (no new process to supervise).
- Cons:
  - We write the durability and step tracing ourselves (§4).
  - Native-image metadata has to cover the new HTTP/JSON paths. The tracing agent already does this
    on forked `run`.
  - Skills written for Claude Code have to be split into code plus a prompt (§3.1). That is the point
    of the exercise, but it is real work.

**(b) Sidecar agent SDK.** A Python or TypeScript process, supervised the way `Supervisor` already
runs the converter, runs the Claude Agent SDK (or pydantic-ai or OpenAI Agents) and talks to
Histmersion over MCP/REST.

- Pros:
  - Today's skills and agents run nearly unchanged.
  - Fastest route to "the session runs headless".
- Cons:
  - Orchestration stays inside a model's context. That is the opposite of "deterministic where
    possible".
  - A second set of state (SDK sessions) sits outside Postgres.
  - It needs Node/Python plus the Claude Code binary in the bundle.
  - With the Claude Agent SDK it is Anthropic-only. Local and OpenAI models would need a different SDK.
  - Tool calls cross HTTP and come back as Markdown, so typed outputs have to be re-parsed.
  - The sidecar needs a write token and the full tool set, which widens the damage a prompt injection
    could do.

**(c) Workflow engine with LLM activities** (Temporal, Prefect, Inngest).

- Pros:
  - The best durability, retries and history UI.
- Cons:
  - It adds a whole service and its database to a single-user local bundle.
  - It needs native-image metadata for the Temporal Java SDK, or a Python worker.
  - The jobs UI would be duplicated.
- The durability we need is modest: one user, at most about 4 concurrent agents, steps that take
  minutes. Postgres step checkpoints (the DBOS pattern) give 90% of the value for about 5% of the cost.

**Security** (applies to all three; (a) makes it easiest to enforce):

- **API keys** come only from env or the config file (`${?ANTHROPIC_API_KEY}`…). They never go into
  the database, `/api/about`, logs or traces. Every request/response dump is redacted.
- **Write permissions** are granted per step. Each LLM step gets an explicit tool allowlist:
  - an extraction step gets *no* write tools and returns typed JSON, which code validates and writes;
  - the fixer gets `patch_article` for its own slug only, plus read tools;
  - no LLM step gets `query` against the admin role, `backup`/`restore`, `put_source` or
    `put_article` (destructive replaces), `rename`, or acquisition and network tools, unless the
    pipeline definition says so.
- **Prompt injection from ingested PDFs.** An OCR body is untrusted input; a scanned book can contain
  "ignore previous instructions". Mitigations:
  1. The body is passed only as delimited *data* in a user turn, never in the system prompt.
  2. Steps that read bodies have read-only tools or none.
  3. Every effect is a typed output that code validates: quotes must be verbatim, citekeys must exist,
     and vocabularies come from enums.
  4. Writes that are high-impact or hard to undo go through an approval gate: a status change to
     `retracted`/`debunked`, an edge of kind `refutes`/`superseded_by`, or an article publish when
     configured.
  5. Every write is recorded with the run id, so a run can be audited and its changes undone from
     `article_versions`.
- **The CLI-only Calibre step** becomes a deterministic "leave a gap" step. The pipeline records that
  the original is not in Calibre yet. The existing `histmersion calibre gaps` hook and
  `calibre sync --apply` (host-side, GUI closed) handle it. The UI shows it as a pending host action.

**Recommendation: (a)**, built on sttp-ai, with a Postgres-checkpointed step engine written in
Histmersion. Keep **(b) as an optional step backend later**: a `claude-code` step kind that runs
`claude -p --output-format json` (the [headless mode](https://code.claude.com/docs/en/headless)) or an
Agent SDK sidecar, for open-ended exploration such as *trailhead*, where a fixed pipeline is not
realistic. That is Orca's mechanism, used only where it fits.

### 3.1 Skills become pipeline definitions plus prompts

Each `skills/<name>/SKILL.md` becomes:

1. a **pipeline** in Scala (steps, data passing, gates, retries, queue updates);
2. **prompt files**, one per LLM step, in `skills/<name>/prompts/<step>.md`, with the step's JSON
   output schema derived from a case class;
3. the remaining SKILL.md, which shrinks to "to do X, run `histmersion agent run X …`", plus the
   manual fallback for external sessions.

The four `article-*` agent definitions move from the research workspace into
`histmersion/agents/`. The Claude Code plugin ships them, and the embedded runner reads the same
files, so there is **one copy of each prompt**. Each step records the hash of the prompt file it used.
Prompts are bundled into the native image as resources, with a config override directory for
iterating without a rebuild.

## 4. Workflow modelling

### 4.1 Engine

```scala
// sketch, not a final API
enum StepKind { case Code, Llm, Gate, Approval, HostAction }
trait Step[I, O]:
  def name: String
  def kind: StepKind
  def run(in: I)(using RunCtx): Either[StepError, O]      // O has a circe Codec → checkpointed
final case class Pipeline[I, O](name: String, version: Int, steps: Plan[I, O])
```

- **Checkpoints.** Before a step runs, the engine looks up `(run_id, step_seq, attempt)`. If an output
  is recorded, it replays that output instead of running the step. LLM outputs are therefore never paid
  for twice on resume, and a resumed run is deterministic up to the first step that had not finished.
- **Parallel steps.** These are Ox `par` forks inside the run's scope (the three reviewers).
  Cancellation is Ox interruption plus a `cancelled` status.
- **LLM steps.** Each one is an sttp-ai `agent.run` with:
  - `maxIterations`, and `maxTokens` from the step definition;
  - a `BudgetInterceptor` fed by the *run's* remaining budget;
  - a tracing interceptor that writes `agent_messages` and `agent_tool_calls` rows and pushes events
    to the run's SSE stream;
  - a typed result via `deriveResponseSchema[T]`.
- **Retries.** When a typed result fails code validation, the step is retried at most *n* times with
  the validator's error list appended to the prompt. After that the step fails, or opens an approval.

### 4.2 `enrich-source <citekey>`

| # | Step | Kind | What it does / what is verified in code |
|---|---|---|---|
| 1 | load | code | `get_source` with sections and TOC. If the body is large, pick chunks by heading (TOC offsets) instead of sending 450 KB |
| 2 | metadata | **LLM** (no tools) | Output `{title, authors[], year, venue, doi?, language}` |
| 3 | validate-metadata | code | Compare with OpenAlex/Crossref (already in `acquire`). Check `year-citekey-mismatch`; if it fails, propose a citekey and, if approved, call `rename` (code calls it; the LLM never does) |
| 4 | classify | **LLM** (read tools: `facets`, `search` brief) | Output `{summary, topics[], regions[], tags[], period{start,end,unit}, status_proposal, status_evidence}` |
| 5 | validate-classify | code | `topics`/`tags` must be in the facet vocabulary or flagged as new (`non-canonical-tag`). `status` uses the enum. Period: `start ≤ end`, `unit` converted in code (§4.4). Regions are resolved against the gazetteer |
| 6 | findings | **LLM**, one call per chunk group, in parallel ≤ k | Each finding is `{claim, quote, chunk_id, place_name, period, period_name, tags, confidence}`. **The quote is mandatory** |
| 7 | verify-findings | code | **Quote verification**: normalise whitespace, hyphenation, ligatures and Unicode quotes, then check the quote is a substring of the cited chunk (OCR-tolerant fuzzy match with a configured threshold, and the ratio recorded). Geocode `place_name` through `places`; LLM coordinates are only a flagged fallback. Drop findings that duplicate existing ones (same quote span) |
| 8 | notes | **LLM** (read tools) | A short `notes` body following `writing-guide.md`; `[[citekey]]` links |
| 9 | validate-notes | code | Every `[[x]]` exists as a citekey or slug; no escaped links; `article-filler` rule over the notes |
| 10 | patch | code | One `patch_source` with `ifUpdatedAt` from step 1. On 412: re-load, re-run only steps 5/7/9 (no LLM calls), re-patch |
| 11 | refs / assess | code | `refs --apply`, `assess --apply` (existing handlers). If `assess` returns `retracted`, the status is overwritten and the run is flagged |
| 12 | status gate | approval (configurable) | Any status proposal other than `current`/`foundational`, or one that needs a typed edge (`superseded-without-edge`), waits for a person |
| 13 | events | **LLM** + code | Optional. Uses the `ingest-paper` §3.5 events rules; dedup by label and place in code |
| 14 | host action | host-action | Record "not in Calibre" (picked up by `calibre gaps` / `sync`) |
| 15 | close | code | Tick the queue item (`jobs.acquire_item` / task id), write the `log` entry, run `lint --rule no-findings,no-period,unassessed-status` for this citekey. Any hit is a run warning |

### 4.3 `review-article <slug> [--fix] [--rounds N]`

| # | Step | Kind | Notes |
|---|---|---|---|
| 1 | snapshot | code | Article body plus `updated_at`, kept by the engine (no file export, no `updated_at` string shuffling) |
| 2 | mechanical | code | `article-filler`, `article-uncited-paragraph`, escaped `\[\[`, a hand-written `Sources cited` heading, the section set for the kind, glossary present when > 5 glossed terms (heuristic), dangling links. These become `M<n>` findings |
| 3 | panel | **3 LLM steps in parallel** | `rules` (read tools: `get_source`, `neighbors`, `search`, `query`), `cold-reader` (**no tools**, article text only, smaller model by config), `skeptic` (read tools including `followups`). Each returns the JSON it already specifies, now as a schema-validated case class |
| 4 | merge | code | Worst verdict wins. Dedup by `where`+`problem`, tag by reviewer, keep C/S pairs on the same passage. Findings the fixer rejected and a reviewer raises again in a later round are **escalated** (an approval item), as the skill specifies |
| 5 | gate | code | No major findings → PASS, go to 8. Rounds exhausted → go to 8 with remaining majors |
| 6 | fixer | **LLM** (read tools + `patch_article` limited to this slug, `ifUpdatedAt` injected by code) | Its output JSON (`applied`, `rejected[{evidence}]`…) is validated: every rejection must carry a quote, and code checks that the quote appears in the cited source body (the skill's "a rejection you cannot quote is not a rejection" rule, now enforced) |
| 7 | re-lint | code | Same checks as step 2. Then loop to step 3 |
| 8 | record | code | Diff of the round's `article_versions`. Queue log entry `review-article <slug>: round N → …`. A `tasks` item for each remaining MAJOR. Set `goals` status where linked |

The orchestrator's "apply contained majors directly" shortcut is **not** carried over. An approval item
with the reviewer's quote and proposed fix replaces it, and a person accepts it with one click.

### 4.4 Code vs LLM: a summary

- **Code:** data passing; `ifUpdatedAt` handling; vocabulary enums (status, confidence, article kind,
  edge kind); citekey and slug existence; verbatim-quote checks; period sanity (`start ≤ end`,
  `|year| ≤` a configured bound, inside the source's own period); **BP → BCE conversion**; geocoding;
  dedup; merging reviewer outputs; round counting and gating; lint; queue close and log; Calibre gap;
  diff; budget accounting.
- **BP → BCE conversion detail.** The model returns `{value, unit: BCE|CE|BP|calBP|BC_unspecified}`
  and never a converted number. Histmersion uses historical reckoning with no year 0 (`V015`), so
  `calBP` converts as astronomical year `1950 − BP`, mapped to historical. Uncalibrated `BP` (raw
  radiocarbon years) is *not* converted. It is stored as a flagged, low-confidence finding, or sent to
  an approval.
- **LLM:** reading and summarising; extracting claims and their quotes; classifying against a supplied
  vocabulary; judging status and edge evidence; reviewing prose (rules, comprehension, specialist
  refutation); rewriting. Every LLM output is a **JSON-schema-typed case class**. Free text appears only
  inside fields such as `summary`, `notes` and `body`, and those are linted.

### 4.5 Other pipelines

- **compile/extend article:** five phases. Extraction gives a claim table: an LLM step whose output
  claims each carry `citekey` and `quote`, verified in code. Then evidence model → draft → self-check
  (code lint) → `review-article --fix` as a sub-run.
- **currency check (per topic):** code runs `assess`/`followups` over the topic. An LLM classifies
  each new citing paper as confirms/challenges/refutes/supersedes, citing a quote. Edges and status
  changes go through the approval gate. Acquisition candidates go to the `acquire` queue.
- **acquisition sweep:** code runs `discover`/`wikirefs`. An LLM ranks relevance against a goal.
  Ingest is automatic for open-access items and goes to the `acquire` queue for the rest.

## 5. Jobs, tracing and UI

### 5.1 Schema (Flyway `V029+`)

```sql
-- agent runs ride on `jobs` (kind = 'agent', so `histmersion jobs` and /api/jobs see them) plus detail tables
ALTER TABLE jobs ADD COLUMN pipeline text, ADD COLUMN pipeline_version int,
  ADD COLUMN target_kind text, ADD COLUMN target_id text, ADD COLUMN goal_slug text,
  ADD COLUMN parent_job text, ADD COLUMN started_by text,            -- cli|ui|mcp|scheduler
  ADD COLUMN params jsonb, ADD COLUMN budget jsonb,                  -- {maxTokens, maxCostUsd}
  ADD COLUMN tokens_in bigint DEFAULT 0, ADD COLUMN tokens_out bigint DEFAULT 0,
  ADD COLUMN tokens_cache_read bigint DEFAULT 0, ADD COLUMN tokens_cache_write bigint DEFAULT 0,
  ADD COLUMN cost_usd numeric(12,6) DEFAULT 0;
-- status gains: waiting_approval | paused | cancelled  (status column is unconstrained text today)

CREATE TABLE agent_steps (job_id text REFERENCES jobs ON DELETE CASCADE, seq int, name text, kind text,
  attempt int, status text, input jsonb, output jsonb,              -- output = the checkpoint
  prompt_hash text, model text, tokens_in bigint, tokens_out bigint, cost_usd numeric(12,6),
  error text, started_at timestamptz, finished_at timestamptz, PRIMARY KEY (job_id, seq, attempt));
CREATE TABLE agent_messages (job_id text, seq int, n int, role text, content jsonb,   -- trace, prunable
  tool_name text, tool_input jsonb, tool_result text, latency_ms int, created_at timestamptz DEFAULT now(),
  PRIMARY KEY (job_id, seq, n));
CREATE TABLE agent_artifacts (job_id text, seq int, kind text,      -- diff|findings|review|report
  ref text, body text, created_at timestamptz DEFAULT now());       -- ref: article_versions.id, citekey…
CREATE TABLE approvals (id serial PRIMARY KEY, job_id text, seq int, kind text, title text,
  payload jsonb, status text DEFAULT 'open', decided_by text, decided_at timestamptz, note text);
CREATE TABLE work_leases (target_kind text, target_id text, job_id text, holder text,
  expires_at timestamptz, PRIMARY KEY (target_kind, target_id));   -- see §6
ALTER TABLE article_versions ADD COLUMN job_id text;            -- which run wrote the NEXT body
ALTER TABLE goals ADD COLUMN plan jsonb, ADD COLUMN schedule text, ADD COLUMN budget jsonb;
CREATE TABLE goal_links (goal_slug text REFERENCES goals ON DELETE CASCADE, kind text, ref text,
  PRIMARY KEY (goal_slug, kind, ref));                               -- article|source|query
```

Writes made by an agent carry the job id (`article_versions.job_id`, and a `job_id` on
source-patch audit rows if one is added), so each run can be audited and undone.

### 5.2 Budgets, concurrency, control

- **Concurrency.**
  - `agents.max-concurrent-runs = 4` is the user's limit on agents running at once.
  - A separate `agents.max-llm-calls-in-flight` also counts parallel reviewer forks, so one review
    panel (3 calls) plus one enrich run cannot exceed the cap.
  - The OCR pool stays separate.
- **Budgets** are checked at three levels:
  - per step: `maxTokens` and iteration cap;
  - per run: a `BudgetInterceptor` over the run's remainder;
  - global: daily and monthly cost caps checked before each LLM call, using a prices table in config
    keyed by provider model id. sttp-ai ships no prices. A model missing from the table counts as
    *infinite* cost, so it is refused. That is the opposite of sttp-ai's default, where an unpriced
    model counts as zero.
- **Prompt caching** for Anthropic: shared prefixes such as the writing guide and the reviewer system
  prompts are marked `CacheControl.Ephemeral`, and cache read/write tokens are recorded separately.
- **Breaching a budget** pauses the run (status `paused`, reason `budget`) instead of failing it. A
  person can raise the budget and resume.
- **Pause, resume, cancel:**
  - pause stops before the next step;
  - resume replays checkpoints;
  - cancel interrupts the Ox scope, marks the run `cancelled` and releases its leases.
  - On server restart, `running` agent jobs become `paused: interrupted`, not `failed`, because they
    can resume. OCR jobs keep today's behaviour.
- **Approvals.** An `Approval` step writes an `approvals` row, sets the run to `waiting_approval` and
  releases its concurrency slot. Approving or rejecting re-enqueues the run. The approval's payload is
  rendered as a diff or a proposed patch.
- **Notifications.**
  - SSE at `/api/events` drives the web UI's badge and the run page.
  - An optional `agents.notify-command` runs a host command (for example `osascript` or
    `terminal-notifier`) when an approval opens or a run fails.
  - A daily usage line goes into the `log` queue.

### 5.3 Surfaces (one handler, three views, as for everything else)

- **CLI:**
  - `histmersion agent run <pipeline> <target> [--goal g] [--model role=provider:model] [--budget-usd 2] [--rounds 3] [--approve auto|manual] [--follow]`
  - `histmersion runs [--status …] [<id> [--steps|--messages|--diff|--follow]]`
  - `histmersion runs pause|resume|cancel <id>`
  - `histmersion approvals [list|approve|reject <id> [--note]]`
  - `histmersion agent models` (configured providers and a connectivity check)
  - `histmersion agent usage [--since]`
  - `histmersion goals add|update|plan|schedule`
- **REST:**
  - `POST /api/runs`, `GET /api/runs[/{id}[/steps|/messages|/artifacts]]`
  - `POST /api/runs/{id}/{pause|resume|cancel}`
  - `GET|POST /api/approvals[/{id}/decision]`
  - `GET /api/events` (SSE)
  - `GET /api/agents/usage`
- **MCP:** `start_run`, `run_status`, `list_approvals`, `decide_approval`. An *external* Claude Code
  session can then hand a whole review panel to the server with one `start_run review-article` call
  instead of spawning four subagents. That is cheaper for the session's own context.
- **Web UI** (Laminar):
  - a **Runs** page: live list with status, pipeline, target, tokens, cost and elapsed time;
  - a **run detail** page: a step timeline with per-step tokens and cost, expandable messages and tool
    calls (bodies truncated), artifacts with an article diff viewer (from `article_versions`), and
    approve/reject buttons inline;
  - **Goals**: a tree of goals with linked articles and sources, a "plan" button and a schedule;
  - a "Run…" menu on source and article pages ("Enrich", "Review & fix", "Currency check").
- **Config keys** (HOCON, each with an env override):

```hocon
histmersion.agents {
  enabled = false                                  # off by default; the external workflow is untouched
  prompts-dir = ""                                 # override bundled prompts (iteration without rebuild)
  providers {
    anthropic { kind = anthropic, api-key = ${?ANTHROPIC_API_KEY} }
    openai    { kind = openai,    api-key = ${?OPENAI_API_KEY} }
    local     { kind = openai-compatible, base-url = "http://127.0.0.1:11434/v1", api-key = "" }
  }
  roles {                                          # step role → provider:model, overridable per run
    extractor = "anthropic:<model-id>", reviewer = "…", cold-reader = "<smaller model>",
    skeptic = "…", fixer = "…"
  }
  prices { "<model-id>" { input-per-mtok = 0, output-per-mtok = 0, cache-read-per-mtok = 0, cache-write-per-mtok = 0 } }
  budgets { run-default-usd = 2.0, daily-usd = 10.0, monthly-usd = 150.0 }   # placeholders, not advice
  max-concurrent-runs = 4
  max-llm-calls-in-flight = 4
  approvals { status-change = manual, edge-refutes = manual, article-publish = auto }
  trace { keep-messages-days = 30, otel-endpoint = "" }                      # optional OTLP → Langfuse
}
```

Model ids and prices are not filled in here on purpose. They come from config (plan §1.9: no
hardcoded constants).

## 6. Coexistence with the external-agent workflow

Both workflows must keep working, and they can, because both write through the same `Handlers`:

- `agents.enabled = false` by default. Nothing changes for Claude Code sessions.
- **Leases.** A run takes a `work_leases` row for its target (`article:<slug>`, `source:<ck>`).
  - `get_article`/`get_source` show "being worked on by run R since T", so an external agent sees it.
  - Writes from outside the run are *not* blocked. `ifUpdatedAt` remains the real guard: a 412 inside
    a run triggers a deterministic re-load and re-validate step, never a blind overwrite.
- **One set of prompts.** Skills and agent prompts live in the Histmersion repo. The plugin installs
  them for Claude Code and the server bundles them. The research workspace's
  `.claude/agents/article-*.md` become plugin-provided.
- **Skills learn the new verbs.** For example, `review-article` says: "if `histmersion agent models`
  answers, prefer `histmersion agent run review-article <slug> --fix --follow`; otherwise, the manual
  panel below". The `article-gate` hook can suggest the same thing.
- **Same bookkeeping.** Runs write the same `log` and `tasks` entries, so the meta exports read the
  same whichever workflow did the work.

## 7. Phases and effort

The estimates are rough. They assume one implementer familiar with the codebase, with tests
included.

| Phase | Scope | Effort |
|---|---|---|
| **P0 spike** | Add sttp-ai `core`+`claude`+`openai`. Build the server natively with one `ClaudeAgent.synchronous` call and one tool over a `Handlers` method; regenerate native metadata. Confirm a local OpenAI-compatible endpoint (Ollama) works. Measure the binary-size delta | 2–3 days |
| **P1** | Provider config, prices and budgets. The step engine with Postgres checkpoints and resume. Schema `jobs`+`agent_steps`+`agent_messages`. Tool adapters with per-step allowlists. **The `enrich-source` pipeline** with quote verification, period/BP conversion, vocabulary checks and the patch/412 loop. CLI `agent run`/`runs`. A minimal web Runs list | 2–3 weeks |
| **P2** | **`review-article` pipeline** (panel in parallel, merge, fixer, rounds, escalation). Approvals table and UI. Diff viewer. SSE events. MCP `start_run`/`run_status`. Prompts moved into `histmersion/agents/`. Skills updated to prefer embedded runs | 2–3 weeks |
| **P3** | Goals: `plan` (an LLM proposes sub-goals, queries and candidate DOIs as typed output; a person approves), `goal_links`, a scheduler (currency check and acquisition sweep per goal on a cron), daily/monthly caps, notifications, compile/extend-article pipeline | 3–4 weeks |
| **P4 (optional)** | `claude-code` step backend (headless CLI or Agent SDK sidecar) for open-ended *trailhead* exploration. OTel export to Langfuse | 1–2 weeks |

## 8. Risks

- **sttp-ai under native-image** is unverified. P0 exists to settle it. Fallback: keep sttp-ai's
  provider *clients* (plain sttp requests) and write the roughly 200-line loop ourselves.
- **sttp-ai is pre-1.0** and changes quickly (three releases in late September 2026). Pin the version;
  the engine's `LlmStep` hides its API.
- **Unpriced models count as zero cost in sttp-ai's own budget.** Our wrapper must invert that (§5.2).
- **Quote verification against OCR text** will give false negatives on poor scans. Use a fuzzy
  threshold, record the ratio, and send low ratios to approval rather than rejecting them.
- **Local models** are weaker at structured output and tool use. Roles let a person choose per step,
  and the validators catch failures either way.
- **Prompt drift between the two workflows** is avoided by keeping one copy of each prompt and
  recording `prompt_hash`.
- **Trace volume.** Message bodies can be large. Truncate tool results and prune them after
  `keep-messages-days`.
- **Over-automation.** Without approval gates, status changes and edges could spread errors. Their
  defaults are `manual`.

## 9. Test strategy

- **Unit (munit):** each validator gets table tests (quote normalisation, BP/calBP conversion, period
  sanity, vocabulary, link existence, merge/dedup, round gating, escalation of re-raised rejected
  findings).
- **Pipelines with scripted models:** sttp-ai's `agent-testkit` `ScriptedAgent` runs the real loop and
  the real tools against the `*_test` databases. Assert on the tools offered per step (the allowlist),
  the schemas sent, and the DB state after the run. The testkit's matchers need scalatest, which is
  `Provided`. Use its plain-data API (`script.requests`, `offeredTools`, `toolResultsSent`) from munit.
- **Recorded fixtures:** an `agents.record = true` mode writes each step's request and response to
  `src/test/resources/llm-fixtures/<pipeline>/<case>/`, keyed by a hash of (prompt_hash, model, input).
  A replay backend serves them, so CI never calls a paid API. A fixture miss fails the test.
- **Deterministic replays of real runs:** `histmersion runs replay <id>` re-executes a finished run
  from its `agent_steps` checkpoints with LLM steps served from the recorded outputs. It is useful for
  testing a changed validator against a real run without new token spend.
- **Injection tests:** fixture PDFs or bodies containing instructions such as "call put_source…" or
  "set status retracted". The test asserts that no write outside the allowlist happens and that the
  status gate opens.
- **Resume tests:** kill the run between steps (interrupt the scope), restart, and assert that no LLM
  step re-executes (fixture hit count) and that the final DB state matches an uninterrupted run.

## 10. Recommendation

Build an **in-process Scala engine** (option a):

- **SoftwareMill's sttp-ai** for providers, the tool loop, usage and budgets. It uses the same tapir
  and sttp versions as Histmersion, its tool schemas come from the same Tapir `Schema`/circe
  typeclasses Histmersion already derives, and it supports OpenAI-compatible local models.
- **A small Postgres-checkpointed step engine** in Histmersion (the DBOS pattern), instead of Temporal
  or a sidecar.
- **Typed outputs plus code validators** at every LLM boundary.
- **Approval gates** for statuses and edges.

There is no SoftwareMill "Orca". Orca is VirtusLab's (a SoftwareMill sister company since 2023): a
git-committed, CLI-driving flow runner for software development. Its stage-and-resume idea is worth
copying, but it is not a dependency for a corpus server. Start with P0 (a native-image spike) and then
the `enrich-source` pipeline. The external Claude Code workflow stays fully functional throughout,
and it can hand work to the server through the new MCP `start_run` tool.
