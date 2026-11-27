package com.histmersion
package api.ocr
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.Sha256
import com.histmersion.lint.{tolerated, LintRule}

/** The converter sidecar's `/convert` response. */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final case class ConvertedImage(
    name: ConvertedImage.Name,
    mime: ConvertedImage.Mime,
    base64: ConvertedImage.Base64,
    width: Option[ConvertedImage.Width] = None,
    height: Option[ConvertedImage.Height] = None,
    sha256: Option[Sha256] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ConvertedImage:
  export api.exchange.Bundle.Asset.{FileName as Name, MimeText as Mime}
  export api.media.AssetInput.Base64
  export api.media.ImageSize.{Height, Width}
