package com.histmersion.api

import hearth.kindlings.tapirschemaderivation.KindlingsSchema

/** `POST /api/site-types/refine`: `limit` sites at most (none = every one not asked yet), `refresh` asks again about
  * those asked before, `dryRun` only counts.
  */
final case class SiteTypesRequest(dryRun: Boolean = false, limit: Option[Int] = None, refresh: Boolean = false)
    derives hearth.kindlings.circederivation.KindlingsCodecAsObject,
      KindlingsSchema
object SiteTypesRequest:
  given sttp.tapir.Schema[SiteTypesRequest] = summon[KindlingsSchema[SiteTypesRequest]].schema
