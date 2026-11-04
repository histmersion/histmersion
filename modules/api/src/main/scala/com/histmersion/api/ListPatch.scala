package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema

final case class ListPatch(
    add: List[String] = Nil,
    remove: List[String] = Nil,
    replace: Option[List[String]] = None
) derives KindlingsSchema, com.histmersion.api.json.Schema
object ListPatch:


  given Codec.AsObject[ListPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[ListPatch])
