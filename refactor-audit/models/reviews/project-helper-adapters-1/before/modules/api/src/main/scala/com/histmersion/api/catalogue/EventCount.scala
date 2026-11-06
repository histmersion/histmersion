package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class EventCount(key: EventCount.Key, n: EventCount.N) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
object EventCount:


  type OfKeys = OfKeys.T
  object OfKeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)
  type N = N.T
  object N extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(keys: List[com.histmersion.api.catalogue.EventCount.OfKeys]): List[EventCount] =
    keys.groupBy(identity).toList.map((k, v) => EventCount(k, _root_.com.histmersion.api.catalogue.EventCount.N(v.size))).sortBy(c => (-(c.n).value, c.key))
  def add(a: List[EventCount], b: List[EventCount]): List[EventCount] =
    (a ++ b).groupBy(_.key).toList.map((k, cs) => EventCount(k, cs.map(_.n).sum)).sortBy(c => (-(c.n).value, c.key))
