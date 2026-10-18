# Embedded research agents: design study

**Status:** research and proposal (2026-10-03). P0 is done (a **go**, §11), P1 is built (§12), so
are P2 (§13) and P3 (§14); §12–§14 list every deviation from the sketches below. It sits alongside
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

## 11. P0 result (2026-10-03): go

**Verdict: go.** sttp-ai works in the native image with no hand-written metadata, both provider paths
run the full tool loop with a typed answer, and the binary grows by about 10%.

- **Dependencies.** `com.softwaremill.sttp.ai` 0.11.4: `core_3`, `claude_3`, `openai_3`, plus
  `agent-testkit_3` for tests only. tapir (1.13.31) and sttp-client4 (4.0.26) stayed the same. circe is
  evicted from 0.14.10 to 0.14.16, and `tapir-apispec-docs`/`apispec` 0.11.10 come in. The JVM logs an
  SLF4J "no providers" warning. It was already there before this change (the live server log has it 29
  times), so it is not a regression.
- **The spike.** `POST /api/agents/probe` (`histmersion agent probe <citekey> [--model provider:model]`)
  makes one agent call. It offers exactly one read-only tool (`get_source`, a typed `AgentTool` over
  `Handlers.sourceMarkdown`) and asks for a typed answer (`ProbeAnswer{citekey, title, year}`, which
  becomes a JSON-schema `response_format`/`output_config`). It then checks the answer against the record.
- **JVM.** The call went through both providers to a local stub that speaks the OpenAI
  chat-completions format and the Anthropic Messages format. The stub has no model; it answers from the
  request's response schema. Each provider made a `get_source` tool call, got the tool result back, and
  returned a structured final answer, with usage and cost recorded. There are no Anthropic or OpenAI keys.
  The Claude path is covered by that wire-format stub and by sttp-ai's `ScriptedAgent` in munit.
- **Native.** The server was built with GraalVM CE 25.0.1 and Sloth. Its reachability metadata comes
  from the tracing agent on a forked `server/run`, with an empty database at the end so that V035 and the
  prompt resources were recorded. The native server ran the same probe through both providers. It also
  ran whole `enrich-source` runs (§12) against a throwaway Postgres on its own port, driven by the native
  CLI (`agent run … --follow`, `runs`, `show`, `resume --budget-usd`, `cancel`) and by the MCP
  `agent_run`/`agent_runs` tools. The metadata changes are lazy-val field entries for the new derived
  givens, the three prompt files and V035. Nothing had to be written by hand.
- **Binary size** (`histmersion-server`, built the same way from master `e2437cd`):

  | build | bytes | delta |
  |---|---|---|
  | master | 125,824,144 | — |
  | P0 (sttp-ai + probe) | 138,886,448 | +13.1 MB (+10.4%) |
  | P0 + P1 (engine, pipeline, routes) | 140,573,168 | +14.7 MB (+11.7%) |

- **Live local model: not shown.** The Ollama daemon on `:11434` lists its models, but every chat
  or generate call fails with `error starting llama-server: llama-server binary not found`. Its Homebrew
  install was removed while the process was still running. The OpenAI-compatible request still made
  the round trip: the provider's error came back parsed and was reported as a clean API error (and an
  engine run pauses with reason `provider`). Once Ollama is reinstalled, re-run the spike with
  `histmersion agent probe <citekey> --model local:qwen2.5-coder:32b` (price it first:
  `HISTMERSION_AGENTS_PRICES="qwen2.5-coder:32b=0/0"`).
- **What the spike taught.**
  1. `AgentTool.fromFunction` takes the MCP tools' own Kindlings `Schema` and codec unchanged, so the
     tool schemas the model sees are the ones MCP advertises.
  2. sttp-ai's loop answers `Tool not found` for any tool the step was not offered, which enforces
     the per-step allowlist by construction.
  3. Claude structured output checks the model id only against sttp-ai's list of known models. Custom
     ids pass.
  4. OpenAI's strict tools rewrite schemas for strict mode, so `local` defaults to `strict-tools = false`.

## 12. P1 as built (2026-10-03), and where it differs from the sketches above

**Built**

- **Config** (`agents { … }` in `reference.conf`, see the README table). `enabled = false` by default:
  every agent operation except `GET /api/agents/models` refuses, and no run pool starts.
  - Providers: `anthropic`, `openai` and `local`, each with a base URL, a key from the environment, a
    `strict-tools` flag and a timeout.
  - `roles.<role> = "provider:model"`.
  - `prices` is a string, `"<model>=<in>/<out>[/<cache-read>/<cache-write>]; …"`, because an env
    override can only be a string. **An unpriced model is refused** at start and at probe time.
  - `budgets` (run USD, run tokens, daily, monthly), `max-concurrent-runs = 4`,
    `max-llm-calls-in-flight = 4`, and `step`, `verify`, `trace` and `enrich` limits.
- **Schema V035.**
  - `jobs` gains `pipeline`, `pipeline_version`, `target_kind`, `target_id`, `model`, `budget_usd`,
    `budget_tokens` and `warnings`.
  - `agent_steps` has one row per *attempt*. The row with status `done` is the checkpoint.
  - `agent_messages` holds the trace.
  - Token and cost totals are **not** columns on `jobs`. They are sums over the steps, so they are
    written in one place only.
- **Engine** (`server/agents/Engine.scala`).
  - A pipeline is straight-line Scala calling `ctx.code[O](name)` and `ctx.llm[A, O](name, role,
    prompt, tools, schema)(validate)`. `A` is the answer type the model must fill, and `O` is the
    *verified* value that is checkpointed.
  - Before step *n* runs, its finished attempt is looked up and replayed. A resumed run therefore
    never repeats a finished step and never pays for an LLM answer twice; the munit resume test proves
    this. If the recorded step has another name, or the pipeline version changed, the run is refused.
  - A rejected answer is retried up to `step.max-retries` times, with the validator's problems
    appended. On the last attempt the validator keeps whatever passed.
  - Concurrency: `max-concurrent-runs` workers in an Ox scope, plus a semaphore of
    `max-llm-calls-in-flight` held around every LLM call.
  - On restart, a `running` run becomes `paused: interrupted` and queued runs are re-enqueued. The OCR
    reconciliation (`JobRepo.failInterrupted`) no longer touches agent runs.
