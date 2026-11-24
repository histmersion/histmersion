package com.histmersion
package plugins.media
import com.histmersion.util.problem.{External, Problem}

import com.histmersion.plugin.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** A collection that needs a key of the user's own: registered with its key resolved from its secret reference, or
  * none. Without one its import is refused with what to set, and the API is not asked.
  *
  * @param conf
  *   its settings
  * @param plugin
  *   its plugin's id, named in what to set
  * @param key
  *   the key, when one is set
  * @param needs
  *   what key it needs ("an api.data.gov key")
  * @param settings
  *   its settings' path (`media.smithsonian`)
  */
abstract class KeyedCollection(
    conf: CollectionSettings,
    plugin: KeyedCollection.Plugin,
    key: Option[KeyedCollection.Key],
    needs: KeyedCollection.Needs,
    settings: KeyedCollection.Settings
) extends MediaCollection:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def perPlace: MediaCollection.PerPlace = MediaCollection.PerPlace(conf.perPlace.value)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def requestIntervalMs: MediaCollection.RequestIntervalMs =
    MediaCollection.RequestIntervalMs(conf.requestIntervalMs.value)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def refusalBackoffMs: List[MediaCollection.RefusalBackoffMs] =
    conf.refusalBackoffMs.map(ms => MediaCollection.RefusalBackoffMs(ms.value))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def latestYear: Option[Year] = conf.latestYear.map(y => Year(y.value))
  val role = MediaCollection.Object

  /** What `histmersion media providers` says of it: its description, and that it is not asked without a key. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  protected def described(text: String): MediaCollection.Description =
    MediaCollection.Description(text + (if key.isEmpty then " — NO KEY SET: not asked" else ""))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def check: Either[Problem, Unit] =
    if !conf.enabled.value then
      Left(
        Problem.Refused
          .Disabled(
            Problem.Refused.Feature(s"$title"),
            Problem.SettingKey(s"histmersion.$settings.enabled"),
            None,
            None
          )
      )
    else
      key
        .toRight(
          Problem.Config.Setting(
            Problem.SettingKey(s"histmersion.plugins.$plugin.api-key"),
            Problem.Config.Fault.KeyNeeded(External.PluginSaid.of(s"$needs"))
          )
        )
        .map(_ => ())

  /** The key, for a request: a collection without one is never asked ([[check]]). */
  protected def apiKey: KeyedCollection.Key = key.getOrElse(KeyedCollection.Key(""))

  /** Pure: an HTTP 401/403 of a key-gated API named as a refused key, with the setting to check. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  protected def explain(e: Problem): Problem = e.answered.filter(_.refusesAccess) match
    case Some(status) =>
      Problem.Config.Setting(
        Problem.SettingKey(s"histmersion.plugins.${plugin.value}.api-key"),
        Problem.Config.Fault.Rejected(Problem.Upstream.Name(s"${plugin.value}"), status)
      )
    case None => e

object KeyedCollection:
  type Plugin = Plugin.T
  object Plugin extends Newtype[String, Pure]
  type Key = Key.T
  object Key extends Newtype[String, Pure]
  type Needs = Needs.T
  object Needs extends Newtype[String, Pure]
  type Settings = Settings.T
  object Settings extends Newtype[String, Pure]
