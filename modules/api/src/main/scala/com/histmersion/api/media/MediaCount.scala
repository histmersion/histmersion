package com.histmersion
package api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class MediaCount(key: MediaCount.Key, n: MediaCount.N, allowed: MediaCount.Allowed = MediaCount.Allowed(true))
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object MediaCount:



  type Key = Key.T
  object Key extends RefinedType[String, Pure]:
    given scala.math.Ordering[T] = scala.math.Ordering.by[T, String](_.value)
  type N = N.T
  object N extends RefinedType[Int, Pure]
  type Allowed = Allowed.T
  object Allowed extends RefinedType[Boolean, Pure]

  def of(keys: List[Key], allowed: Key => Boolean = _ => (true)): List[MediaCount] =
    keys
      .groupBy(identity)
      .toList
      .map((k, v) => MediaCount(Key((k).value), N(v.size), Allowed((allowed(Key((k).value))))))
      .sortBy(c => (-(c.n).value, c.key))
