package com.histmersion
package plugins.media

import com.histmersion.plugin.*
import io.github.iltotore.iron.constraint.any.Pure

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
abstract class MuseumCollection(conf: CollectionSettings, provider: MuseumCollection.Provider, key: MuseumCollection.Key) extends MediaCollection:
  override def perPlace: MediaCollection.PerPlaceResult = MediaCollection.PerPlaceResult(conf.perPlace.value)
  override def requestIntervalMs: MediaCollection.RequestIntervalMsResult = MediaCollection.RequestIntervalMsResult(conf.requestIntervalMs.value)
  override def refusalBackoffMs: List[MediaCollection.RefusalBackoffMsResult] =
    conf.refusalBackoffMs.map(ms => MediaCollection.RefusalBackoffMsResult(ms.value))
  override def check: Either[MediaCollection.CheckResultPart1, Unit] =
    Either.cond(
      conf.enabled.value,
      (),
      MediaCollection.CheckResultPart1(s"${provider.value} is switched off (${key.value}.enabled = false)")
    )

object MuseumCollection:
  type Provider = Provider.T
  object Provider extends Newtype[String, Pure]
  type Key = Key.T
  object Key extends Newtype[String, Pure]
