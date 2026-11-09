package com.histmersion
package server.agents

import com.histmersion.api.Problem
import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.agents.{
  AgentDef,
  AgentsControllers,
  FirstCallProbe,
  LlmCall,
  LlmClient,
  ModelLimitReached,
  PlanQuotaRepo
}

import com.histmersion.config.CodexConfig
import com.histmersion.api.automation.Agents.ModelRef
import com.histmersion.api.automation.PlanQuota
import io.circe.{parser, Json}
import io.circe.syntax.*
import sttp.ai.core.agent.*
import sttp.apispec.circe.*

import java.nio.charset.StandardCharsets
import java.nio.file.Files

/** The `codex` provider: `codex exec --json -p <role>` per step attempt, on the GENERATED Codex home
  * (docs/research-agent-runtimes.md §2.4): the role's profile carries its prompt (includes and skills inlined — with
  * the shell off the model could not open a listed skill) and narrows Histmersion's MCP server to the role's tools; the
  * base config switches the shell, web search, images, apps, plugins, memories and sub-agents off, read-only sandbox,
  * no approvals. The empty working directory keeps any project `AGENTS.md` and `.codex/` out; the run token decides the
  * tools the endpoint serves. After each step the ChatGPT plan pool is read from the app-server (throttled).
  */
final class CodexExecClient(val model: ModelRef, cfg: CodexConfig, rt: CliRuntime) extends LlmClient:

  def run[Out](call: LlmCall[Out]): AgentResult[Either[AgentFailure, Out]] =
    val agent = call.agent.getOrElse(throw java.io.IOException("codex: a step must run as an agent definition"))
    val home = rt.trees.codexHome.getOrElse(
      throw java.io.IOException("codex: no CODEX_HOME — set agents.runtimes.dir or agents.providers.codex.home")
    )
    if !Files.isRegularFile(home.resolve(s"${agent.name}.config.toml")) then
      throw java.io.IOException(s"codex: no profile for ${agent.name} in $home — run `histmersion agent generate`")
    // the home's shared lock for the whole step (a retention pass never runs under it, #2582); a pass
    // may follow once the step is done
    try Codex.Home.using(home)(runIn(call, agent, home))
    finally Codex.Home.maybeRetain(home, cfg)

  private def runIn[Out](
      call: LlmCall[Out],
      agent: AgentDef,
      home: java.nio.file.Path
  ): AgentResult[Either[AgentFailure, Out]] =
    CliProcess.withDir(CliProcess.WithDirPrefix("histmersion-codex-")) { dir =>
      val timeout = CliProcess.RunTimeoutSeconds(cfg.timeoutSeconds.value)
      val grant = rt.grant(call, Some(agent), timeout)
      try
        val schema = CliProcess.privateFile(
          dir.resolve("schema.json"),
          CliProcess.PrivateFileText(Codex.outputSchemaJson(call.schema).value)
        )
        val work = Files.createDirectories(dir.resolve("work"))
        val last = dir.resolve("last.txt")
        val argv = Codex.argv(cfg, model.model, agent.name, schema, work, last)
        val out = java.util.concurrent.atomic.AtomicReference[Option[CliOutcome]](None)
        val ctx = LlmCallContext(
          ConversationHistory.withInitialPrompt(call.user.value),
          call.tools.nonEmpty,
          IterationInfo(1, math.max(1, call.maxIterations.value))
        )
        val _ = AgentInterceptor.compose(call.interceptors).aroundLlmCall(ctx) {
          val r = CliProcess.run(
            argv,
            work,
            env => Codex.environment(cfg, home, grant.map(_.token), env),
            CliProcess.RunStdin(call.user.value),
            timeout,
            CliProcess
              .RunHint("install the Codex CLI or set agents.providers.codex.command (HISTMERSION_AGENTS_CODEX_COMMAND)")
          )
          val o = Codex.parse(r.lines)
          val lastText = Option
            .when(Files.isRegularFile(last))(Files.readString(last, StandardCharsets.UTF_8))
            .filter(_.trim.nonEmpty)
          val withLast = o.modify(_.finalText).setTo(lastText.map(CliOutcome.FinalText(_)).orElse(o.finalText))
          out.set(Some(withLast))
          FirstCallProbe.report(call.interceptors, None) // one intercepted call for the whole loop: no per-call usage
          if withLast.finalText.isEmpty && withLast.error.isEmpty then
            throw java.io.IOException(
              s"codex exec exited ${r.exit} without an answer" +
                (if r.stderr.value.nonEmpty then s": ${r.stderr.value.takeRight(600)}" else "")
            )
          AgentResponse(
            withLast.finalText.fold("")(_.value),
            Nil,
            StopReason.EndTurn,
            Some(withLast.usage),
            Some(model.model.value)
          )
        }
        val o = out.get().getOrElse(throw IllegalStateException("codex: no outcome"))
        rt.pools.foreach(_.refresh(PlanQuotaRepo.EpochMillis(System.currentTimeMillis())))
        o.error.filter(e => CliOutcomes.isLimit(ClaudeCode.Limit.Sentence(e.value))) match
          case Some(m) =>
            val until = rt.pools
              .flatMap(_.blocked(PlanQuota.PoolId.ChatgptPlan, Some(AgentsControllers.ModelName(model.model.value))))
              .flatMap(_._2)
              .map(ms => java.time.Instant.ofEpochMilli(ms.value))
            throw new ModelLimitReached(model, until, domain.Problem(s"codex exec: $m".take(300)))
          case None =>
            CliOutcomes.result(
              o,
              call.user,
              call.schema,
              Some(ClaudeCode.ModelId(model.model.value))
            )
      finally grant.foreach(g => rt.tokens.revoke(g.token))
    }
