package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class AssetDetail(
    name: String,
    mime: String,
    bytes: Int,
    sha256: String,
    width: Option[Int] = None,
    height: Option[Int] = None,
    kind: Option[String] = None,
    caption: Option[String] = None,
    license: Option[String] = None,
    lat: Option[Double] = None,
    lon: Option[Double] = None,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
