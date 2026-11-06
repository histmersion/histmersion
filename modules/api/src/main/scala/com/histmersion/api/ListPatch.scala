package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema

final case class ListPatch(
    add: List[ListPatch.Add] = Nil,
    remove: List[ListPatch.Remove] = Nil,
    replace: Option[List[ListPatch.Replace]] = None
) derives KindlingsSchema, com.histmersion.api.json.Schema
object ListPatch:



  given Codec.AsObject[ListPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[ListPatch])
