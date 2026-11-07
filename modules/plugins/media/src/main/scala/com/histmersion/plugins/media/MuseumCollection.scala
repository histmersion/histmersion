package com.histmersion.plugins.media

import com.histmersion.plugin.*

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
  override def perPlace: com.histmersion.plugin.MediaCollection.PerPlaceResult = com.histmersion.plugin.MediaCollection.PerPlaceResult(conf.perPlace.value)
  override def requestIntervalMs: com.histmersion.plugin.MediaCollection.RequestIntervalMsResult = com.histmersion.plugin.MediaCollection.RequestIntervalMsResult(conf.requestIntervalMs.value)
  override def refusalBackoffMs: List[com.histmersion.plugin.MediaCollection.RefusalBackoffMsResult] =
    conf.refusalBackoffMs.map(ms => com.histmersion.plugin.MediaCollection.RefusalBackoffMsResult(ms.value))
  override def check: Either[com.histmersion.plugin.MediaCollection.CheckResultPart1, Unit] =
    Either.cond(
      conf.enabled.value,
      (),
      com.histmersion.plugin.MediaCollection.CheckResultPart1(s"${provider.value} is switched off (${key.value}.enabled = false)")
    )

object MuseumCollection:
  type Provider = Provider.T
  object Provider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
