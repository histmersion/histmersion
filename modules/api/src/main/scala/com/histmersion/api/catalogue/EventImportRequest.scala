package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class EventImportRequest(
    provider: String,
    dryRun: Boolean = true,
    limit: Option[Int] = None,
    bbox: Option[String] = None,
    from: Option[Long] = None,
    to: Option[Long] = None,
    minVei: Option[Int] = None,
    minMagnitude: Option[Double] = None,
    refresh: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema
object EventImportRequest:
  given Schema[EventImportRequest] = summon[KindlingsSchema[EventImportRequest]].schema
