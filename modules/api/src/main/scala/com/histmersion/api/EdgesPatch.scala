package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema

final case class EdgesPatch(add: List[EdgeInput] = Nil, remove: List[EdgeInput] = Nil) derives KindlingsSchema
object EdgesPatch:
  given Schema[EdgesPatch] = summon[KindlingsSchema[EdgesPatch]].schema

  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[EdgesPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[EdgesPatch])
