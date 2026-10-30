package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `POST /api/media/import`: ask a provider about places and events — `wikidata` (the default), `depicts`, `met`,
  * `cleveland`, `aic`, `loc`, `pleiades`, `opencontext`, `smithsonian` or `europeana` (docs/media-providers.md).
  * `dryRun` (the default) reads the provider's statements and licences and stores NOTHING; `place`/`event` narrow it to
  * one; `refresh` asks again about ones already asked (and re-reads the licences of what is stored).
  */
final case class MediaImportRequest(
    provider: String = "wikidata",
    dryRun: Boolean = true,
    limit: Option[Int] = None,
    place: Option[String] = None,
    event: Option[String] = None,
    refresh: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema

object MediaImportRequest:
  given Schema[MediaImportRequest] = summon[KindlingsSchema[MediaImportRequest]].schema
