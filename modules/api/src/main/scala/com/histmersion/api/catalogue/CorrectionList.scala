package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.syntax.*
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CorrectionList(total: Int, corrections: List[Correction])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object CorrectionList:
  given Schema[CorrectionList] = summon[KindlingsSchema[CorrectionList]].schema
