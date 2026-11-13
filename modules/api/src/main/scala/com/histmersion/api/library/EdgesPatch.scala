package com.histmersion
package api.library
import scala.annotation.tailrec
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.StrictJson
import com.histmersion.api.given

final case class EdgesPatch(add: List[Edge.Input] = Nil, remove: List[Edge.Input] = Nil)
    derives StrictJson,
      KindlingsSchema,
      com.histmersion.api.json.Schema
