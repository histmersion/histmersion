package com.histmersion
package server.agents
import com.histmersion.util.problem.Text.sentence

import com.histmersion.domain.automation.agents.{FirstCallProbe, LlmCall, LlmClient, ModelLimitReached}

import com.histmersion.config.OpencodeConfig
import com.histmersion.api.automation.Agents.ModelRef
import sttp.ai.core.agent.*

/** The `opencode` provider: `opencode run --agent <role> --format json -m <provider/model>` on the GENERATED config
  * (`XDG_CONFIG_HOME` and `OPENCODE_CONFIG_DIR` pointed at it, the Claude Code folders and autoupdate off, an empty
  * working directory): the role's agent denies every tool but its own. opencode has no schema flag, so the schema
  * travels in the message and the final text is parsed as JSON (the validators send a miss back, as for every
  * provider).
  */
final class OpencodeRunClient(val model: ModelRef, cfg: OpencodeConfig, rt: CliRuntime) extends LlmClient:

  def run[Out](call: LlmCall[Out]): AgentResult[Either[AgentFailure, Out]] =
    val agent = call.agent.getOrElse(throw java.io.IOException("opencode: a step must run as an agent definition"))
    val (xdg, conf) = (for x <- rt.trees.opencodeXdg; c <- rt.trees.opencodeConfig yield (x, c))
      .filter(_._2.resolve("agents").resolve(s"${agent.name}.md").toFile.isFile)
      .getOrElse(
        throw java.io.IOException(s"opencode: no generated agent ${agent.name} — run `histmersion agent generate`")
      )
    CliProcess.withDir(CliProcess.WithDirPrefix("histmersion-opencode-")) { dir =>
      val timeout = CliProcess.RunTimeoutSeconds(cfg.timeoutSeconds.value)
      val grant = rt.grant(call, Some(agent), timeout)
      try
        val argv = Opencode.argv(cfg, model.model, agent.name, dir)
        val user = CliProcess.RunStdin(call.user.value + Opencode.schemaNote(Codex.schemaJson(call.schema)).value)
        val out = java.util.concurrent.atomic.AtomicReference[Option[CliOutcome]](None)
        val ctx = LlmCallContext(
          ConversationHistory.withInitialPrompt(call.user.value),
          call.tools.nonEmpty,
          IterationInfo(1, math.max(1, call.maxIterations.value))
        )
        val _ = AgentInterceptor.compose(call.interceptors).aroundLlmCall(ctx) {
          val r = CliProcess.run(
            argv,
            dir,
            env => Opencode.environment(xdg, conf, grant.map(_.token), env),
            user,
            timeout,
            CliProcess.RunHint(
              "install opencode or set agents.providers.opencode.command (HISTMERSION_AGENTS_OPENCODE_COMMAND)"
            )
          )
          val o = Opencode.parse(r.lines)
          out.set(Some(o))
          FirstCallProbe.report(call.interceptors, None) // one intercepted call for the whole loop: no per-call usage
          if o.finalText.isEmpty && o.error.isEmpty then
            throw java.io.IOException(
              s"opencode run exited ${r.exit} without an answer" +
                (if r.stderr.value.nonEmpty then s": ${r.stderr.value.takeRight(600)}" else "")
            )
          AgentResponse(o.finalText.fold("")(_.value), Nil, StopReason.EndTurn, Some(o.usage), Some(model.model.value))
        }
        val o = out.get().getOrElse(throw IllegalStateException("opencode: no outcome"))
        o.error.filter(e => CliOutcomes.isLimit(ClaudeCode.Limit.Sentence(e.sentence))) match
          case Some(m) =>
            // m is opencode run's own words (Opencode.parse), whole: "opencode run: <what it said>"
            throw new ModelLimitReached(model, None, m)
          case None =>
            CliOutcomes.result(
              o,
              call.user,
              call.schema,
              Some(ClaudeCode.ModelId(model.model.value))
            )
      finally grant.foreach(g => rt.tokens.revoke(g.token))
    }
