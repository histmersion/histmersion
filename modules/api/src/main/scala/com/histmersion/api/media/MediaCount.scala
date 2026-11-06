package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MediaCount(key: MediaCount.Key, n: MediaCount.N, allowed: MediaCount.Allowed = _root_.com.histmersion.api.media.MediaCount.Allowed(true))
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object MediaCount:



  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)
  type N = N.T
  object N extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Allowed = Allowed.T
  object Allowed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(keys: List[com.histmersion.api.media.MediaCount.Key], allowed: com.histmersion.api.media.MediaCount.Key => Boolean = _ => (true)): List[MediaCount] =
    keys
      .groupBy(identity)
      .toList
      .map((k, v) => MediaCount(_root_.com.histmersion.api.media.MediaCount.Key((k).value), _root_.com.histmersion.api.media.MediaCount.N(v.size), _root_.com.histmersion.api.media.MediaCount.Allowed((allowed(_root_.com.histmersion.api.media.MediaCount.Key((k).value))))))
      .sortBy(c => (-(c.n).value, c.key))
