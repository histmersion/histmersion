package com.histmersion.api.personal

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class BookmarkNew(target: Target, label: Option[BookmarkLabel] = None, title: Option[String] = None)
    derives KindlingsCodecAsObject,
      KindlingsSchema

object BookmarkNew:
  given Schema[BookmarkNew] = summon[KindlingsSchema[BookmarkNew]].schema
