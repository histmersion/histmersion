package com.histmersion.pluginhost

import com.histmersion.api.{PluginsReport, PluginStatus}
import com.histmersion.plugin.*
import org.ekrich.config.Config

import scala.jdk.CollectionConverters.*
import scala.util.Try

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
    withSecrets: Set[String] = Set.empty,
    /** The effective block of each plugin this build registered (its own block over its defaults, as reported). */
    blocks: Map[String, Config] = Map.empty,
    /** The secrets each plugin this build registered was given, by reference, as this build resolved them. */
    secretValues: Map[String, Map[SecretRef, String]] = Map.empty
):
  /** A plugin's status and contributions, to carry into the next generation unchanged. */
  def carry(id: String): Option[(PluginStatus, Registries)] =
    report.plugins.find(_.id == id).map(st => st -> contributions.getOrElse(id, Registries.empty))

  /** The plugins registered in this generation (afresh or carried over). */
  def registered: Set[String] = contributions.keySet

  /** The value of the secret the setting `key` of the registered plugin `pluginId` refers to, as this generation
    * resolved it for that plugin; `None` when the setting holds no secret reference (or the plugin is not registered).
    */
  def secret(pluginId: String, key: String): Option[String] =
    for
      block <- blocks.get(pluginId)
      text <- Try(block.getString(key)).toOption
      ref <- SecretRef.parse(text).toOption
      value <- secretValues.get(pluginId).flatMap(_.get(ref))
    yield value

object Generation:
  val empty: Generation = Generation(Registries.empty, PluginsReport(Spi.Version))
