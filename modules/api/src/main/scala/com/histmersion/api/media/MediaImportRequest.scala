package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MediaImportRequest(
    provider: String = "wikidata",
    dryRun: Boolean = true,
    limit: Option[Int] = None,
    place: Option[String] = None,
    event: Option[String] = None,
    refresh: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
