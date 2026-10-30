package com.histmersion.api.personal

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A bookmark: what it points at, the name the person gave it, and what the item is called now (`title`: the item's
  * current title, else the name it had when it was bookmarked, else what its target says). `createdAt`, `updatedAt`:
  * ISO-8601.
  */
final case class Bookmark(
    id: BookmarkId,
    target: Target,
    title: String,
    label: Option[BookmarkLabel] = None,
    createdAt: String = "",
    updatedAt: String = ""
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  /** What a list shows first: the person's own name, else the item's. */
  def name: String = label.map(_.value).filter(_.trim.nonEmpty).getOrElse(title)

object Bookmark:
  given Schema[Bookmark] = summon[KindlingsSchema[Bookmark]].schema
