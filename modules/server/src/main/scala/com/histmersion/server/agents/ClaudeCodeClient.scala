package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.{AgentGenerator, FirstCallProbe, LlmCall, LlmClient, ModelLimitReached}

import com.histmersion.config.ClaudeCodeConfig
import com.histmersion.api.Agents.ModelRef
import io.circe.{parser, Json}
import io.circe.syntax.*
import sttp.ai.core.agent.*
import sttp.apispec.circe.*

import java.nio.charset.StandardCharsets
import java.nio.file.Files

/** The `claude-code` provider: ONE `claude -p` process per LLM step attempt, run as the step's agent from the GENERATED
  * headless plugin (docs/research-agent-runtimes.md §2.4, §3a):
  *
  *   - `--plugin-dir <agents>/claude-code/headless --agent histmersion-agents:<role>`: the role's prompt (its includes
  *     and skills inlined — a main-thread agent gets no `skills:` preload) and its tools;
  *     `--settings <roles/<role>/settings.json>`: deny-by-default permissions, NO `disableAllHooks` (it would turn the
  *     quota-guard mod off); `--setting-sources ""` keeps the person's own settings and hooks out (the plan login
  *     stays, §3a P2). `--disable-slash-commands` (config `disable-slash-commands`): no skill or slash command is
  *     loaded — the role's skills are already in its prompt, and a main-thread `--agent` gets no `skills:` preload
  *     anyway (§3a P4).
  *   - Tools: built-in tools off (`--tools ""`, or WebSearch/WebFetch for a web role), the agent MCP endpoint the only
  *     server (`--strict-mcp-config --mcp-config`, which also drops any server a plugin bundles, §3a P4), reached with
  *     a per-step RUN TOKEN that the server binds to this role's tools — the allowlist holds whatever the CLI does.
  *   - The user message — the untrusted data — on stdin; the typed answer `--json-schema`.
  *   - Quota: every `rate_limit_event` (both windows, every call, §3a P1) is recorded in the Claude plan pool as it
  *     arrives; when the pool falls below its floor mid-step the process is stopped. The quota-guard mod does the same
  *     from inside (it denies tools and aborts the turn: the result then says `terminal_reason: "aborted_streaming"`
  *     with an empty answer). Either way the step ends as the plan's limit — the chain is re-picked, and pauses with
  *     `plan-quota`.
  *
  * Without a generated plugin (generation off or failed) it falls back to `--system-prompt-file` with the same prompt.
  * It never touches a credential: a key in the server's environment is REMOVED from the CLI's unless `use-api-key` (a
  * present key always wins in `-p`).
  */
final class ClaudeCodeClient(val model: ModelRef, cfg: ClaudeCodeConfig, rt: CliRuntime) extends LlmClient:

  def run[Out](call: LlmCall[Out]): AgentResult[Either[AgentFailure, Out]] =
    CliProcess.withDir("histmersion-claude-") { dir =>
      val grant = Option.when(call.tools.nonEmpty)(rt.grant(call, call.agent, cfg.timeoutSeconds)).flatten
      try
        val mcp = grant.map(g =>
          CliProcess.privateFile(dir.resolve("mcp.json"), ClaudeCode.mcpConfig(rt.agentMcpUrl, Some(g.token)).noSpaces)
        )
        // the generated plugin and role settings, when they exist for this agent
        val plugin = for
          a <- call.agent
          p <- rt.trees.claudePlugin.filter(d => Files.isRegularFile(d.resolve("agents").resolve(s"${a.name}.md")))
          s <- rt.trees.claudeRole(a.name).filter(Files.isRegularFile(_))
        yield ClaudeCode.Launch.Agent(p.toString, a.name, s.toString)
        val launch = plugin.getOrElse(
          ClaudeCode.Launch.SystemPrompt(
            Files.writeString(dir.resolve("system.md"), call.system, StandardCharsets.UTF_8).toString
          )
        )
        val web = call.agent.map(a => AgentGenerator.claudeWebTools(a.web)).getOrElse(Nil)
        // the CLI's working directory: empty, so no project CLAUDE.md, .claude/ or .mcp.json is read
        val work = Files.createDirectories(dir.resolve("work"))
        val argv = ClaudeCode.argv(
          cfg,
          model.model,
          call.maxIterations,
          ClaudeCode.schemaJson(call.schema),
          launch,
          mcp.map(_.toString),
          call.tools.map(_.name),
          rt.definitions.tools.map(_.value),
          web
        )
        val pool = rt.pools
        // the LLM-slot semaphore and the latency the trace shows: the interceptors see the whole process
        // as one call. Carried out of the interceptor chain in a reference (its return type is sttp-ai's).
        val out = java.util.concurrent.atomic.AtomicReference[Option[ClaudeCode.Outcome]](None)
        val stopped = java.util.concurrent.atomic.AtomicReference[Option[String]](None)
        val ctx = LlmCallContext(
          ConversationHistory.withInitialPrompt(call.user),
          call.tools.nonEmpty,
          IterationInfo(1, math.max(1, call.maxIterations))
        )
        val _ = AgentInterceptor.compose(call.interceptors).aroundLlmCall(ctx) {
          val r = CliProcess.run(
            argv,
            work,
            env => ClaudeCode.environment(cfg, call.maxTokens, grant.map(_.token), rt.quotaUrl, env),
            call.user,
            cfg.timeoutSeconds,
            "install Claude Code or set agents.providers.claude-code.command (HISTMERSION_AGENTS_CLAUDE_COMMAND) to its absolute path",
            onLine = l => pool.flatMap(p => ClaudeCode.watch(l, p))
          )
          stopped.set(r.stopped)
          val o = ClaudeCode.parse(r.lines)
          out.set(Some(o))
          // the whole process is one intercepted call: its first model call is read from the stream
          FirstCallProbe.report(call.interceptors, o.firstCall)
          if o.result.isEmpty && r.stopped.isEmpty then
            throw java.io.IOException(
              s"claude -p exited ${r.exit} without a result" +
                (if r.stderr.nonEmpty then s": ${r.stderr.takeRight(600)}" else "")
            )
          AgentResponse(o.result.fold("")(_.text), Nil, StopReason.EndTurn, Some(o.usage), o.modelId)
        }
        val o = out.get().getOrElse(throw IllegalStateException("claude-code: no outcome"))
        // every reading of the run is in the pool (also the ones the watch already recorded: newest wins)
        pool.foreach(p => p.record(com.histmersion.api.PlanQuota.PoolId.ClaudePlan, o.readings))
        val guard = stopped
          .get()
          .orElse(o.result.filter(ClaudeCode.abortedByGuard).map(_ => "the quota-guard mod ended the turn"))
        guard match
          case Some(why) =>
            val until = pool
              .flatMap(_.blocked(com.histmersion.api.PlanQuota.PoolId.ClaudePlan, Some(model.model)))
              .flatMap(_._2)
              .map(java.time.Instant.ofEpochMilli)
            throw ModelLimitReached(model, until, s"claude -p stopped by the quota guard: $why")
          case None =>
            ClaudeCode.result(o, call.user, call.schema) match
              case Right(r)  => r
              case Left(lim) => throw ModelLimitReached(model, lim.until, lim.message)
      finally grant.foreach(g => rt.tokens.revoke(g.token))
    }
