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
  override def perPlace: Int = conf.perPlace
  override def requestIntervalMs: Int = conf.requestIntervalMs
  override def refusalBackoffMs: List[Long] = conf.refusalBackoffMs
  override def check: Either[String, Unit] =
    Either.cond(conf.enabled, (), s"$provider is switched off ($key.enabled = false)")

object MuseumCollection:
  type Provider = Provider.T
  object Provider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
