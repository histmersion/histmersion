package com.histmersion
package api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Year

final case class MediaGeoHit(
    citekey: Option[Citekey],
    assetName: Bundle.Asset.FileName,
    kind: Option[MediaItem.Kind],
    lat: Latitude,
    lon: Longitude,
    periodStart: Option[Year],
    periodEnd: Option[Year],
    caption: Option[MediaItem.Caption],
    uid: MediaItem.Uid = MediaItem.Uid(""),
    provider: Option[MediaItem.Provider] = None,
    license: Option[MediaItem.License] = None,
    credit: Option[MediaItem.Credit] = None,
    thumbUrl: Option[MediaItem.ThumbUrl] = None,
    fileUrl: Option[MediaItem.FileUrl] = None,
    sourceUrl: Option[MediaItem.SourceUrl] = None,
    licenseUrl: Option[MediaItem.LicenseUrl] = None,
    author: Option[MediaItem.Author] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema
