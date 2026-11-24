package com.histmersion
package pluginhost

import com.histmersion.api.plugins.{PluginsReport, PluginStatus}
import com.histmersion.plugin.*
import org.ekrich.config.Config

import scala.jdk.CollectionConverters.*
import scala.util.Try
import com.histmersion.lint.{LintRule, tolerated}

/** One load of the plugins for one config: the registries and the report. Built by [[PluginHost.build]] as a pure
  * function of (plugins, user config, environment, host services) — no global state, no static cache — so a later
  * config reload (docs/research-plugins.md §3.5) builds the next generation beside the running one and swaps it in only
  * when it is complete.
  */
final case class Generation(
    registries: Registries,
    report: PluginsReport,
    /** What each registered plugin contributed — what a partial rebuild carries over unchanged. */
    contributions: Map[Plugin.Info.Id, Registries] = Map.empty,
    /** The plugins this build registered afresh (all of them on a full build); the others were carried over from the
      * previous generation (config reload, §3.5).
      */
    rebuilt: List[Plugin.Info.Id] = Nil,
    /** The registered plugins whose config holds a secret: never carried over, so a reload resolves their secrets again
      * (a rotated password needs only `config reload`, §3.7).
      */
    withSecrets: Set[Plugin.Info.Id] = Set.empty,
    /** The effective block of each plugin this build registered (its own block over its defaults, as reported). */
    blocks: Map[Plugin.Info.Id, Config] = Map.empty,
    /** The secrets each plugin this build registered was given, by reference, as this build resolved them. */
    secretValues: Map[Plugin.Info.Id, Map[Secret.Ref, PluginHost.SecretValue]] = Map.empty
):
  /** A plugin's status and contributions, to carry into the next generation unchanged. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def carry(id: Plugin.Info.Id): Option[(PluginStatus, Registries)] =
    report.plugins.find(_.id.value == id.value).map(st => st -> contributions.getOrElse(id, Registries.empty))

  /** The plugins registered in this generation (afresh or carried over). */
  def registered: Set[Plugin.Info.Id] = contributions.keySet

  /** The value of the secret the setting `key` of the registered plugin `pluginId` refers to, as this generation
    * resolved it for that plugin; `None` when the setting holds no secret reference (or the plugin is not registered).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def secret(pluginId: Plugin.Info.Id, key: PluginHost.ConfigPath): Option[PluginHost.SecretValue] =
    for
      block <- blocks.get(pluginId)
      text <- Try(block.getString(key.value)).toOption
      ref <- Secret.Ref.parse(Secret.ParseText(text)).toOption
      value <- secretValues.get(pluginId).flatMap(_.get(ref))
    yield value

object Generation:
  val empty: Generation = Generation(Registries.empty, PluginsReport(PluginsReport.SpiVersion(Spi.Version)))
