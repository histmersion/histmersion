package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class AssetInput(
    name: com.histmersion.api.exchange.Bundle.Asset.FileName,
    mime: com.histmersion.api.exchange.Bundle.Asset.MimeText,
    base64: AssetInput.Base64,
    width: Option[com.histmersion.api.media.ImageSize.Width] = None,
    height: Option[com.histmersion.api.media.ImageSize.Height] = None,
    sha256: Option[com.histmersion.api.Sha256] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object AssetInput:
  type Base64 = Base64.T
  object Base64 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
