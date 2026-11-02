package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class AssetLinkPut(
    citekey: String,
    assetName: String,
    relation: String,
    targetType: String,
    targetId: String,
    xywh: Option[String] = None,
    notes: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema

object AssetLinkPut:
  given Schema[AssetLinkPut] = summon[KindlingsSchema[AssetLinkPut]].schema
