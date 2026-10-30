package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class PlaceImportResult(
    places: Int,
    names: Int,
    relations: Int,
    failed: List[String] = Nil,
    job: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object PlaceImportResult:
  given Schema[PlaceImportResult] = summon[KindlingsSchema[PlaceImportResult]].schema
