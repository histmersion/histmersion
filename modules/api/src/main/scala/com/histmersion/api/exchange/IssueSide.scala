package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.api.given

final case class IssueSide(label: String, uid: UUID, lines: List[String] = Nil)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
