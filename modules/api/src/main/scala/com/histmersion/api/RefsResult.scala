package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class RefsResult(
    target: String,
    doi: Option[String],
    backend: String,
    count: Int,
    resolved: Int,
    edgesAdded: Int,
    references: List[RefRow]
) derives KindlingsCodecAsObject,
      KindlingsSchema
object RefsResult:
  given Schema[RefsResult] = summon[KindlingsSchema[RefsResult]].schema
