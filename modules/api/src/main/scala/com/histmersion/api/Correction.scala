package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.syntax.*
import sttp.tapir.Schema

final case class Correction(
    id: Long,
    targetKind: String,
    targetId: String,
    field: String,
    value: String,
    upstreamValue: String,
    status: String,
    upstream: Option[String] = None,
    upstreamNow: Option[String] = None,
    label: Option[String] = None,
    createdAt: String = "",
    checkedAt: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object Correction:
  given Schema[Correction] = summon[KindlingsSchema[Correction]].schema
