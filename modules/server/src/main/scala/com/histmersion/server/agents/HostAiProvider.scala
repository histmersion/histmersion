package com.histmersion
package server.agents
import com.histmersion.util.problem.Problem

import com.histmersion.api.automation.Agents.ProviderKind
import com.histmersion.plugin.*
import com.histmersion.pluginhost.HostOwned
import com.histmersion.domain.Title
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** An AI provider that is Histmersion's OWN code (the three CLI runtimes), registered through the plugin host like any
  * other (docs/research-plugins.md §22, D2) but run by the host's own clients with its `agents.providers.<name>.*`
  * settings, unchanged and read at each call. The SPI's `turn` is never called for it.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@movesTo(FeaturePlace.AutomationAgents)
final case class HostAiProvider(plugin: HostAiProvider.Plugin, kind: ProviderKind) extends AiProvider, HostOwned:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def title: AiProvider.Title =
    AiProvider.Title(BuiltinAi.titles.getOrElse(kind, Title(kind.wire.value)).value)
  def turn(request: AiTurn.Request, http: AiHttp): Either[Problem, AiTurn] =
    Left(Problem.Invalid.Broken.here(Problem.Invalid.Rule.HostProvider(Problem.Raw.of(kind.wire))))

object HostAiProvider:

  export util.plugins.Plugin.Id as Plugin
