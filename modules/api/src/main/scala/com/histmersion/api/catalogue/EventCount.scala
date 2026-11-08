package com.histmersion
package api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class EventCount(key: EventCount.Key, n: EventCount.N) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
object EventCount:



  type Key = Key.T
  object Key extends Newtype[String, Pure]
  type N = N.T
  object N extends Newtype[Int, Pure]

  def of(keys: List[Key]): List[EventCount] =
    keys.groupBy(identity).toList.map((k, v) => EventCount(Key((k).value), N(v.size))).sortBy(c => (-(c.n).value, c.key))
  def add(a: List[EventCount], b: List[EventCount]): List[EventCount] =
    (a ++ b).groupBy(_.key).toList.map((k, cs) => EventCount(k, N(cs.map(c => (c.n.value: Int)).sum))).sortBy(c => (-(c.n).value, c.key))
