package com.histmersion.plugin.testkit

import com.histmersion.plugin.*

/** How the server judges one record a media collection answered, before it ties it to a place.
  *
  * @param key
  *   the record's key, or the reason the collection gave for an unreadable one
  * @param licence
  *   the licence as the SERVER reads the statement (`CC-BY-4.0`), when it can
  * @param refused
  *   why the server would not keep it (withheld, no credit line, a licence family the allowlist `media.collections`
  *   lacks, no image); `None`: it would be considered for a place — the server's match rules (names on the map, dates)
  *   decide the rest
  */
final case class MediaVerdict(key: MediaVerdict.Key, item: Option[MediaItem], licence: Option[MediaVerdict.Licence], refused: Option[MediaVerdict.Refused])

object MediaVerdict:
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Licence = Licence.T
  object Licence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Refused = Refused.T
  object Refused extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
