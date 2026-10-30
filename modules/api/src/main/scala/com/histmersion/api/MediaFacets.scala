package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** What the media layer can be filtered by: pictures per provider and per licence (`none` = no licence recorded).
  */
final case class MediaFacets(providers: List[MediaCount], licences: List[MediaCount])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object MediaFacets:
  given Schema[MediaFacets] = summon[KindlingsSchema[MediaFacets]].schema
