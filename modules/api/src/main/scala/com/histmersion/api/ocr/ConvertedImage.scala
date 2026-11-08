package com.histmersion.api.ocr
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

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
  export com.histmersion.api.exchange.Bundle.Asset.{FileName as Name, MimeText as Mime}
  export com.histmersion.api.media.AssetInput.Base64
  export com.histmersion.api.media.ImageSize.{Width, Height}
