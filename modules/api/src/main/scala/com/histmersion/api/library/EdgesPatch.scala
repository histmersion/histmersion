package com.histmersion.api.library
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema
import com.histmersion.api.StrictJson
import com.histmersion.api.given

final case class EdgesPatch(add: List[EdgeInput] = Nil, remove: List[EdgeInput] = Nil) derives KindlingsSchema
object EdgesPatch:
  given Schema[EdgesPatch] = summon[KindlingsSchema[EdgesPatch]].schema

  given Codec.AsObject[EdgesPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[EdgesPatch])
