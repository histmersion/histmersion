package com.histmersion
package api.media
import com.histmersion.util.json.JsonCodec
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
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object AssetInfo:
  export api.exchange.Bundle.Asset.{FileName as Name, MimeText as Mime}
  export ImageSize.{Height, Width}
