package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class MediaFacets(providers: List[MediaCount], licences: List[MediaCount])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object MediaFacets:
  given Schema[MediaFacets] = summon[KindlingsSchema[MediaFacets]].schema
