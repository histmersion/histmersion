package com.histmersion.api.catalogue
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema
import com.histmersion.api.StrictJson
import com.histmersion.api.given

final case class PeriodInput(start: Long, end: Long) derives KindlingsSchema
object PeriodInput:
  given Schema[PeriodInput] = summon[KindlingsSchema[PeriodInput]].schema

  given Codec.AsObject[PeriodInput] = StrictJson.codec(KindlingsCodecAsObject.derived[PeriodInput])
