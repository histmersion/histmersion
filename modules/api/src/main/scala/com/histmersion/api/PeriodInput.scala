package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema

/** Deep time as signed years, BCE negative (3300 BCE -> -3300). */
final case class PeriodInput(start: Long, end: Long) derives KindlingsSchema
object PeriodInput:
  given Schema[PeriodInput] = summon[KindlingsSchema[PeriodInput]].schema

  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[PeriodInput] = StrictJson.codec(KindlingsCodecAsObject.derived[PeriodInput])
