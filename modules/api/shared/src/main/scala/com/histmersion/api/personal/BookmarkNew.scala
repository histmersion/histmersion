package com.histmersion.api.personal

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A bookmark to make. `title`: what the item is called, as the one making it sees it (kept for an item the collection
  * has no record of). An item already bookmarked is answered with its bookmark, the label changed when one is given.
  */
final case class BookmarkNew(target: Target, label: Option[BookmarkLabel] = None, title: Option[String] = None)
    derives KindlingsCodecAsObject,
      KindlingsSchema

object BookmarkNew:
  given Schema[BookmarkNew] = summon[KindlingsSchema[BookmarkNew]].schema