- **Budgets.**
  - Before every LLM call the engine checks the run's USD and token caps and the global daily and
    monthly caps (summed from `agent_steps`).
  - Inside the sttp-ai loop, a `decide` interceptor priced by **our** table finishes the loop when the
    run's cap is reached.
  - Breaching a cap **pauses** the run with reason `budget`. `agent resume <id> --budget-usd N`
    raises the cap and continues.
  - A provider failure pauses with reason `provider`, and is also resumable.
- **Verifiers** (pure, `core/AgentVerify.scala` and `core/EnrichSource.scala`, table-tested):
  - Quotes: NFKC, ligatures, Unicode quotes and dashes, de-hyphenation across line breaks, Markdown
    marks, whitespace. Verbatim first, then a word-level in-order match that tolerates one OCR slip
    per word of four letters or more, with the ratio recorded. Quotes under four words must be
    verbatim.
  - Status and confidence vocabularies are enums. A typo is rejected, never coerced to `unassessed`.
  - `[[link]]` targets must exist; escaped brackets are rejected.
  - Period sanity: start ≤ end, no year 0, the `max-abs-year` bound, and a warning when a finding falls
    outside the source's own span.
  - Years: BCE/CE/calBP are converted in code. calBP is astronomical `1950 − BP`, mapped to historical
    reckoning. Raw BP is refused.
