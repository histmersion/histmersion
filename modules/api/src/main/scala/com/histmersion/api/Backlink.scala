package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Something that links TO an article: another article, or a source whose notes mention it. */
final case class Backlink(kind: String, id: String, title: Option[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object Backlink:
  given Schema[Backlink] = summon[KindlingsSchema[Backlink]].schema
