package com.histmersion.api.catalogue
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given

final case class SiteTypesRequest(dryRun: Boolean = false, limit: Option[Int] = None, refresh: Boolean = false)
    derives hearth.kindlings.circederivation.KindlingsCodecAsObject,
      KindlingsSchema
object SiteTypesRequest:
  given sttp.tapir.Schema[SiteTypesRequest] = summon[KindlingsSchema[SiteTypesRequest]].schema
