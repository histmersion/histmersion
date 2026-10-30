package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.SourceStatus

final case class StatusCount(status: SourceStatus, count: Int) derives KindlingsCodecAsObject, KindlingsSchema
object StatusCount:
  given Schema[StatusCount] = summon[KindlingsSchema[StatusCount]].schema
