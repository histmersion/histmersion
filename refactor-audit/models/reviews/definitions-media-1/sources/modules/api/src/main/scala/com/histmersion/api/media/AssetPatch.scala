package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec
import com.histmersion.api.StrictJson
import com.histmersion.api.given

final case class AssetPatch(
    kind: Option[com.histmersion.api.media.MediaItem.Kind] = None,
    caption: Option[com.histmersion.api.media.MediaItem.Caption] = None,
    license: Option[com.histmersion.api.media.MediaItem.License] = None,
    lat: Option[com.histmersion.api.catalogue.Latitude] = None,
    lon: Option[com.histmersion.api.catalogue.Longitude] = None,
    periodStart: Option[com.histmersion.api.catalogue.Year] = None,
    periodEnd: Option[com.histmersion.api.catalogue.Year] = None
) derives KindlingsSchema, com.histmersion.api.json.Schema

object AssetPatch:


  given Codec.AsObject[AssetPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[AssetPatch])
