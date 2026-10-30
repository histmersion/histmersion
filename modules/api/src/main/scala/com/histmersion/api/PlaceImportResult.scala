package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `POST /api/places/import` — what the upsert wrote. */
final case class PlaceImportResult(
    places: Int,
    names: Int,
    relations: Int,
    failed: List[String] = Nil,
    /** A real `places fetch` runs as a background job (#1946): its id, the counts then being zero. */
    job: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object PlaceImportResult:
  given Schema[PlaceImportResult] = summon[KindlingsSchema[PlaceImportResult]].schema
