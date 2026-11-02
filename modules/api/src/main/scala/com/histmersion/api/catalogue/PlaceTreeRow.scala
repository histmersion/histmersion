package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class PlaceTreeRow(
    id: String,
    label: String,
    kind: String,
    depth: Int,
    findings: Int,
    contested: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema
object PlaceTreeRow:
  given Schema[PlaceTreeRow] = summon[KindlingsSchema[PlaceTreeRow]].schema
