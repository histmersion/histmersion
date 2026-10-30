package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** An article citing one of an event's attesting sources. */
final case class EventArticleRef(slug: String, title: String, citekey: String)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object EventArticleRef:
  given Schema[EventArticleRef] = summon[KindlingsSchema[EventArticleRef]].schema
