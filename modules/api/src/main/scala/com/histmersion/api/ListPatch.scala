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
) derives KindlingsSchema
object ListPatch:
  given Schema[ListPatch] = summon[KindlingsSchema[ListPatch]].schema

  given Codec.AsObject[ListPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[ListPatch])
