package com.histmersion
package api.library
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema
import com.histmersion.api.StrictJson
import com.histmersion.api.given

final case class EdgesPatch(add: List[Edge.Input] = Nil, remove: List[Edge.Input] = Nil) derives KindlingsSchema, com.histmersion.api.json.Schema
object EdgesPatch:


  given Codec.AsObject[EdgesPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[EdgesPatch])
