package com.histmersion.api.personal

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.ItemKind

final case class UnreadRow(
    kind: ItemKind,
    id: ItemKey,
    title: com.histmersion.api.personal.ReadMark.Title,
    authors: List[com.histmersion.api.library.AuthorName] = Nil,
    year: Option[com.histmersion.api.catalogue.Year] = None,
    status: Option[UnreadRow.Status] = None,
    periodStart: Option[com.histmersion.api.catalogue.Year] = None,
    periodEnd: Option[com.histmersion.api.catalogue.Year] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object UnreadRow:
  type Status = Status.T
  object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
