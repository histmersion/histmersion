package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MediaGeoHit(
    citekey: Option[com.histmersion.api.library.Citekey],
    assetName: com.histmersion.api.exchange.Bundle.Asset.FileName,
    kind: Option[com.histmersion.api.media.MediaItem.Kind],
    lat: com.histmersion.api.catalogue.Latitude,
    lon: com.histmersion.api.catalogue.Longitude,
    periodStart: Option[com.histmersion.api.catalogue.Year],
    periodEnd: Option[com.histmersion.api.catalogue.Year],
    caption: Option[com.histmersion.api.media.MediaItem.Caption],
    uid: com.histmersion.api.media.MediaItem.Uid = _root_.com.histmersion.api.media.MediaItem.Uid(""),
    provider: Option[com.histmersion.api.media.MediaItem.Provider] = None,
    license: Option[com.histmersion.api.media.MediaItem.License] = None,
    credit: Option[com.histmersion.api.media.MediaItem.Credit] = None,
    thumbUrl: Option[com.histmersion.api.media.MediaItem.ThumbUrl] = None,
    fileUrl: Option[com.histmersion.api.media.MediaItem.FileUrl] = None,
    sourceUrl: Option[com.histmersion.api.media.MediaItem.SourceUrl] = None,
    licenseUrl: Option[com.histmersion.api.media.MediaItem.LicenseUrl] = None,
    author: Option[com.histmersion.api.media.MediaItem.Author] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
