package com.histmersion.api.personal

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class UnreadPage(rows: List[UnreadRow], total: Int, offset: Int = 0)
    derives KindlingsCodecAsObject,
      KindlingsSchema

object UnreadPage:
  given Schema[UnreadPage] = summon[KindlingsSchema[UnreadPage]].schema
