package com.histmersion.api.ocr
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

/** The converter sidecar's `/convert` response. */
final case class ConvertedImage(
    name: ConvertedImage.Name,
    mime: ConvertedImage.Mime,
    base64: ConvertedImage.Base64,
    width: Option[ConvertedImage.Width] = None,
    height: Option[ConvertedImage.Height] = None,
    sha256: Option[com.histmersion.api.Sha256] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object ConvertedImage:
  type Name = com.histmersion.api.exchange.Bundle.Asset.FileName
  val Name: _root_.com.histmersion.api.exchange.Bundle.Asset.FileName.type = _root_.com.histmersion.api.exchange.Bundle.Asset.FileName
  type Mime = com.histmersion.api.exchange.Bundle.Asset.MimeText
  val Mime: _root_.com.histmersion.api.exchange.Bundle.Asset.MimeText.type = _root_.com.histmersion.api.exchange.Bundle.Asset.MimeText
  type Base64 = com.histmersion.api.media.AssetInput.Base64
  val Base64: _root_.com.histmersion.api.media.AssetInput.Base64.type = _root_.com.histmersion.api.media.AssetInput.Base64
  type Width = com.histmersion.api.media.ImageSize.Width
  val Width: _root_.com.histmersion.api.media.ImageSize.Width.type = _root_.com.histmersion.api.media.ImageSize.Width
  type Height = com.histmersion.api.media.ImageSize.Height
  val Height: _root_.com.histmersion.api.media.ImageSize.Height.type = _root_.com.histmersion.api.media.ImageSize.Height
