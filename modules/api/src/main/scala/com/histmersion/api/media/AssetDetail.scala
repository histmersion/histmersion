package com.histmersion
package api.media
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.ByteCount
import com.histmersion.api.Sha256
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Year
import com.histmersion.api.automation.Creation

final case class AssetDetail(
    name: Bundle.Asset.FileName,
    mime: Bundle.Asset.MimeText,
    bytes: ByteCount,
    sha256: Sha256,
    width: Option[ImageSize.Width] = None,
    height: Option[ImageSize.Height] = None,
    kind: Option[MediaItem.Kind] = None,
    caption: Option[MediaItem.Caption] = None,
    license: Option[MediaItem.License] = None,
    lat: Option[Latitude] = None,
    lon: Option[Longitude] = None,
    periodStart: Option[Year] = None,
    periodEnd: Option[Year] = None,
    creator: Option[Creation] = None,
    createdAt: Option[Creation.At] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema
