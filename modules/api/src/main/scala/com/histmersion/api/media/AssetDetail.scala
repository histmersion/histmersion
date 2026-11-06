package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class AssetDetail(
    name: com.histmersion.api.exchange.Bundle.Asset.FileName,
    mime: com.histmersion.api.exchange.Bundle.Asset.MimeText,
    bytes: com.histmersion.api.ByteCount,
    sha256: com.histmersion.api.Sha256,
    width: Option[com.histmersion.api.media.ImageSize.Width] = None,
    height: Option[com.histmersion.api.media.ImageSize.Height] = None,
    kind: Option[com.histmersion.api.media.MediaItem.Kind] = None,
    caption: Option[com.histmersion.api.media.MediaItem.Caption] = None,
    license: Option[com.histmersion.api.media.MediaItem.License] = None,
    lat: Option[com.histmersion.api.catalogue.Latitude] = None,
    lon: Option[com.histmersion.api.catalogue.Longitude] = None,
    periodStart: Option[com.histmersion.api.catalogue.Year] = None,
    periodEnd: Option[com.histmersion.api.catalogue.Year] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
