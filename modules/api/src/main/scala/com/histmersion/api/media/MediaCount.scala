package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MediaCount(key: MediaCount.Key, n: MediaCount.N, allowed: MediaCount.Allowed = true)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object MediaCount:


  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type N = N.T
  object N extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Allowed = Allowed.T
  object Allowed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(keys: List[String], allowed: String => Boolean = _ => true): List[MediaCount] =
    keys
      .groupBy(identity)
      .toList
      .map((k, v) => MediaCount(k, v.size, allowed(k)))
      .sortBy(c => (-c.n, c.key))
