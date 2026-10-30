package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID

final case class IssueSide(label: String, uid: UUID, lines: List[String] = Nil)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object IssueSide:
  given Schema[IssueSide] = summon[KindlingsSchema[IssueSide]].schema
