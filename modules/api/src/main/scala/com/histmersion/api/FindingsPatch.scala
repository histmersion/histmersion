package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema

final case class FindingsPatch(
    add: List[FindingInput] = Nil,
    remove: List[String] = Nil,
    replace: Option[List[FindingInput]] = None
) derives KindlingsSchema
object FindingsPatch:
  given Schema[FindingsPatch] = summon[KindlingsSchema[FindingsPatch]].schema

  given Codec.AsObject[FindingsPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[FindingsPatch])
