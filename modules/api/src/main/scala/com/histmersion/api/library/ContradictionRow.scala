package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class ContradictionRow(
    citekey: String,
    title: String,
    year: Option[Int],
    status: com.histmersion.api.library.Source.Status,
    direction: Option[String],
    kind: Option[String],
    otherCitekey: Option[String],
    otherTitle: Option[String],
    otherYear: Option[Int] = None,
    otherStatus: Option[com.histmersion.api.library.Source.Status] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
