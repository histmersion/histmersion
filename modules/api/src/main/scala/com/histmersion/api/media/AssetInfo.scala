package com.histmersion
package api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.ByteCount
import com.histmersion.api.Sha256

final case class AssetInfo(
    name: AssetInfo.Name,
    mime: AssetInfo.Mime,
    bytes: ByteCount,
    sha256: Sha256,
    width: Option[AssetInfo.Width] = None,
    height: Option[AssetInfo.Height] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object AssetInfo:
  export com.histmersion.api.exchange.Bundle.Asset.{FileName as Name, MimeText as Mime}
  export ImageSize.{Width, Height}
