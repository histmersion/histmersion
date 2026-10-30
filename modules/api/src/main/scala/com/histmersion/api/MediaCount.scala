package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A count by key (a provider, a licence, a role, a reason); `allowed = false` marks a licence the provider's allowlist
  * refuses.
  */
final case class MediaCount(key: String, n: Int, allowed: Boolean = true)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object MediaCount:
  given Schema[MediaCount] = summon[KindlingsSchema[MediaCount]].schema

  /** Pure: count `keys`, most frequent first, then by key. */
  def of(keys: List[String], allowed: String => Boolean = _ => true): List[MediaCount] =
    keys
      .groupBy(identity)
      .toList
      .map((k, v) => MediaCount(k, v.size, allowed(k)))
      .sortBy(c => (-c.n, c.key))
