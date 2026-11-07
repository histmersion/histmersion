package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.{FirstCallProbe, LlmCall, LlmClient, ModelLimitReached}

import com.histmersion.config.OpencodeConfig
import com.histmersion.api.automation.Agents.ModelRef
import io.circe.{parser, Json}
import io.circe.syntax.*
import sttp.ai.core.agent.*
import sttp.apispec.circe.*

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
    CliProcess.withDir(com.histmersion.server.agents.CliProcess.WithDirPrefix("histmersion-opencode-")) { dir =>
      val grant = rt.grant(call, Some(agent), cfg.timeoutSeconds)
      try
        val argv = Opencode.argv(cfg, model.model, agent.name, dir.toString)
        val user = call.user + Opencode.schemaNote(Codex.schemaJson(call.schema))
        val out = java.util.concurrent.atomic.AtomicReference[Option[CliOutcome]](None)
        val ctx = LlmCallContext(
          ConversationHistory.withInitialPrompt(call.user),
          call.tools.nonEmpty,
          IterationInfo(1, math.max(1, call.maxIterations))
        )
        val _ = AgentInterceptor.compose(call.interceptors).aroundLlmCall(ctx) {
          val r = CliProcess.run(
            argv,
            dir,
            env => Opencode.environment(xdg.toString, conf.toString, grant.map(_.token), env),
            user,
            cfg.timeoutSeconds,
            "install opencode or set agents.providers.opencode.command (HISTMERSION_AGENTS_OPENCODE_COMMAND)"
          )
          val o = Opencode.parse(r.lines)
          out.set(Some(o))
          FirstCallProbe.report(call.interceptors, None) // one intercepted call for the whole loop: no per-call usage
          if o.finalText.isEmpty && o.error.isEmpty then
            throw java.io.IOException(
              s"opencode run exited ${r.exit} without an answer" +
                (if r.stderr.nonEmpty then s": ${r.stderr.takeRight(600)}" else "")
            )
          AgentResponse(o.finalText.getOrElse(""), Nil, StopReason.EndTurn, Some(o.usage), Some(model.model))
        }
        val o = out.get().getOrElse(throw IllegalStateException("opencode: no outcome"))
        o.error.filter(CliOutcomes.isLimit) match
          case Some(m) => throw ModelLimitReached(model, None, s"opencode run: $m".take(300))
          case None    => CliOutcomes.result(o, call.user, call.schema, Some(model.model))
      finally grant.foreach(g => rt.tokens.revoke(g.token))
    }
