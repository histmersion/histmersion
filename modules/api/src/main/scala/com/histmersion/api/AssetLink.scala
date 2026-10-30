package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class AssetLink(
    id: Long,
    citekey: String,
    assetName: String,
    relation: String,
    targetType: String,
    targetId: String,
    xywh: Option[String] = None,
    notes: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema

object AssetLink:
  given Schema[AssetLink] = summon[KindlingsSchema[AssetLink]].schema
