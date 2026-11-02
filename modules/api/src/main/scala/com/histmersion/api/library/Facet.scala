package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class Facet(
    value: String,
    count: Int,
    id: Option[String] = None,
    contested: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema
object Facet:
  given Schema[Facet] = summon[KindlingsSchema[Facet]].schema
