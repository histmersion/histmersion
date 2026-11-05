package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class EventCount(key: String, n: Int) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
object EventCount:


  def of(keys: List[String]): List[EventCount] =
    keys.groupBy(identity).toList.map((k, v) => EventCount(k, v.size)).sortBy(c => (-c.n, c.key))
  def add(a: List[EventCount], b: List[EventCount]): List[EventCount] =
    (a ++ b).groupBy(_.key).toList.map((k, cs) => EventCount(k, cs.map(_.n).sum)).sortBy(c => (-c.n, c.key))
