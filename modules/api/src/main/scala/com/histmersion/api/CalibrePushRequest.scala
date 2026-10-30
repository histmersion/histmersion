package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Written back after a host-side `calibre push`, so the source stops counting as a gap. */
final case class CalibrePushRequest(citekey: String, calibreId: String) derives KindlingsCodecAsObject, KindlingsSchema
object CalibrePushRequest:
  given Schema[CalibrePushRequest] = summon[KindlingsSchema[CalibrePushRequest]].schema
