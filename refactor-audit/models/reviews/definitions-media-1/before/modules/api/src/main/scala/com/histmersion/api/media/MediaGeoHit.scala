package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MediaGeoHit(
    citekey: Option[String],
    assetName: String,
    kind: Option[String],
    lat: Double,
    lon: Double,
    periodStart: Option[Long],
    periodEnd: Option[Long],
    caption: Option[String],
    uid: String = "",
    provider: Option[String] = None,
    license: Option[String] = None,
    credit: Option[String] = None,
    thumbUrl: Option[String] = None,
    fileUrl: Option[String] = None,
    sourceUrl: Option[String] = None,
    licenseUrl: Option[String] = None,
    author: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
