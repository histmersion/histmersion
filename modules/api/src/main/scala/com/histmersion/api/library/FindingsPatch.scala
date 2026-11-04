package com.histmersion.api.library
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema
import com.histmersion.api.StrictJson
import com.histmersion.api.given

final case class FindingsPatch(
    add: List[Finding.Input] = Nil,
    remove: List[String] = Nil,
    replace: Option[List[Finding.Input]] = None
) derives KindlingsSchema, com.histmersion.api.json.Schema
object FindingsPatch:


  given Codec.AsObject[FindingsPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[FindingsPatch])
