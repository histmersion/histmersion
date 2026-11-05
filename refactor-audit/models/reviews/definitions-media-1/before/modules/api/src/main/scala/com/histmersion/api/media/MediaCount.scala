package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MediaCount(key: String, n: Int, allowed: Boolean = true)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object MediaCount:


  def of(keys: List[String], allowed: String => Boolean = _ => true): List[MediaCount] =
    keys
      .groupBy(identity)
      .toList
      .map((k, v) => MediaCount(k, v.size, allowed(k)))
      .sortBy(c => (-c.n, c.key))
