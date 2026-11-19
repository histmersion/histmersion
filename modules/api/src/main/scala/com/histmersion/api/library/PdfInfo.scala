package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.ByteCount
import io.github.iltotore.iron.constraint.any.Pure

final case class PdfInfo(
    source: PdfInfo.Source,
    url: Option[PdfInfo.Url],
    bytes: ByteCount,
    spoolPath: Option[PdfInfo.SpoolPath]
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object PdfInfo:
  type Source = Source.T
  object Source extends Newtype[String, Pure]
  type Url = Url.T
  object Url extends Newtype[String, Pure]
  type SpoolPath = SpoolPath.T
  object SpoolPath extends Newtype[String, Pure]
