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
  type Name = com.histmersion.api.exchange.Bundle.Asset.FileName
  val Name: _root_.com.histmersion.api.exchange.Bundle.Asset.FileName.type = _root_.com.histmersion.api.exchange.Bundle.Asset.FileName
  type Mime = com.histmersion.api.exchange.Bundle.Asset.MimeText
  val Mime: _root_.com.histmersion.api.exchange.Bundle.Asset.MimeText.type = _root_.com.histmersion.api.exchange.Bundle.Asset.MimeText
  type Width = com.histmersion.api.media.ImageSize.Width
  val Width: _root_.com.histmersion.api.media.ImageSize.Width.type = _root_.com.histmersion.api.media.ImageSize.Width
  type Height = com.histmersion.api.media.ImageSize.Height
  val Height: _root_.com.histmersion.api.media.ImageSize.Height.type = _root_.com.histmersion.api.media.ImageSize.Height
