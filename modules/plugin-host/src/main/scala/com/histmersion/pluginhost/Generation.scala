package com.histmersion.pluginhost

import com.histmersion.files.*
import com.histmersion.api.{PluginsReport, PluginStatus}
import com.histmersion.plugin.*

import scala.jdk.CollectionConverters.*

/** One load of the plugins for one config: the registries and the report. Built by [[PluginHost.build]] as a pure
  * function of (plugins, user config, environment, host services) — no global state, no static cache — so a later
  * config reload (docs/research-plugins.md §3.5) builds the next generation beside the running one and swaps it in only
  * when it is complete.
  */
final case class Generation(
    registries: Registries,
    report: PluginsReport,
    /** What each registered plugin contributed — what a partial rebuild carries over unchanged. */
    contributions: Map[String, Registries] = Map.empty,
    /** The plugins this build registered afresh (all of them on a full build); the others were carried over from the
      * previous generation (config reload, §3.5).
      */
    rebuilt: List[String] = Nil,
    /** The registered plugins whose config holds a secret: never carried over, so a reload resolves their secrets again
      * (a rotated password needs only `config reload`, §3.7).
      */
    withSecrets: Set[String] = Set.empty
):
  /** A plugin's status and contributions, to carry into the next generation unchanged. */
  def carry(id: String): Option[(PluginStatus, Registries)] =
    report.plugins.find(_.id == id).map(st => st -> contributions.getOrElse(id, Registries.empty))

object Generation:
  val empty: Generation = Generation(Registries.empty, PluginsReport(Spi.Version))
