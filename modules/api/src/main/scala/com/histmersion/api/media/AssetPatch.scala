package com.histmersion
package api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec
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
) derives KindlingsSchema,
      com.histmersion.api.json.Schema

object AssetPatch:

  given Codec.AsObject[AssetPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[AssetPatch])
