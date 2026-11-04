package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MediaItem(
    uid: String,
    name: String,
    mime: String,
    bytes: Int,
    citekey: Option[String] = None,
    provider: Option[String] = None,
    providerKey: Option[String] = None,
    width: Option[Int] = None,
    height: Option[Int] = None,
    kind: Option[String] = None,
    caption: Option[String] = None,
    license: Option[String] = None,
    licenseUrl: Option[String] = None,
    author: Option[String] = None,
    credit: Option[String] = None,
    sourceUrl: Option[String] = None,
    originalUrl: Option[String] = None,
    retrievedAt: Option[String] = None,
    thumbUrl: Option[String] = None,
    fileUrl: String = "",
    targets: List[MediaTarget] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
