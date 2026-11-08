package com.histmersion
package server.agents

import com.histmersion.api.automation.Agents.ProviderKind
import com.histmersion.plugin.*
import com.histmersion.util.plugins.HostProvided

import scala.collection.immutable.ListMap
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.Title

/** Histmersion's six AI providers as plugins: in every binary, each enabled by default and switched off by
  * `histmersion.plugins.ai-<name>.enabled = false`. The three HTTP APIs (`ai-anthropic`, `ai-openai`, `ai-local`) are
  * first-party plugins found on the class path, run through the host's loop as any plugin's provider; the three
  * command-line runtimes (`claude-code`, `codex`, `opencode`) the host still supplies itself, run by its own clients
  * with their `agents.providers.<name>.*` settings, read at each call.
  */
object BuiltinAi:
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]

  /** The command-line runtimes the host runs itself. */
  type Reserved = Reserved.T
  object Reserved extends Newtype[String, Pure]

  val runtimes: List[ProviderKind] = List(ProviderKind.ClaudeCode, ProviderKind.Codex, ProviderKind.Opencode)

  val titles: Map[ProviderKind, Title] = Map(
    ProviderKind.ClaudeCode -> Title("Claude Code (claude -p on your login)"),
    ProviderKind.Codex -> Title("Codex CLI (codex exec on your login)"),
    ProviderKind.Opencode -> Title("opencode (opencode run)")
  )

  /** One entry per host-run provider, in [[ProviderKind.values]]' order. */
  val entries: List[HostAiProvider] =
    ProviderKind.values.toList.filter(runtimes.contains).map(k => HostAiProvider(pluginOf(k), k))

  /** The plugin a built-in provider comes with. */
  def pluginOf(kind: ProviderKind): com.histmersion.util.plugins.Plugin.Id =
    com.histmersion.util.plugins.Plugin.Id.assume(s"ai-${kind.wire.value}")

  /** The six built-in providers' plugins. */
  val pluginIds: Set[com.histmersion.util.plugins.Plugin.Id] = ProviderKind.values.map(pluginOf).toSet

  def plugins: List[Plugin] = entries.map(e =>
    HostProvided(
      e.plugin,
      _.aiProviders.add(Registry.AddId(e.kind.wire.value), e).left.map(m => Plugin.RegisterResultPart1(m.value))
    )
  )

  /** Names a plugin's provider may not take. */
  val reserved: Set[Reserved] = ProviderKind.values.map(k => Reserved(k.wire.value)).toSet

  /** Pure: the names plugin providers are registered under — what `provider:model` may name besides the host's own
    * (`ModelRef.parse`'s `known`).
    */
  def known(
      registered: ListMap[com.histmersion.util.plugins.Plugin.Id, AiProvider]
  ): Set[com.histmersion.util.plugins.Plugin.Id] = registered.keySet.toSet.filterNot(id => reserved(Reserved(id.value)))

  /** Pure: the provider a reference names among the registered ones, or why it cannot be used: a built-in provider
    * whose plugin is switched off, or a plugin provider no longer registered.
    */
  def lookup(
      kind: ProviderKind,
      registered: ListMap[com.histmersion.util.plugins.Plugin.Id, AiProvider]
  ): Either[Problem, AiProvider] =
    registered
      .get(com.histmersion.util.plugins.Plugin.Id.assume(kind.wire.value))
      .toRight(
        Problem(
          if reserved(Reserved(kind.wire.value)) then
            s"the ${kind.wire} provider is switched off (histmersion.plugins.${pluginOf(kind)}.enabled = false)"
          else s"no AI provider '${kind.wire}' is registered (its plugin is switched off or was removed)"
        )
      )