- **`enrich-source <citekey>`**: load · classify · findings-1..n · geocode · notes · patch · close.
  - Tools per step: classify gets `facets` and `search`, findings and notes get **no** tools, and no
    step has a write tool.
  - The OCR'd body travels only inside `<source-text>` tags in the user turn.
  - Places are geocoded through the gazetteer only (`db.Geocoder`, `ST_PointOnSurface`). The
    findings schema has no coordinates at all; it has `place` and an optional `placeKind`
    (site | region | polity | water | unknown). Since #981 names compare by a folded key (diacritics,
    case, apostrophes, an aside such as "Ur(i)"), a label beats an alias, a broad entity beats a site
    unless the model said site, a dated snapshot is chosen by the finding's own period, Pleiades
    placeholder points rank last, and "X, Country" / "northern X" are read too — the last two (and a
    placeholder) marked approximate. The finding keeps the asked name as `region`; the `geocode`
    checkpoint records each choice (place id, label, why approximate) for audit.
    `lint --rule finding-geocode [--fix]` re-places points the older geocoder chose.
  - Findings guards (#1424, `agents.enrich.guards`, added when the extractor moved to a smaller model).
    Each sends the answer back while attempts remain, and keeps it with a warning on the last attempt:
    - too few places: fewer than `min-placed-share` (0.5) of the findings that name a place resolve in
      the gazetteer. The check asks the geocoder itself (the `geocode` step comes later), and lists
      the names that did not resolve. A finding with no place is a non-spatial claim and does not count.
    - a century stored as a year: a year below 100 when the claim, quote or period name mentions a
      century it is not inside ("11th century" → 11; "1st century" → 1–100 is right), or when the
      source's own period starts `century-gap-years` (500) later. A year the text states with its era
      ("79 CE", "AD 79") is valid.
    - a claim pointing at a table: one of `pointer-phrases` ("this date", "the next column", "as
      listed", …) as whole words in the claim.
  - The single patch carries `ifUpdatedAt`. On a 412 it re-reads the source and rebuilds the patch from
    the checkpointed answers, without calling the model again.
  - Bookkeeping: one `log` line per run, and a `tasks` item for any status a person must decide.
- **Surfaces.**
  - REST: `GET /api/agents/models`, `POST /api/agents/probe`, `POST|GET /api/agents/runs`,
    `GET /api/agents/runs/{id}`, `POST /api/agents/runs/{id}/{pause|resume|cancel}`.
  - CLI: `histmersion agent models|probe|run|runs|show|pause|resume|cancel`.
  - MCP: `agent_run`, `agent_runs`.
  - Agent runs also show up in `histmersion jobs` (kind `agent`).

**Deviations from §§4–5 (decided while building)**

1. **No approvals table yet (P2).** The §4.2 step 12 status gate is replaced by a rule:
   - the run may only move a source from `unassessed` to `current` or `foundational`;
   - any other proposal becomes a `tasks` item plus a run warning, and is never applied.

   Run states are `queued|running|paused|done|failed|cancelled`; there is no `waiting_approval`.
2. **The patch only fills; it never overwrites.**
   - It never overwrites an existing summary, notes or period.
   - Topics and regions are merged, and tags and findings are added.
   - A finding repeating an existing claim is skipped.
3. **Not yet in `enrich-source`:** events (step 13). The findings windows run one after another, not
   in parallel. (The metadata check, `refs`/`assess`, the per-citekey lint at close and ticking an
   acquisition item were added in P2, §13.)
4. **Fixed providers.** Providers are a fixed set (`anthropic`, `openai`, `local`) rather than
   arbitrary named blocks. `trace.otel-endpoint` is not implemented (`prompts-dir` and `approvals.*`
   came in P2).
5. **Prompts.** They are bundled server resources (`agents/prompts/enrich-source/*.md`), and each
   attempt records their sha256. They have not moved to a shared `histmersion/agents/` directory used
   by the plugin.
6. **Naming.** The CLI is `histmersion agent …` (one verb group) instead of `histmersion runs …`. The
   MCP tools were `agent_run`/`agent_runs` in P1; P2 renamed them to the design's `start_run`/`run_status`.
7. **New: the probe.** `agent probe` was added as the connectivity and tool-calling check that §5.3
   calls `agent models`; `agent models` itself makes no call.
8. **No web UI Runs page yet** (added in P2, §13).
9. **No `agents.record` fixture mode and no `runs replay`.** Scripted tests use sttp-ai's
   `ScriptedAgent` behind an `LlmClient` seam instead.

**Recommended next steps**

1. Reinstall Ollama, then run the live probe and one live `enrich-source` on a small source with
   `local:qwen2.5-coder:32b`. Record whether the local model follows the schemas; the validators will
   show where it does not.
2. Add a minimal web Runs list and run page reading `/api/agents/runs` (still open from P1).
3. P2: the approvals table, then `review-article` on the same engine. The mechanical checks are already
   lint rules, and the panel is three `ctx.llm` steps in parallel inside one Ox `par`.
4. Fold the missing `enrich-source` steps back in: metadata validation against OpenAlex, `refs`/`assess`,
   and lint at close.
5. Add a pruning job for `agent_messages` (`trace.keep-messages-days` is read but nothing prunes yet).

## 13. P2 as built (2026-10-04), and where it differs

**Finished from P1**

- **Web UI.** Two windows, offered in the toolbar only when `/api/config` says `agentsEnabled`
  (`agents.enabled`):
  - *agent runs* (`RunsView`): the runs with a status filter in the URL hash (`runs=paused`),
    pipeline, target (a source or article link), options, tokens and cost against the budget;
  - one window per run: the step table (try, status, model, tokens, USD, the validator's note),
    warnings, the trace per step (collapsed), and pause / resume (with an optional new budget) /
    cancel behind the write gate (`Auth.writable`, as every write control).
  - Live status is **polling**, not SSE: the same backoff as the jobs window (quick, then slower,
    stopped once nothing is queued or running or the window closes). SSE would need a second
    transport through netty-sync for one user and ≤ 4 runs; polling with backoff costs one small GET
    every 1–15 s while something moves.
- **`enrich-source` v2** (a paused v1 run is refused on resume: its steps no longer line up). Steps:
  load · **metadata** · classify · findings-1..n · geocode · notes · patch · **refs** · **assess** ·
  **status** · **close**.
  - `metadata` compares the record with OpenAlex/Crossref by DOI (title, year, first author, the
    citekey's own year — the `year-citekey-mismatch` rule). Differences are run warnings; nothing is
    renamed or rewritten (§4.2 step 3 proposed a citekey rename on approval — left to a person).
    There is no LLM metadata step (§4.2 step 2): the record already has what it would extract.
  - `refs --apply` and `assess --apply` through the acquisition handlers (an `AgentUpstream` seam,
    attached in `Main`). A retraction from `assess` overwrites the status, as `assess` always does,
    flags the run, and suppresses the model's status proposal.
  - A network failure in these steps is a warning, never a failed run. No DOI, or
    `agents.enrich.upstream = false`, records why they were skipped.
  - `status`: a status beyond filling `unassessed` with `current`/`foundational` goes through the
    approval gate (it was a `tasks` item in P1).
  - `close`: the per-source lint (`no-findings`, `no-period`, `unassessed-status` — warnings), the
    acquisition item the run was started for ticked off exactly as an OCR upload does
    (`--acquire-item`, validated when the run is started), and the log line.
- **Trace pruning.** `agent_messages` older than `trace.keep-messages-days` are deleted when the pool
  starts and then at most every `trace.prune-every-minutes`, after a run settles. Steps (checkpoints,
  tokens, cost) are never pruned.

**P2**

- **One copy of each review prompt.** `agents/article-{rules-reviewer,cold-reader,skeptic,fixer}.md`
  at the repo root are the four Claude Code agent definitions, ported unchanged from the research
  workspace. The plugin ships them (`plugin/agents → ../agents`) and the build copies them, with
  `docs/writing-guide.md`, into the server's resources as `agents/…`. The embedded steps read each
  agent's body (frontmatter removed) plus a short server-owned note
  (`prompts/review-article/{common,rules,cold-reader,skeptic,fixer}.md`: tools instead of shell
  commands, typed JSON instead of the Output block, no writes) plus, for the three that read the
  database, the guide. `agents.prompts-dir` overrides any file at the same relative path, read on
  every use, so a prompt can be iterated on without a rebuild; each attempt records the hash of the
  text it used.
- **`review-article <slug> [--fix] [--rounds N]`** (§4.3). Per round: `snapshot-r` · `mechanical-r` ·
  `rules-r`, `cold-reader-r`, `skeptic-r` **in parallel** · `merge-r` · `proposals-r` · (`fixer-r` ·
  `patch-r` while a MAJOR is open, fixing was asked for and rounds remain) · then `record`.
  - **Since 2026-10-09 (pipeline version 4) the passes after a fix are VERIFICATION passes**, around
    the article's review ledger (V073): `ledger` first, `ledger-r` before each `merge-r`, and after a
    fix only the reviewers owning a claimed finding re-check (`rules-check-r`, …), then a confirming
    full panel. The design and the data behind it: docs/research-review-convergence.md §6.
  - **Parallel steps** are an Ox `par` inside the run's thread: their step numbers are reserved first,
    in program order, so a resume lines them up with their checkpoints whichever finished first, and
    only the unfinished ones run again (tested). A halt of one wins over the others' results (cancel,
    then failure, then pause).
  - **Tools:** rules — `get_source`, `passages`, `neighbors`, `search`, `query` (the read-only role), `facets`,
    `article_coverage`;
    cold reader — **none**; skeptic — the same plus `followups` (never applied); fixer — the read tools
    and `check_edits` (a dry run of its edits, read only). No step has a write tool: the fixer returns
    a list of edits and code applies and writes them.
  - **Mechanical checks** (`M` findings, pure): banned filler, a paragraph with no `[[citekey]]` (the
    lint rule's own test), escaped brackets, a hand-written Sources cited heading, a missing
    Disagreements / Unresolved-or-Open-questions / Not used section (not for `index` articles), a
    duplicated heading, a `[[link]]` to nothing.
  - **Coverage (#958):** an article that records its filter (`articles.coverage_filter`, V066) gets
    its Not used list, the counts above it and the mechanical half of its `_Method_` note GENERATED
    at read time (`ArticleCoverage`, from the live corpus) — so for it a Not used section is not
    required, a hand-written one or a `_Method_` line is a MAJOR `M` finding, and the coverage DRIFT
    is checked by code: a source the filter returns that is neither cited nor given a reason (one MAJOR
    finding listing them), a stored reason for a source now cited or no longer returned (minor). The
    snapshot carries the coverage; every reviewer and the fixer read the generated sections after the
    article (`<generated-sections>`). The fixer answers drift with `reasons` `[{citekey, reason}]`
    (an empty one removes), validated against the coverage (a source of the filter the revised body
    does not cite; an applied drift finding leaves none unplaced) and written with the round's patch.
    An article with no filter keeps the old rules, and the run warns once.
  - **Validation of each reviewer:** code numbers the findings (`R1`, `C2`, `S3`); a severity outside
    major/minor or a finding without a problem is sent back; a verdict worse than the findings is a
    finding left out (sent back), a milder one is corrected. The cold reader's mechanical severity
    rules are enforced by code: an empty explain-back slot adds a MAJOR; more than 8 undefined terms
    adds a MINOR count finding ("N terms undefined (count only; the individual findings carry the
    severity)") — the individually named terms keep the reader's severity, and a MAJOR verdict the
    reader states for the count alone is corrected, not sent back.
  - **Merge:** mechanical, rules, cold reader, skeptic in that order; duplicates by normalised
    `where` + `problem` dropped; a cold-reader and a skeptic finding on the same passage both kept; the
    verdict is the worst of every report and finding. A finding the fixer **rejected** in an earlier
    round that the same reviewer raises again (same quoted passage, or same `where` + `problem`) is
    flagged **escalated**.
  - **Evidence:** a rules or skeptic finding carries up to three `{citekey, offset, quote}` records it
    rests on; code checks each quote against the record (the OCR-tolerant matcher), drops the ones it
    cannot find with a warning, the merge keeps them (a dropped duplicate's join the kept finding), and
    the fixer reads them under the finding (`evidence: [[ck]] @offset: "…"`), so it starts where the
    reviewer looked instead of searching again.
  - **Fixer answer = edits** (`{op: replace|insertAfter|insertBefore|delete, anchor, text}`), applied
    by code in order to the body the round reviewed; an anchor must occur exactly once (whitespace
    normalised), else the edit is a problem naming its number and anchor head. A replace with no
    text (`null` or empty, as a strict-schema provider sends a field it left out) is a problem, never
    a delete — a delete says `delete`. Rewriting the whole
    body cost 300–700k tokens an attempt and introduced new errors; a retry now carries the previous
    edits back. The step output records every edit and what became of it.
  - **One check, two callers:** `ReviewArticle.checkBody` is both the validation below and the
    `check_edits` tool (`POST /api/articles/{slug}/check`, `histmersion article check`), so the fixer
    can see the problems before it answers; the compile writer may lint its whole draft with it too.
  - **Fixer turns:** the fixer agent's `max-turns` plus `review.fixer-turns-per-finding` per major or
    factual (rules/skeptic) minor finding, at most `review.fixer-turns-cap` (an 18-finding round ran
    out of 12 turns).
  - **Fixer validation:** the result must link only to real citekeys or articles, carry no
    escaped brackets, no Sources cited heading, no uncited paragraph, no banned filler and no
    formatting error the edits introduced; every
    MAJOR must be applied or rejected; mechanical and cold-reader findings can only be applied
    (*define, never delete*); every rejection must name a citekey and quote its record (summary,
    findings, notes or body), and code looks for the quote with the OCR-tolerant matcher — "a
    rejection you cannot quote is not a rejection" is now enforced. On the last attempt the edits
    that apply are kept unless the result is fatal (empty, or a link problem the edits added — an edit
    whose text links to nothing is never applied), the rest are reported, and an unverifiable
    rejection is recorded as *unverified* (the finding stays open).
  - **Patch:** `patch_article` with the snapshot's `ifUpdatedAt`. A 412 (someone else wrote the
    article meanwhile) drops this fix with a warning and the next round reviews **their** text; it
    never overwrites (tested). The step records the `article_versions` id holding the previous text,
    so a run's edit can be undone from the version history.
  - **Rounds:** `--rounds` counts panel rounds (default `agents.review.max-rounds = 3`, at most
    `rounds-cap = 5`); the fixer works between them, so a fixing run gets at least two.
  - **Record:** the skill's log line (`review-article [[slug]]: round N → VERDICT (R:x C:y S:z M:w),
    fixer applied a / rejected b`) and one `tasks` item per MAJOR still open (an escalated one says
    "raised again after the fixer rejected it"). The orchestrator's "apply contained majors directly"
    shortcut is not carried over (§4.3).
  - An article longer than `review.article-chars` is reviewed cut and never revised (a fixer cannot
    edit a body it was not shown). The fixer gets `review.fixer-max-tokens` output tokens.
- **Approvals** (V041 `approvals`; `AgentProposals`, `Approvals`):
  - What a run may change without a person: filling `unassessed` with `current`/`foundational`
    (P1's rule). Every other status, and every disagreement edge (`challenges`, `refutes`, `updates`,
    `superseded_by`), follows `agents.approvals.status-change` / `.edges`: **`manual` by default**
    (an open approval) or `auto` (applied at once, recorded already approved). Anything but `auto`,
    a typo included, is manual.
  - Sources: `enrich-source`'s status proposal; the skeptic's `proposals` (its `db_issues`, typed:
    kind, citekey, status or edge kind + target, evidence, and a quote that code finds in the record of
    either end before anything is recorded).
  - The same change is open at most once (a partial unique index).
  - Deciding is a compare-and-set on `open` first, then the change is applied through
    `Handlers.patchSource` (the patch a person would send); a failure to apply leaves the approval
    `failed` with the reason. Deciding works with agents off.
  - **A run does not wait for its approvals** (no `waiting_approval` state): it finishes and the
    proposal waits. A blocking gate is only needed when a later step depends on the decision, and no
    step does yet.
  - Web UI: an *approvals* window (open ones, or all with the toggle), each with the change, the
    evidence, the quote, a note, and approve / reject behind the write gate; it reloads when a run it
    watches settles.
- **Surfaces.**
  - REST: `GET /api/agents/approvals[?state=…]`, `POST /api/agents/approvals/{id}/{approve|reject}`
    (body `{note}`); `POST /api/agents/runs` takes `fix`, `rounds`, `acquireItem`, each refused on the
    wrong pipeline.
  - CLI: `histmersion agent run … [--fix] [--rounds N] [--acquire-item ID]`,
    `histmersion agent approvals [--state …]`, `histmersion agent approve|reject <id> [--note …]`.
  - MCP: `start_run`, `run_status`, `list_approvals`, `decide_approval` (53 tools).
  - The `review-article` skill now says to prefer `histmersion agent run review-article <slug> --fix
    --follow` when the server's agents are on, and keeps the manual panel unchanged for when they are
    not.
- **Schema:** V040 `jobs.params jsonb` (a run's options, decoded with every field optional), V041
  `approvals`. Both are in the server's native-image metadata, with the prompt files
  (`NativeMetadataSuite` enforces migrations and every bundled prompt).

**Deviations decided while building P2**

1. **Polling, not SSE** (above). There is no `/api/events`.
2. **Approvals do not block a run** (above), and there is no `agent_artifacts` table: a run's article
   edit is undone from `article_versions` (the `patch-r` checkpoint names the version).
3. **Escalation goes to the backlog**, not to an approval item: an escalated or remaining MAJOR is a
   `tasks` line, as the skill records it. Approvals are kept for changes code would APPLY.
4. **The fixer has no `patch_article` tool** (§4.3 step 6 gave it one limited to its slug): it returns
   the body and code writes it — the same "no step has a write tool" rule as `enrich-source`.
5. **Budgets with parallel steps.** The run's cap is checked before every LLM call; the three panel
   calls start together, so a run can overshoot its cap by up to one call per parallel step (a
   sequential step overshoots by at most one call, as in P1). It then pauses before the next call.
6. **No live model was exercised** (no API keys, no local model server; a local provider is deferred,
   #825). Everything is tested with sttp-ai's scripted model, and the JVM server was driven end to end
   through the real OpenAI-compatible wire by a stub that answers per agent (§11's method).

**Recommended next steps**

1. With a key or a local model: one live `review-article --fix` on a short article; compare its
   findings with the Claude Code panel's on the same article.
2. A blocking approval step for the compile/extend-article pipeline (P3), where publishing waits.
3. Run the four agent definitions from the plugin in the research workspace and delete its own
   `.claude/agents/article-*.md`, so the two workflows read one copy.
4. SSE for the runs windows if polling ever shows up in a profile.

## 14. P3 as built (2026-10-04), and where it differs

**Schema** (V046–V049, all in the server's native-image metadata with the four new prompt files):

- V046: `goals.plan` (the plan a person approved, kept as the record of what was created from it),
  `goals.budget_usd` (the cap of the runs started for the goal), and `goal_links (goal_slug, kind,
  ref, note)` with kinds `source`, `article`, `query` (what an acquisition sweep searches for) and
  `queue-item`.
- V047: approvals a run WAITS on. `approvals.subject` (the goal or article slug), `approvals.seq`
  (the waiting step; one step, one approval — a resumed run finds the one it opened), `citekey`
  nullable (a plan or an article is not about one source).
- V048: `jobs.goal_slug`, `jobs.parent_job` (compile-article → its review run), `jobs.origin`
  (`person`, `scheduler`, `run`).
- V049: `goal_schedules (goal_slug, kind, cron, enabled, budget_usd, next_run_at, last_run_at,
  last_job, last_note)`, kinds `currency` and `acquisition`.

**The blocking approval step.** `RunCtx.approval(name, kind, subject, mode)(payload)` is the third
kind of step. The first time a run reaches it, the payload (a typed proposal as JSON) becomes an
approval of a blocking kind, and the run halts with `Halt.Wait`: its state becomes **`waiting`** (a
new `RunState`; `running → waiting → queued | cancelled`), and it holds no worker. Deciding the
approval (CLI, REST, MCP, web) only records the decision and wakes the run (`waiting → queued`); the
run replays its finished steps, reaches the approval step again, checkpoints the decision
(`Decided{approved, approval, note}`) and goes on, applying exactly the proposal that was approved. A
decision that lands between the approval being opened and the run's transition is caught (the engine
looks once more after the transition). Cancelling a waiting run rejects its open approval. In `auto`
mode the approval is recorded already approved and the run does not stop. The two blocking kinds are
`goal-plan` (always manual) and `article-publish` (`agents.approvals.article-publish`, `manual` by
default); P2's `status-change` and `edge` are applied by the decision itself, as before.

**`plan-goal <goal>`** — load · propose · verify · approve · apply.

- `propose` (role `planner`, tools `search`, `facets`, `get_source`): `PlanAnswer{rationale,
  subGoals[{slug, title, notes, tags}], queries[], works[{doi, why}]}`. Code checks it
  (`PlanGoal.check`): slugs valid and new, titles present, DOIs normalised, queries ≤
  `agents.plan.query-chars`, caps (`agents.plan.max-*`). What a retry can fix is sent back; what it
  cannot (a slug already a goal, a query the goal already has) is dropped with a note.
- `verify` (code): every DOI through the discover path (`AgentUpstream.meta`, i.e. OpenAlex/
  Crossref). A DOI that does not resolve, or a retracted work, is dropped and shown as dropped — a
  person never sees an unverified work. Works already in the corpus carry their citekey.
- `approve` (blocking, always manual): nothing exists yet. A plan with nothing left after the checks
  opens no approval; the run closes with a log line.
- `apply` (code, on approval): sub-goals created as children of the goal (inheriting its regions and
  period), query links, works in the corpus linked as sources, the others added to the acquisition
  queue as `Authors (year). Title. https://doi.org/… — for goal <slug>: why` and linked by item (a DOI
  already in the queue is linked, never added twice), the plan kept on the goal, one log line. A
  rejection changes nothing.

**`compile-article <slug>`** — snapshot · filter · extract-1..n · evidence · draft · approve · write
· review · close. A slug that exists is EXTENDED, a new one is CREATED (`--title`, `--kind`, default
`concept`; a filter — `--query`, `--topic`, `--tag` — is required for a new article, an existing one
defaults to its title as the query).

- `filter` (code): `search` with the filter, up to `agents.compile.max-sources`; when extending, only
  the sources the article does not cite yet are extracted (none: the run ends, no LLM call).
- `extract-i` (role `extractor`, **no tools**, the record as `<source-record>` data):
  `ExtractAnswer{relevant, reason, claims[{claim, quote, date, place, confidence}]}`. Every quote is
  looked for in the record with the OCR-tolerant matcher; confidence is the enum; an irrelevant source
  must say why (that reason is stored with the article and shown under its generated Not used list).
- `evidence` (code, from the database): statuses, the disagreement edges among the filter's sources
  (only citekey targets), claims per source (due weight), the Not-used list.
- `draft` (role `writer`, no tools; the server's draft prompt plus the writing guide):
  `DraftAnswer{title, summary, body, section, reasons}`. The deterministic self-check is the validator:
  fatal — an empty text, a link to nothing, escaped brackets, a Sources cited heading, an extension
  under a heading the article lacks; sent back, then kept and listed for the person — a paragraph with
  no `[[citekey]]`, a citation of a source with no claim in the table (a decorative citation; the
  Not-used section is named, not cited), the first citation of a non-current source not stating its
  status (from the database), a missing compulsory section (new articles), banned filler, a
  hand-written Not used section or `_Method_` note (#958: code generates both from the filter the run
  records), a reason for a source outside the claim table or one the draft cites, a claim-table
  source neither cited nor given a reason. The run's filter (its own, else the article's recorded
  one, else its title) and the reasons (the extraction's for irrelevant sources, the writer's for
  the others it leaves out) are written with the article.
- `approve` (blocking by default): the person sees the whole draft, the self-check's remaining
  problems, what it cites and leaves out.
- `write` (code): a new article with `put_article` only while the slug is still free (taken
  meanwhile: nothing written, a `tasks` item); an extension with `append` (+ `section`) and the
  snapshot's `ifUpdatedAt` — a 412 drops it with a `tasks` item, never an overwrite. The previous text
  is in `article_versions`, as for every write.
- `review` (code): a `review-article` run with fix, origin `run`, parent this run, same goal and model
  (`agents.compile.review`, default on). A refusal (no reviewer model) is a warning.
- `close`: the article linked to the run's goal, the log line.

**The scheduled pipelines.**

- **`currency-check <goal>`** (no model, no cost): the goal's sources (linked, and cited by its linked
  articles) with a DOI → `assess --apply` and `followups` (read only). A citing paper read as
  refuting, challenging or superseding becomes an EDGE PROPOSAL through the P2 approval gate when both
  ends are in the corpus (`supersedes` as `superseded_by` from the older to the newer; evidence = the
  citation context; once — a change ever proposed, or an edge already recorded, is not proposed
  again), or an acquisition item when it is not.
- **`acquisition-sweep <goal>`**: `discover` over the goal's query links (its title when it has none),
  dropping what the corpus or the acquisition queue (open or done, by DOI) already has; the
  `planner` ranks the rest against the goal (no tools; a pick that names an unlisted DOI is sent
  back); the picks are queued once and linked to the goal. Nothing is ingested: acquiring stays a
  person's call — unless a watcher opted in (phase 3 of the process managers: legal open access only,
  capped per day).
- **The scheduler** (`Scheduler`): a daemon thread, started with the pool, only when `agents.enabled`
  and `agents.scheduler.enabled`. Every `tick-seconds` each due, enabled schedule (`cron`: five fields
  in UTC or `@hourly/@daily/@weekly/@monthly`, a pure `Cron` parser) either **starts** its pipeline
  (origin `scheduler`, cap = the schedule's, else the goal's, else `run-usd`), is **skipped** to its
  next firing (its previous run has not settled; today's or this month's spend reached
  `budgets.daily-usd`/`monthly-usd` — checked BEFORE the start), or is **deferred** to the next tick
  (`max-concurrent-runs` runs queued or running). A schedule may not fire more often than
  `min-interval-minutes` (refused when set). Notifications: a skip or a refused start is a log line or
  a `tasks` item; a scheduled run that fails or pauses leaves a `tasks` item (the engine adds it; a
  person's run does not — they are watching it).
  **Superseded by the process managers' phase 3** (docs/research-process-managers.md, "As built (phase
  3)"): the `Scheduler` thread and `tick-seconds` are gone; a goal's schedules are the sweeps of its
  watcher manager `watch:<goal>`, whose cron the managers' tick fires; `goal_schedules` rows are moved
  into it (created paused) at start and on every tick. The surfaces above keep working over the watcher.

**Surfaces.**

- CLI: `histmersion goals get|plan|link|unlink|schedule|schedules` (and `update --budget-usd`);
  `histmersion agent run compile-article <slug> [--title] [--kind] [--query] [--topic]… [--tag]…
  [--goal]`, the goal pipelines, `agent runs --goal`, `--status waiting`; `--follow` returns on
  `waiting`.
- REST: `GET /api/goals/{slug}/view`, `POST|DELETE /api/goals/{slug}/links`,
  `PUT|DELETE /api/goals/{slug}/schedules/{kind}`, `GET /api/agents/schedules`,
  `GET /api/agents/runs?goal=`; `POST /api/agents/runs` takes `title`, `kind`, `query`, `topics`,
  `tags`, `goal`, each refused on the wrong pipeline.
- MCP: `start_run` takes the four new pipelines and their options; `run_status` filters by goal;
  `list_approvals`/`decide_approval` show and decide plans and drafts; new `goal_links` and
  `goal_schedule`; `goals` with a slug shows the whole goal view; `patch_goal` takes `budgetUsd`
  (56 tools).
- Web UI: a *research goals* window (the goals as a tree, always offered) and one window per goal —
  its plan waiting for approval with approve / reject in place, the "plan" button (when agents are on),
  sub-goals, links with unlink and an add form, the schedule editor (cron, on/off, cap; save /
  remove; next and last run, the last note), the approved plan, the goal's runs. The approvals window
  renders plans (sub-goals, queries, works in or out of the corpus, what code dropped) and drafts (the
  self-check, Not used, the text). The runs window shows `waiting` runs with the approval they wait
  for (cancel only).

**Deviations decided while building P3**

1. **`waiting`, not `waiting_approval`.** The run state's wire value is one word, like the others.
2. **Schedules are a table**, not `goals.schedule text`: a goal has two independent schedules, each
   with its own state (next, last run, last note).
3. **The currency check has no LLM step.** §4.5 had a model classify each citing paper with a quote;
   the citation graph's own classification (`Acquire.classify` over Semantic Scholar's contexts and
   intents) already does it, and every edge still goes through the approval gate with the context as
   its evidence. A scheduled check therefore costs nothing; the caps still apply before it starts.
4. **The acquisition sweep never ingests** (§4.5 ingested open-access items automatically): it only
   queues, so what enters the corpus stays a person's decision.
5. **The evidence model is code only.** §4.5 and the writing guide's phase 2 include AttrScore
   relations between claims; the writer receives the claim table, the statuses, the edges and the
   weights, and the review panel that follows checks synthesis. A separate LLM step for claim-pair
   relations is left out.
6. **A plan is approved or rejected whole.** Editing a plan before approval (dropping one sub-goal)
   is not offered; reject, re-plan, or apply it and remove what is not wanted.
7. **No local-model provider work** (#825 deferred) and **no live model**: every pipeline is tested
   with sttp-ai's scripted model; the JVM server was driven end to end over the OpenAI-compatible wire
   by a stub that answers from each request's response schema, with real OpenAlex/Crossref lookups.

**Recommended next steps**

1. With a key or a local model: one live `plan-goal` and one `compile-article` on a small topic; read
   the self-check problems the approvals show to tune the draft prompt.
2. An LLM claim-relation step (AttrScore) in compile-article if reviews keep finding synthesis.
3. The research workspace: drop its own `.claude/agents/article-*.md` in favour of the plugin's
   (`plugin/agents`), so the two workflows read one copy (still open from P2).

## 15. Model providers, chains and allowances (2026-10-05)

The research behind this section is in [research-model-providers.md](research-model-providers.md):

- the Claude terms, quoted;
- the local runtimes compared;
- the recommendation: llama.cpp `llama-server` with Qwen 3.6 35B-A3B.

**Verdicts that shaped the build:**

- Running the person's own `claude -p` on their own login, on their own machine, is permitted.
- Offering it to other people as a subscription login is not. The provider exists, is off by default, and is refused when the server listens beyond loopback.

### What was built

**The `claude-code` provider.** A `claude-code:<model>` reference runs ONE `claude -p` process per step attempt (`ClaudeCodeClient`):

- **System prompt.** The step's system prompt replaces Claude Code's own (`--system-prompt-file`).
- **User message on stdin.** The user message, which carries the untrusted data, goes in on stdin, never in argv.
- **Typed answer.** It is `--json-schema`. sttp-ai's schema has its 2020-12 `$schema` marker dropped, because Claude Code validates draft-07. Claude Code re-prompts on a mismatch, and our validators run on the answer as for every provider.
- **Tools.** Built-in tools are off (`--tools ""`).
  - The only MCP server is `--strict-mcp-config` plus a per-call config pointing at the server's new read-only endpoint, `/mcp/agent`. It carries the server's API key as a bearer header when writes are gated, and the file is owner-only.
  - That endpoint serves the seven `Toolbox` tools. They are the same functions the HTTP providers get, so `followups` has no `apply` and `query` runs on the read-only role.
  - The step's allowlist is `--allowedTools`. Every other agent tool is in `--disallowedTools`, which removes it from the model's context. `--permission-mode dontAsk` denies anything else.
  - A step with no tools gets no MCP server at all.
- **Isolation.**
  - `--no-session-persistence`.
  - Hooks disabled (`--settings {"disableAllHooks":true}`).
  - `--disable-slash-commands`.
  - `extra-args` defaults to `--restricted`, which keeps the user's settings files out.
  - The process runs in an empty temporary directory, deleted afterwards.
  - `config-dir` gives the CLI a `CLAUDE_CONFIG_DIR` of its own, for full separation from the user's interactive setup.
- **Credentials.** The CLI's environment loses `ANTHROPIC_API_KEY` and `ANTHROPIC_AUTH_TOKEN` unless `use-api-key = true`, because in `-p` a present key always wins and would bill the API. Histmersion never reads a credential.
- **Reading the result.** Usage, the model and the trace come from `--output-format stream-json`:
  - assistant text, tool calls and their results become the run's trace;
  - `modelUsage` gives the tokens, with cache reads and writes counted as input, as sttp-ai counts them.
- **Concurrency.** The run's interceptors see the whole process as one LLM call. The `max-llm-calls-in-flight` slot is held around it.
- **Errors.**
  - "Usage limit reached" from the plan is `ModelLimitReached`. The model is passed over until the reset time the CLI gives, else for `limit-cooldown-minutes`.
  - A missing executable or a run with no result line is a provider error, which pauses the run with reason `provider`.
  - A cancel kills the CLI's process tree.

**`agent doctor`** (`GET /api/agents/doctor[?offline=true]`, MCP `agent_models {doctor: true}`, CLI `agent doctor [--offline]`, the models window's "check providers"). It spends nothing. Per provider it checks:

- whether a role's chain uses it;
- its key, without showing it;
- its base URL;
- the free model listing (`GET …/models`, online only);
- the chained models present upstream (a warning, since aliases exist);
- every chained model priced and buildable.

For `claude-code` it also checks:

- the loopback rule;
- `claude --version`;
- `claude auth status`, reporting the login method and plan only, never the account;
- that an allowance covers the provider;
- a standing warning that the plan's real remaining usage cannot be seen.

**Model chains** (`ModelChains`, pure, in core):

- `agents.roles.<role>` is now a chain, `provider:model > provider:model > …`. One model is a chain of one, so existing configurations read unchanged.
- A chain saved in the database wins over config until it is reset. That is `agent_role_chains` (V060), set through `agent models set <role> <model>…` / `PUT /api/agents/models/{role}`, MCP `set_agent_models`, or the web UI.
- A run's `--model` still overrides the whole chain.

**Allowances:**

- **What they cover.** One model (`anthropic:claude-sonnet-4-5`) or a whole provider (`claude-code:*`). A Claude plan is one pool, whichever model spends it.
- **Limits.** Tokens and/or USD per **rolling** window (`5h`, `7d`, `day`, `week`, `month` = 30 days), plus a reserve in %.
- **Where they come from.** `agents.allowances` in config, with saved rows (`agent_allowances`, V060) replacing config's for the same target. A saved allowance with no limit means unlimited. A saved row that no longer decodes BLOCKS what it covers instead of lifting the cap.
- **What counts.** Usage is summed from `agent_steps` on every pick; there is no second ledger. Failed attempts count, since they were paid for. V060 adds the `(model, finished_at)` index.
- **When a model frees.** The first moment enough of the oldest spend has left the window that the rest is under `amount − reserve`.

**The pick** (`ModelPicker`), before EVERY LLM attempt, not once per step: the first model of the role's chain that is priced, buildable, not cooling down after a plan limit, and above its reserve under every allowance covering it.

- **Passed-over models.** Each one becomes one run warning, naming the limit.
- **Mid-step guard.** An `AllowanceGuard` interceptor finishes the tool loop once the step's own calls spend the model's headroom. The step then goes to the chain's next model, so at most one call overshoots.
- **Retries.** An attempt cut short by an allowance or a plan limit does not use up a validation retry. At most 8 such switches per step guard against a chain that never settles.
- **When none is left.** The run pauses with the new reason `allowance`. The message reads "all models for role X are below their allowance; next reset at …", and the run records `jobs.resume_at` (V061).
  - A daemon thread re-queues due runs every `agents.allowance-check-seconds`. A run that finds its window spent again simply pauses with the next time.
  - A manual resume works too.
  - Every state change clears `resume_at`.
- **The picture.** `agent models` / `GET /api/agents/models` show each role's chain with the active model and why the others are passed over (and until when), and each allowance's used / left / reserve per window with when it frees. The web UI's new **agent models** window shows the same, opened from the runs window. Chains and allowances are edited there behind the write gate, and the run window of an `allowance`-paused run says when it resumes.

### Deviations and limits

1. **The plan's real remaining usage is not observable.**
   - A `claude-code` allowance counts only what `claude -p` reported to Histmersion.
   - The user's own interactive Claude and Claude Code use spends the same plan unseen.
   - Set the reserve with that in mind; the doctor and the models view say so.
   - A plan limit the CLI reports is caught (`ModelLimitReached`), so the chain moves on even when the cap was too generous.
2. **Rolling windows, not calendar ones.** The global `budgets.daily-usd` and `monthly-usd` stay calendar (UTC day and month) and still apply on top.
3. **Parallel steps can overshoot together.** The three review-panel calls pick at the same moment and may all use the last headroom, as with the run budget (§13.5).
4. **A provider failure still pauses the run.** Only an allowance or a plan limit moves down the chain. A transient network error on the first model must not silently spend a paid fallback.
5. **The probe's usage is not counted** (`agent probe` records no step). It is one small call.
6. **Not verified against a live CLI.** No paid or plan call was made. The CLI provider is tested against a fake `claude` executable replaying recorded `stream-json`, and the doctor against a fake one and a stub listing server.
   - **Uncertain:** whether `--restricted` keeps the subscription login (the docs describe it for evaluation harnesses).
   - **Uncertain:** how a `--mcp-config` server behaves under it.
   - If either fails, set `extra-args = ""` and use `config-dir` for isolation instead.

**Schema:**

- V060: `agent_role_chains`, `agent_allowances`, and the `agent_steps (model, finished_at)` index.
- V061: `jobs.resume_at`.

Both are in the server's native-image metadata.

**Surfaces:**

- **REST:** `PUT|DELETE /api/agents/models/{role}`, `PUT /api/agents/allowances`, `DELETE /api/agents/allowances?target=`, `GET /api/agents/doctor`, plus the agent MCP endpoint `/mcp/agent`.
- **MCP:** `agent_models`, `set_agent_models` (60 tools on `/mcp`).
- **CLI:** `agent models [set <role> <model>… | reset <role>]`, `agent allowance <target> [--limit …]… [--reserve N] [--remove]`, `agent doctor [--offline]`.

## 16. Comparing models per role (#992, 2026-10-08)

To compare a cheaper model (Claude Haiku) with the current role choices on real corpus items, a run
can now write nothing, give one role its own model, record the model that actually served each call,
and be laid beside other runs of the same pipeline on the same target.

### What was built

- **Dry runs.** `agent run … --dry-run` (REST/MCP `dryRun: true`). Every read and every LLM step runs
  as usual; every write to the corpus goes through ONE gate, `RunCtx.write(what, payload)(perform)(simulated)`:
  in a dry run `perform` is skipped and `{what, payload}` (the whole write as JSON — the source patch,
  the article put or patch, the queue item, the proposal, the plan) is recorded on the step
  (`agent_steps.would_write`), shown by `agent show` under "Not written (dry run)" and in the web UI's
  step note as "dry run: not written: …". What is gated, per pipeline:
  - enrich-source: the patch; `refs`/`assess` (called with `apply = false`, and `assess` then records no
    assessment either); the status proposal; ticking off the acquisition item; the log line. The
    closing lint is skipped (it would describe the unpatched source).
  - review-article: the proposals, the fixer's article patch, the tasks and log lines. The fixer's
    unwritten revision is handed to the next round IN MEMORY (its snapshot reviews the revised body), so a
    `--fix` dry run exercises every round a real one would.
  - compile-article: the blocking approval (none is opened; the draft is treated as approved), the
    article put or append, the tasks, goal link and log lines; no chained review run is started (there
    is no written text to review).
  - plan-goal: the blocking approval (treated as approved) and the whole `apply` (sub-goals, links,
    acquisition items, the stored plan), recorded as the plan; the log line.
  - currency-check: `assess` without apply, the edge proposals, the acquisition items and their goal
    links, the log line. acquisition-sweep: the acquisition items and links, the log line.
  - The engine: a dry run never leaves a backlog item, and auto-enrich does not count a dry run as an
    enrichment of the source.
  `jobs.dry_run` is fixed when the run is created: pause, resume and a restart keep it, so a dry run can
  never turn into one that writes (and a run a dry run starts is one too).
- **A role's own model.** `--model-for <role>=<provider:model>` (repeatable; REST `modelFor[{role, model}]`,
  MCP `modelFor: ["role=provider:model"]`) wins over the role's chain and over `--model` for that role,
  for this run only (stored in `params.modelFor`, so a resume keeps it; compile-article hands its review
  roles' overrides to the review run it chains). Checked before the run is queued: the role must exist and
  be one the pipeline (or its chained review) uses, given once, its model a `provider:model` (or a chain)
  that is priced or on a plan and buildable — the readiness `agent models` shows. Plan pools, floors,
  allowances and budgets apply to it as to any chain.
- **The model that served.** `agent_steps.actual_model` holds what the provider reported per step: the
  response's `model` for the API providers (sttp-ai's `llmCalls`), the `modelUsage` keys of a `claude -p`
  result — the busiest (most output tokens) first, several joined by ` + ` when Claude Code handed side
  work to another model. `agent show` and the web UI show `claude-code:haiku → claude-haiku-5-5`.
  A rejected LLM answer is now kept as the failed attempt's `output` (never a checkpoint: only a done
  attempt is replayed).
- **The comparison.** `agent compare <run> <run> […] [--json]`, `GET /api/agents/compare?id=…&id=…`,
  MCP `run_status { compare: [ids] }`: runs of one pipeline on one target (refused otherwise). Per step:
  configured → reported model, how it was paid (price or plan pool), tokens in/out/cache, USD, time,
  attempts and failures. Outputs:
  - enrich-source: findings, the verifier's quote check (every answer of every findings step, a rejected
    one included: an accepted answer carries its own counts — `VerifiedFindings.proposed/quoteFailed` —
    a rejected one is counted from the verifier's reasons), places resolved, dated findings, the classify
    period, summary and notes length, and the claims only that run made (normalised text).
  - review-article: the last verdict and the rounds; round 1 (the one every run reviews on the same text)
    per reviewer (verdict, majors, minors) and by finding kind; its major findings; the majors two runs
    share — same `where` and problems sharing at least half their words, or the same quoted passage.
  Data, not judgement.

### Deviations and limits

1. **Durations are whole seconds** (the step times are stored to the second).
2. **codex and opencode report no model id** of their own in their JSON events; their steps record the
   configured model as the one that served.
3. **A dry review run's coverage is the stored one**: reasons the fixer would set for the article's Not
   used list are recorded with the patch, but the next round's generated sections still show the stored
   reasons.
4. **Upstream reads still happen** in a dry run (metadata, `refs` and `assess` without apply, followups,
   discover): they are reads of OpenAlex/Crossref, not writes.

**Schema:** V070 — `jobs.dry_run`, `agent_steps.actual_model`, `agent_steps.would_write` (in the server's
native-image metadata).

**Surfaces:** CLI `agent run … [--dry-run] [--model-for role=provider:model]…`, `agent compare`; REST
`GET /api/agents/compare`, `RunRequest.dryRun/modelFor`; MCP `start_run { dryRun, modelFor }`,
`run_status { compare }` (no new tool).
