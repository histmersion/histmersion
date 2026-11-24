package com.histmersion
package domain.library.acquisition

import com.histmersion.api.plugins.PluginStatus
import com.histmersion.domain.AcquireRecheck
import com.histmersion.plugin.Access
import com.histmersion.pluginhost.{FetchChain, Registries}
import com.histmersion.lint.{LintRule, tolerated}

/** What the LEGAL acquisition chain is, as the acquisition list's re-check compares it: the legal fetchers in the order
  * they run, each fetcher plugin's version, state and effective settings (secrets as their references, as every report
  * shows them), and whether a contact e-mail and a CORE key are there. A fetcher the config marks unauthorised is left
  * out: the re-check never asks one. Pure.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object FetcherGeneration:

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parts(
      plan: List[(FetchChain.Step.Id, Access)],
      plugins: List[PluginStatus],
      contact: Boolean,
      coreKey: Boolean
  ): List[AcquireRecheck.GenerationPart] =
    val legal = plan.filter(_._2 != Access.Unauthorised)
    val provided = legal.map((id, _) => s"${Registries.SourceFetcher}:$id").toSet
    val owners = plugins.filter(_.provides.exists(p => provided(p.value))).sortBy(_.id.value).map { p =>
      s"plugin ${p.id} ${p.version} ${p.state} " + p.settings
        .sortBy(_.key.value)
        .map(s => s"${s.key}=${s.value}")
        .mkString(" ")
    }
    (legal.map((id, access) => s"$id:${access.toString.toLowerCase(java.util.Locale.ROOT)}") ++ owners ++
      List(s"contact=$contact", s"core-key=$coreKey")).map(AcquireRecheck.GenerationPart(_))

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def of(
      plan: List[(FetchChain.Step.Id, Access)],
      plugins: List[PluginStatus],
      contact: Boolean,
      coreKey: Boolean
  ): AcquireClients.Generation =
    AcquireClients.Generation(AcquireRecheck.generation(parts(plan, plugins, contact, coreKey)).value)
