package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One geolocated picture on the media layer: a source's figure (`citekey` set) or a provider's picture of a place or
  * an event (`provider` set, `citekey` empty) — then with its licence, credit line and the local thumbnail URL the
  * marker's popup shows. `fileUrl` is the stored picture at its full stored size (the capped rendition of a provider's
  * picture: this server's own, [[MediaThumb.filePath]]); `sourceUrl` the provider's page of it, `licenseUrl` its
  * licence's text, `author` who made it — what a full-size view shows beside it.
  */
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
      KindlingsSchema

object MediaGeoHit:
  given Schema[MediaGeoHit] = summon[KindlingsSchema[MediaGeoHit]].schema
