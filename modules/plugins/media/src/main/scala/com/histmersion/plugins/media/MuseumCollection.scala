package com.histmersion
package plugins.media
import com.histmersion.util.problem.Problem

import com.histmersion.plugin.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** One of the open-access collections searched by a place's name, with its own `media.<collection>` block: its pace,
  * its patience with a refusal, how many records it keeps per place, and its own switch.
  *
  * @param conf
  *   its settings
  * @param provider
  *   its name as the host shows it in a refusal ("The Metropolitan Museum of Art")
  * @param key
  *   its settings' path (`media.met`), named when it is switched off
  */
abstract class MuseumCollection(
    conf: CollectionSettings,
    provider: MuseumCollection.Provider,
    key: MuseumCollection.Key
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
  override def check: Either[Problem, Unit] =
    Either.cond(
      conf.enabled.value,
      (),
      Problem.Refused
        .Disabled(Problem.Refused.Feature(s"$provider"), Problem.SettingKey(s"histmersion.$key.enabled"), None, None)
    )

object MuseumCollection:
  type Provider = Provider.T
  object Provider extends Newtype[String, Pure]
  type Key = Key.T
  object Key extends Newtype[String, Pure]
