package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given

final case class PlaceDesignation(
    id: String,
    label: Option[String] = None,
    start: Option[String] = None,
    end: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
