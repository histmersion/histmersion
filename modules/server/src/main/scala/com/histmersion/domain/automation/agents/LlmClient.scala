package com.histmersion
package domain.automation.agents
import com.github.plokhotnyuk.jsoniter_scala.core.{writeToString, JsonValueCodec}
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.server.agents.{BuiltinAi, ClaudeCode, CliRuntime, Codex, HostAiProvider, Opencode}

import com.histmersion.pluginhost.HostAiHttp

import com.histmersion.config.AgentsConfig
import com.histmersion.api.automation.Agents
import com.histmersion.api.automation.Agents.{ModelRef, ProviderKind}
import sttp.ai.core.agent.*
import sttp.ai.core.model.{AIModel, Capability}
import sttp.shared.Identity
import com.histmersion.util.plugins.Plugin
import com.histmersion.plugin.AiProvider
import com.histmersion.plugin.AiHttp

/** A configured model, ready to run [[LlmCall]]s. The engine never sees a provider type: the loop (sttp-ai's) and its
  * builders are hidden behind [[run]], so a pinned-but-pre-1.0 library touches few files.
  */
trait LlmClient:
  def model: ModelRef
  def run[Out](call: LlmCall[Out]): AgentResult[Either[AgentFailure, Out]]

object LlmClient:

  /** Apply an [[LlmCall]] to any sttp-ai builder. Generic in the model type so a plugin's provider and the test kit's
    * scripted model go through the same configuration.
    */

  def configure[M <: AIModel & Capability.All, Out](
      b: AgentBuilder[Identity, M, String, String],
      c: LlmCall[Out]
  ): Agent[Identity, String, Out] =
    b.systemPrompt(c.system.value)
      .maxIterations(math.max(1, c.maxIterations.value))
      .maxTokens(math.max(1, c.maxTokens.value))
      .tools(c.tools)
      .interceptors(c.interceptors)
      .responseSchema(c.schema)
      .build

  /** How the engine builds a client for a model (the seam a test fills with sttp-ai's scripted model). */
  type Factory = (AgentsConfig, ModelRef) => Either[Problem, LlmClient]

  /** The server's factory, resolving the provider through `providers` — the AI-provider registry of the plugin
    * generation the calling thread is pinned to (docs/research-plugins.md §22): a command-line runtime (`claude-code`,
    * `codex`, `opencode`; its built-in plugin on) runs on the generated trees, run tokens and plan pools of `rt`; any
    * other registered provider — the HTTP APIs' first-party plugins among them — runs through the host's tool loop
    * ([[PluginAiClient]]); a switched-off or unregistered one is refused, saying which.
    */
  def factory(
      rt: CliRuntime,
      providers: () => scala.collection.immutable.ListMap[Plugin.Id, AiProvider],
      http: AiHttp = HostAiHttp()
  ): Factory =
    (cfg, ref) =>
      BuiltinAi
        .lookup(ref.provider, providers())
        .left
        .map(why => Problem.Unclassified(Legacy.Text(s"${ref.wire}: ${why.sentence}")))
        .flatMap {
          case _: HostAiProvider => host(rt)(cfg, ref)
          case p                 =>
            PluginAiClient
              .client(ref, p, http, PluginAiClient.Loopback(rt.loopback))
        }

  /** The host's own providers: the command-line runtimes on `rt`. */
  def host(rt: CliRuntime): Factory =
    (cfg, ref) =>
      ref.provider match
        case ProviderKind.ClaudeCode =>
          ClaudeCode.client(cfg.providers.claudeCode, rt, ref)
        case ProviderKind.Codex =>
          Codex.client(cfg.providers.codex, rt, ref)
        case ProviderKind.Opencode =>
          Opencode.client(cfg.providers.opencode, rt, ref)
        case k =>
          Left(
            Problem.Unclassified(Legacy.Text(s"${ref.wire}: ${k.wire} is not one of the host's command-line providers"))
          )

  /** sttp-ai's usage → ours. */
  def usage(u: TokenUsage): Agents.Usage =
    Agents.Usage(
      Agents.Usage.Input(u.inputTokens.value),
      Agents.Usage.Output(u.outputTokens.value),
      Agents.Usage.CacheRead(u.cachedInputTokens.value),
      Agents.Usage.CacheWrite(u.cacheWriteInputTokens.value)
    )

  /** The model the provider reported serving a step (#992): the id each call's response named (an API response's
    * `model`, claude -p's `modelUsage` keys), the busiest (most output tokens) first, joined by ` + ` when several
    * served it (Claude Code may hand side work to a smaller model). None when no call named one.
    */
  def servedBy(calls: Seq[LlmCallUsage]): Option[AgentRepo.StepRecord.ActualModel] =
    val out = calls
      .flatMap(c => c.model.map(_.trim).filter(_.nonEmpty).map(_ -> c.usage.outputTokens.value))
      .groupMapReduce(_._1)(_._2)(_ + _)
    Option.when(out.nonEmpty)(
      AgentRepo.StepRecord.ActualModel(out.toList.sortBy((m, n) => (-n, m)).map(_._1).mkString(" + "))
    )

  /** The model's final text, whether or not it parsed as the typed answer. */
  def rawAnswer[Out](a: Either[AgentFailure, Out])(using JsonValueCodec[Out]): String =
    a.fold(_.rawAnswer, out => writeToString(out))

  /** A [[FinishReason]] as words, for a run's trace. */
  def finish(r: FinishReason): String = r match
    case FinishReason.NaturalStop    => "answered"
    case FinishReason.MaxIterations  => "iteration limit"
    case FinishReason.TokenLimit     => "token limit"
    case FinishReason.BudgetExceeded => "budget exhausted"
    case FinishReason.Custom(why)    => why
    case FinishReason.Error(m)       => s"error: $m"
