package com.histmersion
package plugins.media

import com.histmersion.plugin.{Plugin, Spi}
import com.histmersion.util.problem.{External, Problem}
import hearth.kindlings.sconfigderivation.ConfigReader

/** One of Histmersion's own media collections: on by default (its defaults resource; the two that need a key of the
  * user's own are off), switched off by `histmersion.plugins.<id>.enabled = false`. Its settings are Histmersion's own
  * `histmersion.media.*`, which the host supplies it; its block holds `enabled` and, for a keyed one, its key.
  */
trait MediaPlugin(id: String) extends Plugin:
  val info: Plugin.Info =
    Plugin.Info(Plugin.Info.Id(id), Plugin.Info.Version(MediaPlugin.Version), Plugin.Info.SpiVersion(Spi.Version))

object MediaPlugin:
  val Version = "0.1.0"

  /** The collection `plugin` says, in its own words, why it did not answer or did not take a record: `claim` is what
    * kind of failure that is (an upstream's error message, an operation it does not offer, a record it withholds).
    */
  def said(plugin: String, claim: Problem.Plugin.Claim, text: String): Problem =
    Problem.Plugin.Said(Problem.Plugin.Id(plugin), claim, External.PluginSaid.of(text))

  /** The collection `plugin` does not take the record `item`: `refusal`. */
  def rejected(plugin: String, item: String, refusal: Problem.Plugin.Refusal): Problem =
    Problem.Plugin.Rejected(Problem.Plugin.Id(plugin), Problem.Raw(item), refusal)

  /** The provider's rights field `field` does not mark the record open: it states `stated`, when it states anything. */
  def notOpen[A](field: Problem.Field, stated: Option[A]): Problem.Plugin.Refusal =
    Problem.Plugin.Refusal.NotOpen(field, stated.toVector.map(v => Problem.Raw(s"$v")))

  /** The answer lacks `field`, found in `at` (outermost first). */
  def missing(field: String, at: String*): Problem =
    Problem.Invalid.Missing(Problem.Field(field), at.toVector.map(Problem.Field(_)))

  /** A media plugin's settings: Histmersion's own `media.*`, which the host supplies. */
  final case class Config(media: MediaSettings) derives ConfigReader
