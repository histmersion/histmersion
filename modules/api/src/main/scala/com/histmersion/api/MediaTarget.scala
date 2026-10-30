package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A place or event a provider's picture is linked to, in its role, from which statement of which item (`P18` of
  * `Q132564`).
  */
final case class MediaTarget(
    targetType: String,
    targetId: String,
    role: String,
    property: Option[String] = None,
    item: Option[String] = None,
    /** Why a collection's object or map is tied to this place: the record's field that names it and the dates compared
      * (`excavation = Mycenae; 1400–1200 BCE within 1600–1100 BCE`).
      */
    basis: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema

object MediaTarget:
  given Schema[MediaTarget] = summon[KindlingsSchema[MediaTarget]].schema
