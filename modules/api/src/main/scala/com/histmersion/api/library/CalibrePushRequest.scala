package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CalibrePushRequest(citekey: String, calibreId: String) derives KindlingsCodecAsObject, KindlingsSchema
object CalibrePushRequest:
  given Schema[CalibrePushRequest] = summon[KindlingsSchema[CalibrePushRequest]].schema
