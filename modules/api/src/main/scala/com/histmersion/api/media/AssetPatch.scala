package com.histmersion
package api.media
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.StrictJson
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Year

final case class AssetPatch(
    kind: Option[MediaItem.Kind] = None,
    caption: Option[MediaItem.Caption] = None,
    license: Option[MediaItem.License] = None,
    lat: Option[Latitude] = None,
    lon: Option[Longitude] = None,
    periodStart: Option[Year] = None,
    periodEnd: Option[Year] = None
) derives StrictJson,
      KindlingsSchema,
      com.histmersion.api.json.Schema
