package com.histmersion.api.personal

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class Bookmark(
    id: BookmarkId,
    target: Target,
    title: String,
    label: Option[BookmarkLabel] = None,
    createdAt: String = "",
    updatedAt: String = ""
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def name: String = label.map(_.value).filter(_.trim.nonEmpty).getOrElse(title)

object Bookmark:
  given Schema[Bookmark] = summon[KindlingsSchema[Bookmark]].schema
