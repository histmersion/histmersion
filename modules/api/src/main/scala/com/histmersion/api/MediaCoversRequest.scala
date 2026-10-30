package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class MediaCoversRequest(
    dryRun: Boolean = true,
    limit: Option[Int] = None,
    citekey: Option[String] = None,
    refresh: Boolean = false,
    covers: Boolean = true,
    illustrations: Boolean = true,
    isbn: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema

object MediaCoversRequest:
  given Schema[MediaCoversRequest] = summon[KindlingsSchema[MediaCoversRequest]].schema
