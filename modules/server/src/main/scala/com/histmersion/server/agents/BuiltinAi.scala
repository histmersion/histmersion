package com.histmersion.server.agents

import com.histmersion.api.Agents.ProviderKind
import com.histmersion.plugin.*
import com.histmersion.util.plugins.HostProvided

import scala.collection.immutable.ListMap

/** Today's six providers as BUILT-IN plugins: in every binary, each enabled by default and switched off by
  * `histmersion.plugins.ai-<name>.enabled = false`; their settings stay `agents.providers.<name>.*` (layered, not
  * mapped — as the media collections' and event catalogues', §19).
  */
object BuiltinAi:

  val titles: Map[ProviderKind, String] = Map(
    ProviderKind.Anthropic -> "Anthropic API",
    ProviderKind.OpenAI -> "OpenAI API",
    ProviderKind.Local -> "OpenAI-compatible local server",
    ProviderKind.ClaudeCode -> "Claude Code (claude -p on your login)",
    ProviderKind.Codex -> "Codex CLI (codex exec on your login)",
    ProviderKind.Opencode -> "opencode (opencode run)"
  )

  /** One entry per host provider, in [[ProviderKind.values]]' order. */
  val entries: List[HostAiProvider] = ProviderKind.values.map(k => HostAiProvider(s"ai-${k.wire}", k))

  val pluginIds: Set[String] = entries.map(_.plugin).toSet

  def plugins: List[Plugin] = entries.map(e => HostProvided(e.plugin, _.aiProviders.add(e.kind.wire, e)))

  /** Names a plugin's provider may not take. */
  val reserved: Set[String] = ProviderKind.values.map(_.wire).toSet

  /** Pure: the names plugin providers are registered under — what `provider:model` may name besides the host's own
    * (`ModelRef.parse`'s `known`).
    */
  def known(registered: ListMap[String, AiProvider]): Set[String] =
    registered.collect { case (name, p) if !p.isInstanceOf[HostAiProvider] => name }.toSet

  /** Pure: the provider a reference names among the registered ones, or why it cannot be used: a host provider whose
    * plugin is switched off, or a plugin provider no longer registered.
    */
  def lookup(kind: ProviderKind, registered: ListMap[String, AiProvider]): Either[String, AiProvider] =
    registered
      .get(kind.wire)
      .toRight(entries.find(_.kind == kind) match
        case Some(e) => s"the ${kind.wire} provider is switched off (histmersion.plugins.${e.plugin}.enabled = false)"
        case None    => s"no AI provider '${kind.wire}' is registered (its plugin is switched off or was removed)")
