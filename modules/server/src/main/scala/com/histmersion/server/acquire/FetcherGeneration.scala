package com.histmersion.server.acquire

import com.histmersion.api.PluginStatus
import com.histmersion.domain.AcquireRecheck
import com.histmersion.plugin.Access
import com.histmersion.pluginhost.Registries

/** What the LEGAL acquisition chain is, as the acquisition list's re-check compares it: the legal fetchers
  * in the order they run, each fetcher plugin's version, state and effective settings (secrets as their
  * references, as every report shows them), and whether a contact e-mail and a CORE key are there. A
  * fetcher the config marks unauthorised is left out: the re-check never asks one. Pure. */
object FetcherGeneration:

  def parts(plan: List[(String, Access)], plugins: List[PluginStatus], contact: Boolean, coreKey: Boolean): List[String] =
    val legal = plan.filter(_._2 != Access.Unauthorised)
    val provided = legal.map((id, _) => s"${Registries.SourceFetcher}:$id").toSet
    val owners = plugins.filter(_.provides.exists(provided)).sortBy(_.id).map { p =>
      s"plugin ${p.id} ${p.version} ${p.state} " + p.settings.sortBy(_.key).map(s => s"${s.key}=${s.value}").mkString(" ")
    }
    legal.map((id, access) => s"$id:${access.toString.toLowerCase(java.util.Locale.ROOT)}") ++ owners ++
      List(s"contact=$contact", s"core-key=$coreKey")

  def of(plan: List[(String, Access)], plugins: List[PluginStatus], contact: Boolean, coreKey: Boolean): String =
    AcquireRecheck.generation(parts(plan, plugins, contact, coreKey))
