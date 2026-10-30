package com.histmersion.api.personal

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A bookmark's new name (none or empty: no name of its own). */
final case class BookmarkEdit(label: Option[BookmarkLabel] = None) derives KindlingsCodecAsObject, KindlingsSchema

object BookmarkEdit:
  given Schema[BookmarkEdit] = summon[KindlingsSchema[BookmarkEdit]].schema
