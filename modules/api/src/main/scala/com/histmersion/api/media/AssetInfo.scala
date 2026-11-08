package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class AssetInfo(
    name: AssetInfo.Name,
    mime: AssetInfo.Mime,
    bytes: com.histmersion.api.ByteCount,
    sha256: com.histmersion.api.Sha256,
    width: Option[AssetInfo.Width] = None,
    height: Option[AssetInfo.Height] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object AssetInfo:
  export com.histmersion.api.exchange.Bundle.Asset.{FileName as Name, MimeText as Mime}
  export com.histmersion.api.media.ImageSize.{Width, Height}
