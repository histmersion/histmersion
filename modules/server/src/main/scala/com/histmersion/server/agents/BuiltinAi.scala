package com.histmersion.server.agents

import com.histmersion.api.automation.Agents.ProviderKind
import com.histmersion.plugin.*
import com.histmersion.util.plugins.HostProvided

import scala.collection.immutable.ListMap

/** Histmersion's six AI providers as plugins: in every binary, each enabled by default and switched off by
  * `histmersion.plugins.ai-<name>.enabled = false`. The three HTTP APIs (`ai-anthropic`, `ai-openai`, `ai-local`) are
  * first-party plugins found on the class path, run through the host's loop as any plugin's provider; the three
  * command-line runtimes (`claude-code`, `codex`, `opencode`) the host still supplies itself, run by its own clients
  * with their `agents.providers.<name>.*` settings, read at each call.
  */
object BuiltinAi:

  /** The command-line runtimes the host runs itself. */
  type TitlesPart2 = TitlesPart2.T
  object TitlesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PluginOfResult = PluginOfResult.T
  object PluginOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PluginIds = PluginIds.T
  object PluginIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reserved = Reserved.T
  object Reserved extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KnownResult = KnownResult.T
  object KnownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KnownRegisteredPart1 = KnownRegisteredPart1.T
  object KnownRegisteredPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LookupResultPart1 = LookupResultPart1.T
  object LookupResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LookupRegisteredPart1 = LookupRegisteredPart1.T
  object LookupRegisteredPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val runtimes: List[ProviderKind] = List(ProviderKind.ClaudeCode, ProviderKind.Codex, ProviderKind.Opencode)

  val titles: Map[ProviderKind, com.histmersion.server.agents.BuiltinAi.TitlesPart2] = Map(
    ProviderKind.ClaudeCode -> "Claude Code (claude -p on your login)",
    ProviderKind.Codex -> "Codex CLI (codex exec on your login)",
    ProviderKind.Opencode -> "opencode (opencode run)"
  )

  /** One entry per host-run provider, in [[ProviderKind.values]]' order. */
  val entries: List[HostAiProvider] =
    ProviderKind.values.toList.filter(runtimes.contains).map(k => HostAiProvider(pluginOf(k), k))

  /** The plugin a built-in provider comes with. */
  def pluginOf(kind: ProviderKind): com.histmersion.server.agents.BuiltinAi.PluginOfResult = s"ai-${kind.wire}"

  /** The six built-in providers' plugins. */
  val pluginIds: Set[com.histmersion.server.agents.BuiltinAi.PluginIds] = ProviderKind.values.map(pluginOf).toSet

  def plugins: List[Plugin] = entries.map(e => HostProvided(e.plugin, _.aiProviders.add(e.kind.wire, e)))

  /** Names a plugin's provider may not take. */
  val reserved: Set[com.histmersion.server.agents.BuiltinAi.Reserved] = ProviderKind.values.map(_.wire).toSet

  /** Pure: the names plugin providers are registered under — what `provider:model` may name besides the host's own
    * (`ModelRef.parse`'s `known`).
    */
  def known(registered: ListMap[com.histmersion.server.agents.BuiltinAi.KnownRegisteredPart1, AiProvider]): Set[com.histmersion.server.agents.BuiltinAi.KnownResult] = registered.keySet.toSet -- reserved

  /** Pure: the provider a reference names among the registered ones, or why it cannot be used: a built-in provider
    * whose plugin is switched off, or a plugin provider no longer registered.
    */
  def lookup(kind: ProviderKind, registered: ListMap[com.histmersion.server.agents.BuiltinAi.LookupRegisteredPart1, AiProvider]): Either[com.histmersion.server.agents.BuiltinAi.LookupResultPart1, AiProvider] =
    registered
      .get(kind.wire)
      .toRight(
        if reserved(kind.wire) then
          s"the ${kind.wire} provider is switched off (histmersion.plugins.${pluginOf(kind)}.enabled = false)"
        else s"no AI provider '${kind.wire}' is registered (its plugin is switched off or was removed)"
      )
