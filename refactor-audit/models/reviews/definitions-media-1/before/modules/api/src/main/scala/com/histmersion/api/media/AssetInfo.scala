package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class AssetInfo(
    name: String,
    mime: String,
    bytes: Int,
    sha256: com.histmersion.api.Sha256,
    width: Option[Int] = None,
    height: Option[Int] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
