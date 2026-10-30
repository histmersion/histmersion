package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema

final case class SourceDeleteRequest(apply: Boolean = false) derives KindlingsSchema
object SourceDeleteRequest:
  given Schema[SourceDeleteRequest] = summon[KindlingsSchema[SourceDeleteRequest]].schema

  given Codec.AsObject[SourceDeleteRequest] = StrictJson.codec(KindlingsCodecAsObject.derived[SourceDeleteRequest])
