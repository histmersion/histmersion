package com.histmersion.domain.library.acquisition

import com.histmersion.api.plugins.PluginStatus
import com.histmersion.domain.AcquireRecheck
import com.histmersion.plugin.Access
import com.histmersion.pluginhost.Registries

/** What the LEGAL acquisition chain is, as the acquisition list's re-check compares it: the legal fetchers in the order
  * they run, each fetcher plugin's version, state and effective settings (secrets as their references, as every report
  * shows them), and whether a contact e-mail and a CORE key are there. A fetcher the config marks unauthorised is left
  * out: the re-check never asks one. Pure.
  */
object FetcherGeneration:

  type PartsResult = PartsResult.T
  object PartsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PartsPlanPart1 = PartsPlanPart1.T
  object PartsPlanPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PartsContact = PartsContact.T
  object PartsContact extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PartsCoreKey = PartsCoreKey.T
  object PartsCoreKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfResult = OfResult.T
  object OfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfPlanPart1 = _root_.com.histmersion.domain.library.acquisition.FetcherGeneration.PartsPlanPart1
  val OfPlanPart1: _root_.com.histmersion.domain.library.acquisition.FetcherGeneration.PartsPlanPart1.type = _root_.com.histmersion.domain.library.acquisition.FetcherGeneration.PartsPlanPart1
  type OfContact = _root_.com.histmersion.domain.library.acquisition.FetcherGeneration.PartsContact
  val OfContact: _root_.com.histmersion.domain.library.acquisition.FetcherGeneration.PartsContact.type = _root_.com.histmersion.domain.library.acquisition.FetcherGeneration.PartsContact
  type OfCoreKey = _root_.com.histmersion.domain.library.acquisition.FetcherGeneration.PartsCoreKey
  val OfCoreKey: _root_.com.histmersion.domain.library.acquisition.FetcherGeneration.PartsCoreKey.type = _root_.com.histmersion.domain.library.acquisition.FetcherGeneration.PartsCoreKey

  def parts(
      plan: List[(com.histmersion.domain.library.acquisition.FetcherGeneration.PartsPlanPart1, Access)],
      plugins: List[PluginStatus],
      contact: com.histmersion.domain.library.acquisition.FetcherGeneration.PartsContact,
      coreKey: com.histmersion.domain.library.acquisition.FetcherGeneration.PartsCoreKey
  ): List[com.histmersion.domain.library.acquisition.FetcherGeneration.PartsResult] =
    val legal = plan.filter(_._2 != Access.Unauthorised)
    val provided = legal.map((id, _) => s"${Registries.SourceFetcher}:$id").toSet
    val owners = plugins.filter(_.provides.exists(provided)).sortBy(_.id).map { p =>
      s"plugin ${p.id} ${p.version} ${p.state} " + p.settings
        .sortBy(_.key)
        .map(s => s"${s.key}=${s.value}")
        .mkString(" ")
    }
    legal.map((id, access) => s"$id:${access.toString.toLowerCase(java.util.Locale.ROOT)}") ++ owners ++
      List(s"contact=$contact", s"core-key=$coreKey")

  def of(plan: List[(com.histmersion.domain.library.acquisition.FetcherGeneration.OfPlanPart1, Access)], plugins: List[PluginStatus], contact: com.histmersion.domain.library.acquisition.FetcherGeneration.OfContact, coreKey: com.histmersion.domain.library.acquisition.FetcherGeneration.OfCoreKey): com.histmersion.domain.library.acquisition.FetcherGeneration.OfResult =
    AcquireRecheck.generation(parts(plan, plugins, contact, coreKey))
