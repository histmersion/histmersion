package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema

/** A heritage designation of a place (Wikidata P1435): its item, its name, and the designation's own dates (when it was
  * granted or lifted — never the monument's), as the record states them.
  */
final case class PlaceDesignation(
    id: String,
    label: Option[String] = None,
    start: Option[String] = None,
    end: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
