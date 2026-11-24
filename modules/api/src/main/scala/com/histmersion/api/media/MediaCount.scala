package com.histmersion
package api.media
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class MediaCount(
    key: MediaCount.Key,
    n: MediaCount.N,
    allowed: MediaCount.Allowed = MediaCount.Allowed(true)
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema
object MediaCount:

  type Key = Key.T
  object Key extends Newtype[String, Pure]
  type N = N.T
  object N extends Newtype[Int, Pure]
  type Allowed = Allowed.T
  object Allowed extends Newtype[Boolean, Pure]

  def of(keys: List[Key], allowed: Key => Boolean = _ => (true)): List[MediaCount] =
    keys
      .groupBy(identity)
      .toList
      .map((k, v) => MediaCount(k, N(v.size), Allowed((allowed(k)))))
      .sortBy(c => (-(c.n).value, c.key))
