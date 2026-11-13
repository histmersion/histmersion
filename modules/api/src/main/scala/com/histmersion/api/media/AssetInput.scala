package com.histmersion
package api.media
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.Sha256
import io.github.iltotore.iron.constraint.any.Pure

final case class AssetInput(
    name: Bundle.Asset.FileName,
    mime: Bundle.Asset.MimeText,
    base64: AssetInput.Base64,
    width: Option[ImageSize.Width] = None,
    height: Option[ImageSize.Height] = None,
    sha256: Option[Sha256] = None
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object AssetInput:
  type Base64 = Base64.T
  object Base64 extends Newtype[String, Pure]
