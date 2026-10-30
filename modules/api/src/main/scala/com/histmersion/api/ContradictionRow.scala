package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.SourceStatus

final case class ContradictionRow(
    citekey: String,
    title: String,
    year: Option[Int],
    status: SourceStatus,
    direction: Option[String],
    kind: Option[String],
    otherCitekey: Option[String],
    otherTitle: Option[String],
    otherYear: Option[Int] = None,
    otherStatus: Option[SourceStatus] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ContradictionRow:
  given Schema[ContradictionRow] = summon[KindlingsSchema[ContradictionRow]].schema
